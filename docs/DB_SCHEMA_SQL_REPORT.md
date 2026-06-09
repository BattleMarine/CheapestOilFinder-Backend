# CheapestOilFinder Backend DB 구조 및 SQL 사용 보고서

## 1. 개요

CheapestOilFinder Backend는 PostgreSQL 16과 PostGIS 3.4를 사용한다. 데이터베이스는 `docker-compose.yml`의 `postgis/postgis:16-3.4` 이미지로 실행되며, 애플리케이션 스키마는 Flyway 마이그레이션(`src/main/resources/db/migration`)으로 관리한다.

현재 DB는 크게 네 영역으로 나뉜다.

- 주유소 기본 정보와 유가: `gas_station`, `fuel`
- 할인 정보: `discount`
- 오피넷 섹터 동기화와 로그: `sync_sector`, `sync_log`, `sector_sync_log`
- 목적지 검색 자동완성과 캐시: `place_autocomplete_entry`, `place_search_cache`

실제 DB 확인 결과, 애플리케이션이 직접 만든 view나 trigger는 없다. 다만 PostGIS 확장을 설치하면서 PostGIS가 제공하는 시스템 view인 `geometry_columns`, `geography_columns`가 존재한다.

## 2. 마이그레이션 구성

| 파일 | 역할 |
| --- | --- |
| `V1__init_schema.sql` | PostGIS 확장 활성화, 주유소/유가/할인/배치 로그 기본 테이블 생성 |
| `V2__create_sync_sector.sql` | 오피넷 API 호출 단위인 섹터와 섹터별 호출 로그 테이블 생성 |
| `V3__add_custom_hex_sector_shape.sql` | 섹터 polygon, 섹터 한 변 길이, 섹터 출처 컬럼 추가 |
| `V4__create_place_search_support.sql` | 목적지 자동완성 테이블과 카카오 검색 캐시 테이블 생성, `pg_trgm` 확장 활성화 |
| `V5__seed_korean_place_autocomplete.sql` | 한국 주요 시도/구/동/읍/면/리/랜드마크 자동완성 seed 삽입 |
| `V6__seed_subway_place_autocomplete.sql` | 지하철역 자동완성 seed 삽입 |

중요한 운영 규칙은 이미 적용된 `V*.sql` 파일을 수정하지 않는 것이다. 이미 적용된 Flyway 마이그레이션을 수정하면 checksum mismatch가 발생해 Spring Boot 서버가 시작되지 않는다. 스키마나 seed를 추가할 때는 항상 새 버전의 마이그레이션 파일을 만든다.

## 3. 확장, view, trigger 현황

### 3.1 PostgreSQL 확장

| 확장 | 생성 위치 | 사용 목적 |
| --- | --- | --- |
| `postgis` | `V1__init_schema.sql` | 좌표 geometry 생성, 거리 계산, 반경 검색, 공간 인덱스 사용 |
| `pg_trgm` | `V4__create_place_search_support.sql` | 자동완성 검색에서 문자열 유사도와 trigram GIN 인덱스 사용 |

### 3.2 View

애플리케이션 전용 view는 없다.

실제 DB에는 다음 PostGIS 제공 view가 있다.

| view | 출처 | 설명 |
| --- | --- | --- |
| `geometry_columns` | PostGIS | geometry 컬럼 메타데이터 조회용 시스템 view |
| `geography_columns` | PostGIS | geography 컬럼 메타데이터 조회용 시스템 view |

### 3.3 Trigger

실제 DB 기준 trigger는 없다.

`gas_station.geom`과 `sync_sector.geom`은 trigger로 갱신하지 않고, PostgreSQL generated column으로 자동 계산한다.

```sql
geom geometry(Point, 4326)
  GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lon, lat), 4326)) STORED
```

이 방식은 `lat`, `lon`이 바뀌면 DB가 저장 컬럼을 자동 재계산한다. 별도 trigger function을 유지할 필요가 없다는 장점이 있다.

## 4. 테이블 구조

### 4.1 `gas_station`

주유소 기본 정보를 저장한다.

| 컬럼 | 설명 |
| --- | --- |
| `uni_id` | 오피넷 사업자 고유번호, PK |
| `poll_div_cd` | 브랜드/상표 코드 |
| `os_nm` | 주유소 상호명 |
| `phone` | 전화번호 |
| `addr` | 도로명/지번 주소 |
| `lat`, `lon` | WGS84 위도/경도 |
| `geom` | PostGIS Point generated column |
| `updated_at` | 주유소 기본 정보 갱신 시각 |

인덱스:

- `gas_station_pkey`: `uni_id` 기본키
- `idx_gas_station_geom`: `geom` GIST 공간 인덱스

역할:

- 주변 주유소 조회의 기준 테이블이다.
- 오피넷 API에서 받은 KATEC 좌표를 백엔드가 WGS84로 변환한 뒤 `lat`, `lon`으로 저장한다.
- `geom`은 반경 검색과 거리 계산에 사용한다.

### 4.2 `fuel`

주유소별 유종 가격을 저장한다.

| 컬럼 | 설명 |
| --- | --- |
| `uni_id` | 주유소 고유번호, PK 및 `gas_station.uni_id` FK |
| `gas_hign` | 고급휘발유 가격 |
| `gas_low` | 보통휘발유 가격 |
| `disl` | 경유 가격 |
| `lpg` | LPG 가격 |
| `updated_at` | 가격 갱신 시각 |

관계:

- `fuel.uni_id`는 `gas_station.uni_id`를 참조한다.
- `ON DELETE CASCADE`가 설정되어 주유소가 삭제되면 유가도 함께 삭제된다.

역할:

- 앱의 선택 유종 가격 계산에 사용된다.
- 오피넷 유종별 API 호출 결과가 들어올 때 해당 유종 컬럼만 갱신한다.

### 4.3 `discount`

브랜드/유종별 할인 정보를 저장한다.

| 컬럼 | 설명 |
| --- | --- |
| `discount_id` | 할인 ID, PK |
| `poll_div_cd` | 적용 브랜드 코드, NULL이면 전체 브랜드 가능 |
| `discount_name` | 할인명 |
| `discount_type` | 할인 방식, 예: `FIXED_PER_LITER`, `FIXED_AMOUNT` |
| `discount_value` | 할인 금액/값 |
| `fuel_type` | 적용 유종, NULL이면 전체 유종 가능 |
| `is_active` | 활성 여부 |
| `starts_at`, `ends_at` | 적용 기간 |
| `created_at`, `updated_at` | 생성/수정 시각 |

인덱스:

- `idx_discount_lookup (poll_div_cd, fuel_type, is_active)`

역할:

- 추천 비용 계산에서 사용자가 선택한 할인 조건을 적용할 때 조회한다.

### 4.4 `sync_log`

오피넷 동기화 배치 단위 로그를 저장한다.

| 컬럼 | 설명 |
| --- | --- |
| `sync_id` | 배치 로그 ID, PK |
| `sync_type` | 동기화 종류, 예: `FORCE_HOT_ONLY`, `DUE_SECTORS` |
| `status` | `RUNNING`, `SUCCESS`, `FAILED` 등 |
| `message` | 요약 메시지 또는 실패 메시지 |
| `started_at`, `finished_at` | 시작/종료 시각 |

역할:

- 관리자 페이지 상단 상태 및 로그 패널의 배치 단위 기록이다.

### 4.5 `sync_sector`

오피넷 API를 호출할 공간 단위인 섹터를 저장한다.

| 컬럼 | 설명 |
| --- | --- |
| `sector_id` | 섹터 ID, PK |
| `sector_code` | 섹터 고유 코드, UNIQUE |
| `center_lat`, `center_lon` | 섹터 중심 WGS84 좌표 |
| `katec_x`, `katec_y` | 오피넷 API 호출에 사용할 KATEC 좌표 |
| `radius_m` | 오피넷 반경 검색 반경 |
| `geom` | 섹터 중심점 generated Point |
| `sync_tier` | `HOT`, `WARM`, `COLD`, `DISABLED` |
| `enabled` | 동기화 활성 여부 |
| `priority_score` | 동기화 우선순위 |
| `last_synced_at` | 마지막 성공 동기화 시각 |
| `next_sync_at` | 다음 자동 동기화 예정 시각 |
| `memo` | 설명 |
| `created_at`, `updated_at` | 생성/수정 시각 |
| `sector_shape` | 섹터 polygon 영역 |
| `side_length_m` | 커스텀 헥스 한 변 길이 |
| `sector_source` | 섹터 생성 출처, 예: `MANUAL`, `CUSTOM_HEX` |

인덱스:

- `idx_sync_sector_due`: 자동 동기화 대상 조회 최적화
- `idx_sync_sector_geom`: 중심점 공간 인덱스
- `idx_sync_sector_shape`: polygon 공간 인덱스
- `idx_sync_sector_source`: 생성 출처 조회
- `idx_sync_sector_tier`: 등급별 조회
- `sync_sector_sector_code_key`: 섹터 코드 UNIQUE

역할:

- 오피넷 API 호출 큐의 기본 단위다.
- `HOT`, `WARM`, `COLD` 등급에 따라 자동/수동 갱신 정책이 달라진다.

### 4.6 `sector_sync_log`

섹터별, 유종별 오피넷 호출 결과를 저장한다.

| 컬럼 | 설명 |
| --- | --- |
| `sync_log_id` | 섹터 호출 로그 ID, PK |
| `sector_id` | 호출한 섹터 ID, `sync_sector` FK |
| `fuel_type` | 내부 유종 enum 이름 |
| `opinet_prodcd` | 오피넷 유종 코드, 예: `B027`, `B034`, `D047` |
| `status` | `RUNNING`, `SUCCESS`, `FAILED` 등 |
| `station_count` | 응답에서 처리한 주유소 수 |
| `started_at`, `finished_at` | 시작/종료 시각 |
| `error_message` | 실패 원인과 응답 preview |

인덱스:

- `idx_sector_sync_log_sector (sector_id, started_at DESC)`

역할:

- 실패한 API 호출 전문/원인을 추적할 수 있게 한다.
- 관리자 페이지의 호출 로그 패널에서 배치 로그와 함께 조회된다.

### 4.7 `place_autocomplete_entry`

목적지 검색 자동완성 후보를 저장한다.

| 컬럼 | 설명 |
| --- | --- |
| `id` | 자동완성 항목 ID, PK |
| `source_type` | 출처, 예: `KOREA_SEED`, `GAS_STATION`, `KAKAO_SEARCH` |
| `source_ref` | 출처 내부 식별자 |
| `entry_type` | 항목 종류, 예: `CITY`, `DISTRICT`, `LANDMARK`, `SUBWAY_STATION`, `NAME`, `ADDRESS` |
| `display_text` | 화면 표시 텍스트 |
| `normalized_text` | 검색용 정규화 텍스트 |
| `primary_text`, `secondary_text` | 주/보조 표시 텍스트 |
| `latitude`, `longitude` | 후보 좌표, 없을 수 있음 |
| `search_weight` | 정렬 가중치 |
| `enabled` | 사용 여부 |
| `updated_at` | 갱신 시각 |

제약/인덱스:

- `uq_place_autocomplete_entry (source_type, source_ref, entry_type)` UNIQUE
- `idx_place_autocomplete_entry_normalized_text`: trigram GIN 인덱스
- `idx_place_autocomplete_entry_enabled_weight`: 활성 후보와 가중치 정렬 최적화

역할:

- 프론트엔드 검색창 자동완성 API가 외부 API 호출 없이 빠르게 후보를 보여줄 수 있게 한다.
- 초기 seed, 주유소 DB, 카카오 검색 결과를 모두 자동완성 후보로 누적할 수 있다.

### 4.8 `place_search_cache`

카카오 Local API 검색 결과 캐시를 저장한다.

| 컬럼 | 설명 |
| --- | --- |
| `cache_key` | 요청 조건을 해시한 64자 키, PK |
| `search_mode` | 검색 모드 |
| `query`, `normalized_query` | 원본/정규화 검색어 |
| `page_no`, `size_no`, `sort_order` | 카카오 검색 페이징/정렬 조건 |
| `center_latitude`, `center_longitude`, `radius_meters` | 위치 기반 검색 조건 |
| `request_json` | 백엔드 요청 JSON |
| `response_json` | 카카오 응답 또는 백엔드 정규화 응답 JSON |
| `provider` | 제공자, 예: `KAKAO` |
| `status_code` | 외부 API 응답 상태 |
| `fetched_at`, `expires_at` | 수집/만료 시각 |
| `last_hit_at`, `hit_count` | 캐시 재사용 정보 |
| `error_message` | 실패 메시지 |

인덱스:

- `idx_place_search_cache_expires_at`: 만료 캐시 관리
- `idx_place_search_cache_lookup`: 검색 조건 기반 조회

역할:

- 같은 목적지 검색 요청이 반복될 때 외부 카카오 API 호출을 줄인다.
- 비용 절감과 응답 속도 개선을 위한 테이블이다.

## 5. 내부/확장 테이블

실제 DB에는 애플리케이션 테이블 외에 다음 내부/확장 테이블이 있다.

| 테이블 | 출처 | 설명 |
| --- | --- | --- |
| `flyway_schema_history` | Flyway | 적용된 마이그레이션 버전과 checksum 기록 |
| `spatial_ref_sys` | PostGIS | 좌표계 메타데이터 |

이 두 테이블은 애플리케이션 비즈니스 데이터가 아니므로 직접 수정하지 않는다.

## 6. SQL 사용 방식

백엔드는 ORM 대신 `JdbcTemplate`과 `NamedParameterJdbcTemplate`을 사용한다. SQL은 repository 계층에 명시적으로 작성되어 있어 어떤 테이블을 어떻게 조회/갱신하는지 추적하기 쉽다.

### 6.1 주유소 주변 검색 SQL

위치 기반 주변 검색은 `StationRepository.findNearby`, `findNearbySnapshots`에서 수행한다.

핵심 SQL 기능:

- `ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)`: 요청 좌표를 WGS84 Point로 변환
- `ST_DWithin(...::geography, ...::geography, :radiusMeters)`: 반경 내 주유소 필터링
- `ST_Distance(...::geography, ...::geography)`: 미터 단위 거리 계산
- `JOIN fuel f ON gs.uni_id = f.uni_id`: 주유소 정보와 유가 결합

예시:

```sql
SELECT gs.uni_id,
       gs.os_nm,
       gs.lat,
       gs.lon,
       f.gas_low AS price_per_liter,
       ROUND(ST_Distance(
           gs.geom::geography,
           ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
       ))::int AS distance_meters
FROM gas_station gs
JOIN fuel f ON gs.uni_id = f.uni_id
WHERE f.gas_low IS NOT NULL
  AND ST_DWithin(
      gs.geom::geography,
      ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
      :radiusMeters
  )
ORDER BY distance_meters ASC
LIMIT 200;
```

경로 기반 검색에서는 점 좌표 대신 `ST_GeomFromText(:routeWkt)`로 route polyline WKT를 받아 경로 주변 주유소를 찾는다.

### 6.2 오피넷 API 결과 upsert SQL

오피넷 결과 저장은 `OpinetStationRepository`가 담당한다.

주유소 기본 정보는 `gas_station.uni_id` 기준으로 upsert한다.

```sql
INSERT INTO gas_station (uni_id, poll_div_cd, os_nm, lat, lon, updated_at)
VALUES (:uniId, :pollDivCd, :stationName, :lat, :lon, CURRENT_TIMESTAMP)
ON CONFLICT (uni_id)
DO UPDATE SET
    poll_div_cd = COALESCE(EXCLUDED.poll_div_cd, gas_station.poll_div_cd),
    os_nm = EXCLUDED.os_nm,
    lat = EXCLUDED.lat,
    lon = EXCLUDED.lon,
    updated_at = CURRENT_TIMESTAMP;
```

유가는 유종별 컬럼만 갱신한다.

```sql
INSERT INTO fuel (uni_id, gas_low, updated_at)
VALUES (:uniId, :price, CURRENT_TIMESTAMP)
ON CONFLICT (uni_id)
DO UPDATE SET gas_low = EXCLUDED.gas_low,
              updated_at = CURRENT_TIMESTAMP;
```

이 구조 덕분에 경유 호출이 실패해도 보통휘발유/고급휘발유 가격은 유지 또는 갱신될 수 있다.

### 6.3 섹터 생성과 갱신 SQL

`SyncSectorRepository`는 섹터 목록, 자동 갱신 대상, 수동 호출 대상을 조회한다.

자동 동기화 대상 조회:

```sql
SELECT ...
FROM sync_sector
WHERE enabled = true
  AND sync_tier IN ('HOT', 'WARM')
  AND next_sync_at <= CURRENT_TIMESTAMP
ORDER BY priority_score DESC,
         last_synced_at ASC NULLS FIRST,
         sector_id ASC
LIMIT :limit;
```

섹터 생성/갱신은 `sector_code`를 기준으로 upsert한다.

```sql
INSERT INTO sync_sector (..., sector_shape, sync_tier, enabled, priority_score, memo)
VALUES (..., ST_SetSRID(ST_GeomFromText(:sectorShapeWkt), 4326), ...)
ON CONFLICT (sector_code)
DO UPDATE SET
    center_lat = EXCLUDED.center_lat,
    center_lon = EXCLUDED.center_lon,
    katec_x = EXCLUDED.katec_x,
    katec_y = EXCLUDED.katec_y,
    sector_shape = EXCLUDED.sector_shape,
    updated_at = CURRENT_TIMESTAMP;
```

동기화가 성공하면 등급별 다음 갱신 시각을 계산한다.

- `HOT`: 1일 뒤, 1440분
- `WARM`: 3일 뒤, 4320분
- `COLD`, `DISABLED`: 사실상 자동 갱신 제외, 525600분

### 6.4 동기화 로그 SQL

배치 단위 로그는 `sync_log`, 섹터/유종 단위 로그는 `sector_sync_log`에 나누어 저장한다.

배치 시작:

```sql
INSERT INTO sync_log (sync_type, status)
VALUES (?, 'RUNNING')
RETURNING sync_id;
```

섹터 유종 호출 시작:

```sql
INSERT INTO sector_sync_log (sector_id, fuel_type, opinet_prodcd, status)
VALUES (:sectorId, :fuelType, :opinetProdcd, 'RUNNING')
RETURNING sync_log_id;
```

관리자 화면의 로그 조회는 실제 DB view를 만들지 않고, 애플리케이션 SQL에서 `UNION ALL`로 배치 로그와 섹터 로그를 합친다.

```sql
SELECT ...
FROM (
    SELECT 'BATCH' AS log_type, sync_id AS ref_id, ...
    FROM sync_log
    UNION ALL
    SELECT 'SECTOR' AS log_type, ssl.sync_log_id AS ref_id, ...
    FROM sector_sync_log ssl
    JOIN sync_sector ss ON ss.sector_id = ssl.sector_id
) logs
ORDER BY started_at DESC, ref_id DESC;
```

로그 비우기 기능은 삭제 전에 CSV로 백업한 뒤 다음 SQL을 실행한다.

```sql
TRUNCATE TABLE sector_sync_log, sync_log RESTART IDENTITY;
```

### 6.5 목적지 자동완성 SQL

자동완성 검색은 `place_autocomplete_entry.normalized_text`를 사용한다.

```sql
SELECT entry_type, display_text, primary_text, secondary_text, source_type, source_ref, latitude, longitude
FROM place_autocomplete_entry
WHERE enabled = true
  AND (normalized_text LIKE :prefix OR normalized_text LIKE '%' || :query || '%')
ORDER BY search_weight DESC,
         CASE WHEN normalized_text LIKE :prefix THEN 0 ELSE 1 END,
         similarity(normalized_text, :query) DESC,
         LENGTH(display_text) ASC,
         display_text ASC
LIMIT :limit;
```

여기서 `similarity`는 `pg_trgm` 확장이 제공한다. 초기 후보는 V5/V6 seed로 들어가고, 주유소 DB 또는 카카오 검색 결과에서 추가 후보를 upsert할 수 있다.

### 6.6 카카오 검색 캐시 SQL

카카오 Local API 응답은 `place_search_cache`에 저장한다.

캐시 조회:

```sql
SELECT response_json
FROM place_search_cache
WHERE cache_key = :cacheKey
  AND expires_at > :now;
```

캐시 hit 기록:

```sql
UPDATE place_search_cache
SET last_hit_at = :now,
    hit_count = hit_count + 1
WHERE cache_key = :cacheKey;
```

캐시 저장은 `cache_key` 기준 upsert다. 같은 요청 조건이면 기존 row를 갱신하고, hit 정보는 유지한다.

```sql
INSERT INTO place_search_cache (...)
VALUES (...)
ON CONFLICT (cache_key) DO UPDATE SET
    response_json = EXCLUDED.response_json,
    fetched_at = EXCLUDED.fetched_at,
    expires_at = EXCLUDED.expires_at,
    last_hit_at = place_search_cache.last_hit_at,
    hit_count = place_search_cache.hit_count;
```

## 7. 데이터 흐름 요약

### 7.1 오피넷 동기화 흐름

1. `sync_sector`에서 호출 대상 섹터를 선택한다.
2. 섹터의 `katec_x`, `katec_y`, `radius_m`로 오피넷 `aroundAll` API를 호출한다.
3. 응답 XML을 파싱한다.
4. KATEC 좌표를 WGS84 좌표로 변환한다.
5. `gas_station`과 `fuel`을 `uni_id` 기준으로 upsert한다.
6. 성공한 섹터는 `last_synced_at`, `next_sync_at`을 갱신한다.
7. 호출 결과는 `sync_log`, `sector_sync_log`에 기록한다.

### 7.2 프론트엔드 주변 주유소 조회 흐름

1. 프론트엔드가 현재 위치와 반경, 선택 유종을 백엔드에 전달한다.
2. 백엔드는 `gas_station.geom`과 요청 좌표 사이의 거리를 계산한다.
3. `fuel` 테이블에서 선택 유종 가격이 있는 주유소만 필터링한다.
4. 거리, 가격, 갱신 시각을 포함해 프론트엔드에 반환한다.

### 7.3 목적지 검색 흐름

1. 자동완성은 `place_autocomplete_entry`만 조회한다.
2. 검색 확정 시 `place_search_cache`를 먼저 확인한다.
3. 캐시가 유효하면 외부 API 없이 캐시 응답을 반환한다.
4. 캐시가 없으면 카카오 Local API를 호출하고 결과를 캐시에 저장한다.
5. 검색 결과 일부는 자동완성 후보로도 재적재한다.

## 8. 설계상 특징과 보고서용 해석

- ORM 대신 명시적 SQL을 사용해 공간 검색, upsert, 캐시 정책을 직접 제어한다.
- PostGIS `geometry`와 `geography`를 함께 활용한다. 저장은 `geometry(Point, 4326)`로 하고, 거리 계산은 미터 단위 정확도를 위해 `geography` cast를 사용한다.
- view와 trigger를 애플리케이션 계층에서 최소화했다. 대신 generated column, repository SQL, Flyway 마이그레이션으로 동작을 명확히 관리한다.
- `ON CONFLICT` upsert를 적극 사용해 외부 API 재수집 시 기존 row를 안전하게 갱신한다.
- 오피넷 호출은 섹터/유종/로그 단위로 분리되어 실패 추적과 재시도 정책을 구현하기 쉽다.
- 목적지 검색은 자동완성과 실제 외부 API 검색을 분리해 비용을 줄이는 구조다.

## 9. 실제 DB 확인 결과

2026-06-09 기준 로컬 Docker DB에서 확인한 결과는 다음과 같다.

- 애플리케이션 주요 테이블: `gas_station`, `fuel`, `discount`, `sync_log`, `sync_sector`, `sector_sync_log`, `place_autocomplete_entry`, `place_search_cache`
- Flyway 내부 테이블: `flyway_schema_history`
- PostGIS 내부 테이블/view: `spatial_ref_sys`, `geometry_columns`, `geography_columns`
- 애플리케이션 생성 view: 없음
- 애플리케이션 생성 trigger: 없음
- 주요 인덱스 수: 애플리케이션 테이블 기준 22개