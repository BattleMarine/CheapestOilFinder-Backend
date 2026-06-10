# CheapestOilFinder Backend

CheapestOilFinder Backend는 Android 앱이 오피넷, 네이버 Directions, 카카오 Local API를 직접 호출하지 않도록 중간에서 데이터를 수집하고 정제해 제공하는 Spring Boot 서버입니다. 주유소와 유가 데이터는 PostgreSQL/PostGIS에 저장하며, 프론트엔드는 이 백엔드의 API만 호출합니다.

## 핵심 기능

- 오피넷 API를 섹터 단위로 호출해 주유소 정보와 유가를 DB에 갱신합니다.
- 현재 위치 기준 주변 주유소, 주유소 상세 정보, 할인 정보 API를 제공합니다.
- 네이버 Directions 5 API를 대신 호출해 자동차 경로 polyline을 반환합니다.
- 목적지 경로 주변 주유소를 후보로 뽑고, 경유 경로 비용을 계산해 추천합니다.
- 카카오 Local API를 대신 호출해 목적지 검색 결과를 제공하고 DB에 캐시합니다.
- DB 기반 자동완성 데이터를 제공해 검색 확정 전 외부 API 호출을 줄입니다.
- 관리자 페이지에서 섹터 상태, 수동 동기화, 호출 로그, CSV 유가 업로드를 다룹니다.

## 저장소 구조

```text
src/main/java/com/oilpricedbmanager/
  config/          설정 바인딩
  controller/      프론트엔드 API와 관리자 API
  domain/          도메인 record, enum
  dto/             요청/응답 DTO
  external/        오피넷, 네이버, 카카오 API 클라이언트
  repository/      JdbcTemplate 기반 DB 접근
  service/         동기화, 검색, 경로, 비용 계산 로직

src/main/resources/
  db/migration/    Flyway 마이그레이션
  static/admin/    관리자 HTML/CSS/JS

docs/              API 가이드와 DB/SQL 보고서
logs/              로컬 로그 백업 위치, Git 제외
secrets/           로컬 API 키 파일, Git 제외
```

## 실행 방법

PostgreSQL/PostGIS 컨테이너를 먼저 실행합니다.

```powershell
docker compose up -d
```

Spring Boot 서버를 실행합니다.

```powershell
.\gradlew.bat bootRun
```

기본 설정은 다음과 같습니다.

| 항목 | 기본값 |
| --- | --- |
| 서버 포트 | `8080` |
| DB URL | `jdbc:postgresql://localhost:15432/oil_price_db` |
| DB 사용자 | `oil_user` |
| DB 비밀번호 | `oil_password` |

8080 포트가 이미 사용 중이면 기존 Java 프로세스를 종료하거나 `SERVER_PORT`를 바꿔 실행합니다.

## 환경 변수와 비밀 정보

실제 API 키는 Git에 올리지 않습니다. 로컬에서는 `secrets/` 파일 또는 환경변수로 주입합니다.

| 용도 | 파일 | 환경변수 |
| --- | --- | --- |
| 오피넷 | `secrets/opinet-api-key.txt` | `OPINET_API_KEY` |
| 네이버 Directions | `secrets/naver_maps_client.txt` | `NAVER_DIRECTIONS_API_KEY_ID`, `NAVER_DIRECTIONS_API_KEY` |
| 카카오 Local | `secrets/kakao_local_api.txt` | `KAKAO_LOCAL_API_KEY` |

`secrets/naver_maps_client.txt`는 첫 줄에 Key ID, 둘째 줄에 Key를 둡니다.

외부 API 호출을 잠시 막고 서버 구조만 검증할 때는 다음 값을 사용할 수 있습니다.

```powershell
$env:OPINET_ENABLED='false'
$env:NAVER_DIRECTIONS_ENABLED='false'
$env:KAKAO_LOCAL_ENABLED='false'
```

## 프론트엔드 API

권장 API 경로는 아래와 같습니다. 예전 호환 경로인 `/api/stations/search/nearby`, `/api/stations/search/route`도 남아 있지만 신규 호출은 권장 경로를 사용합니다.

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/stations/nearby` | 현재 위치와 반경 기준 주변 주유소 조회 |
| `GET` | `/api/stations/{stationId}` | 주유소 코드 기준 상세 정보 조회 |
| `POST` | `/api/stations/route` | 자동차 경로 조회와 목적지 기반 주유소 추천 |
| `GET` | `/api/discounts` | 활성 할인 정보 조회 |
| `GET` | `/api/places/autocomplete` | DB 기반 목적지 자동완성 |
| `POST` | `/api/places/search` | 카카오 Local 기반 목적지 검색, 캐시 우선 |

프론트엔드 연동 예시와 필드 계약은 `docs/FRONTEND_BACKEND_API_GUIDE.md`에서 관리합니다.

## 경로와 추천 주유소

`POST /api/stations/route`는 `routeResultMode`로 동작 범위를 고릅니다.

| 값 | 동작 |
| --- | --- |
| `ROUTE_ONLY` | 네이버 Directions 경로만 계산하고 주유소 추천은 생략 |
| `ROUTE_WITH_STATIONS` | 기본 경로와 경로 주변 추천 주유소를 함께 반환 |

값을 보내지 않으면 호환을 위해 `ROUTE_WITH_STATIONS`로 처리합니다.

`ROUTE_WITH_STATIONS`는 다음 순서로 동작합니다.

1. 현위치와 목적지의 기본 자동차 경로를 네이버 Directions로 계산합니다.
2. PostGIS로 경로 주변 주유소를 조회합니다.
3. 경로 이탈거리와 선택 유종 가격을 바탕으로 경유 계산 후보를 좁힙니다.
4. 후보 주유소에 대해서만 네이버 Directions 경유 경로를 호출합니다.
5. 추가 이동거리, 이동비, 예상 주유비를 반영한 `estimatedTotalCostWon` 기준으로 최종 Top 5를 반환합니다.

네이버 Directions가 목적지 좌표를 도로에 매칭하지 못하면 목적지 주변 후보 좌표로 재시도합니다. 이 경우 응답의 `routeStatus`는 `SNAPPED_TO_NEAREST_ROAD`가 되고, 원래 목적지와 실제 경로 목적지는 `originalDestination`, `routeDestination`으로 구분됩니다.

## 목적지 검색과 자동완성

자동완성은 `place_autocomplete_entry` 테이블을 우선 사용하며 외부 API를 직접 호출하지 않습니다. 사용자가 검색을 확정했을 때만 카카오 Local API를 호출하고, 결과는 `place_search_cache`에 저장합니다. `POST /api/places/search`의 `AUTO` 모드는 주소 검색 10개와 키워드 장소 검색 15개를 함께 호출해 최대 25개를 반환합니다. 캐시 TTL은 `KAKAO_LOCAL_CACHE_TTL_MINUTES`로 조정하며 기본값은 1440분입니다.

검색 API 결과는 자동완성 DB에도 보강 저장되어, 앱을 사용할수록 자주 검색된 장소명이 자동완성 후보로 쌓입니다.

## 오피넷 동기화 정책

오피넷 호출은 섹터와 유종 단위로 순차 실행합니다. 기본 호출 간격은 30초이며 `OPINET_CALL_INTERVAL_MILLIS`로 조정할 수 있습니다.

| 섹터 등급 | 자동 갱신 정책 |
| --- | --- |
| `HOT` | 매일 갱신 |
| `WARM` | 3일 주기 갱신 |
| `COLD` | 자동 갱신 없음, 수동 또는 프론트엔드 요청 시 갱신 |
| `DISABLED` | 호출 대상 제외 |

기본 유종은 보통휘발유, 고급휘발유, 경유입니다. 실패한 호출은 실패 큐에 넣고 전체 1차 호출이 끝난 뒤 한 번만 재시도합니다. 재시도까지 실패하면 세 번째 호출은 하지 않습니다.

요청 우선순위는 `FRONTEND` > `MANUAL` > `AUTO`입니다. 같은 source와 같은 요청 조건이 이미 대기 중이면 중복 enqueue하지 않습니다. 큐가 바뀔 때도 다음 오피넷 호출 전 인터벌은 유지합니다.

## 관리자 페이지

| 페이지 | 설명 |
| --- | --- |
| `/admin/sectors.html` | 섹터 지도, 등급 변경, 수동 동기화, 상태와 로그 확인 |
| `/admin/fuel-price-upload.html` | 현재 판매가격 CSV 업로드와 유가 DB 반영 |

관리자 주요 API는 다음과 같습니다.

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/admin/sectors` | 섹터 목록 조회 |
| `POST` | `/api/admin/sectors/generate/custom-hex` | 커스텀 헥스 섹터 생성 |
| `POST` | `/api/admin/sectors/enabled` | 전체 섹터 활성/비활성 전환 |
| `POST` | `/api/admin/sectors/{sectorId}/status` | 단일 섹터 등급 변경 |
| `POST` | `/api/admin/sync/sectors/due` | 자동 갱신 대상 수동 처리 |
| `POST` | `/api/admin/sync/sectors/force` | HOT/WARM/COLD 범위 강제 재호출 |
| `GET` | `/api/admin/sync/status` | 현재 실행/대기 중인 동기화 상태 조회 |
| `GET` | `/api/admin/sync/logs/recent` | 관리자 호출 로그 조회 |
| `POST` | `/api/admin/sync/logs/clear` | 화면용 로그를 백업 후 삭제 |
| `POST` | `/api/admin/import/fuel-prices` | CSV 유가 업로드 |

`/api/admin/sync/logs/clear`는 실제 서버 로그 파일을 지우지 않습니다. DB의 관리자 표시용 로그를 타임스탬프가 붙은 CSV로 백업한 뒤 화면 로그만 비웁니다.

## CSV 유가 업로드

`/admin/fuel-price-upload.html`에서 현재 판매가격 CSV를 업로드할 수 있습니다.

- `multipart/form-data`의 `file` 필드로 CSV를 받습니다.
- 사용하는 컬럼은 `고유번호`, `고급휘발유`, `휘발유`, `경유`입니다.
- `고급휘발유`는 `fuel.gas_hign`, `휘발유`는 `fuel.gas_low`, `경유`는 `fuel.disl`에 반영합니다.
- 가격이 `0`이거나 비어 있으면 해당 유종 가격은 `NULL`로 저장합니다.
- CSV 업로드는 `gas_station`의 주소, 전화번호, 좌표를 수정하지 않습니다.
- DB에 없는 주유소 고유번호는 FK 보호를 위해 건너뛰고 결과에 일부 목록을 표시합니다.

## 좌표 처리

오피넷 `GIS_X_COOR`, `GIS_Y_COOR`는 KATEC 계열 좌표로 보고 WGS84 위도/경도로 변환해 저장하고 반환합니다. 프론트엔드는 WGS84 좌표만 받습니다. 위치 오차가 의심될 때는 임의 오프셋보다 변환식, 원본 도로명주소, 실제 지도 좌표를 함께 검증합니다.

## DB와 마이그레이션

DB 스키마는 Flyway 마이그레이션으로 관리합니다. 이미 적용된 `src/main/resources/db/migration/V*.sql` 파일은 수정하지 않습니다. 스키마나 seed 데이터를 추가해야 하면 새 버전의 마이그레이션 파일을 만듭니다.

DB 구조와 SQL 설명은 `docs/DB_SCHEMA_SQL_REPORT.md`에 정리되어 있습니다.

## 테스트

전체 테스트는 다음 명령으로 실행합니다.

```powershell
.\gradlew.bat test
```

테스트 코드는 `src/test/java` 아래에 있으며 서버 런타임에는 포함되지 않습니다. 서버 구동이 이상할 때는 테스트 코드보다 포트 점유, Docker DB 상태, Flyway checksum, 외부 API 키, 자동 스케줄러 실행 여부를 먼저 확인합니다.

## Git 제외 대상

다음 항목은 커밋하지 않습니다.

- `logs/`
- `secrets/*.txt`
- `secrets/*.properties`
- `secrets/*.key`
- `*.jks`, `*.keystore`
- `oilpricedbmanager.lnk`
- 빌드 산출물과 IDE 개인 설정
