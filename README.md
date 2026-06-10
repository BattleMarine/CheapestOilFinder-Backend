# CheapestOilFinder Backend

CheapestOilFinder Backend는 Android 앱이 직접 외부 유가 API를 호출하지 않도록 중간에서 데이터를 수집, 정제, 조회하는 Spring Boot 서버입니다. 오피넷 주유소/유가 데이터, 주유소 검색, 경로 기반 검색, 목적지 검색, 관리자 동기화 화면을 담당하며 데이터는 PostgreSQL/PostGIS에 저장합니다.

## 핵심 역할

- 오피넷 API를 호출해 주유소 정보와 유가 데이터를 DB에 갱신합니다.
- Android 프론트엔드에 주변 주유소, 경로 기반 주유소, 주유소 상세 정보를 제공합니다.
- 네이버 Directions 5 API를 백엔드에서 대리 호출해 차량 경로 polyline을 반환합니다.
- 카카오 Local API를 백엔드에서 대리 호출하고, 검색 결과를 캐시합니다.
- 관리자 페이지에서 섹터 상태, 수동 동기화, 동기화 로그를 확인합니다.

## 저장소 구조

```text
src/main/java/com/oilpricedbmanager/
  controller/      REST API와 관리자 API
  service/         동기화, 검색, 경로, 비용 계산 로직
  repository/      JdbcTemplate 기반 DB 접근
  external/        오피넷, 네이버, 카카오 API 클라이언트
  domain/          도메인 enum/record
  dto/             요청/응답 DTO
  config/          설정 바인딩

src/main/resources/
  db/migration/    Flyway 마이그레이션
  static/admin/    관리자 페이지 정적 파일

docs/              프론트엔드 연동 가이드와 분석 문서
logs/              로컬 동기화 로그 백업 위치, Git 제외
secrets/           로컬 API 키 파일, Git 제외
```

## 실행 방법

1. PostgreSQL/PostGIS 컨테이너를 실행합니다.

```powershell
docker compose up -d
```

2. Spring Boot 서버를 실행합니다.

```powershell
.\gradlew.bat bootRun
```

기본 DB 설정은 다음과 같습니다.

- URL: `jdbc:postgresql://localhost:15432/oil_price_db`
- 사용자: `oil_user`
- 비밀번호: `oil_password`
- 서버 포트: `8080`

8080 포트가 이미 사용 중이면 기존 프로세스를 종료하거나 `SERVER_PORT`를 바꿔 실행합니다.

## 주요 API

### 주유소 API

- `GET /api/stations/nearby`: 현재 위치 기준 주변 주유소 조회
- `POST /api/stations/route`: 출발지와 목적지 기준 차량 경로 조회, `routeResultMode`에 따라 경로만 또는 경로 주변 주유소까지 반환
- `GET /api/stations/{stationId}`: 주유소 코드 기준 상세 정보 조회
- `GET /api/discounts`: 활성 할인 정보 조회

### 목적지 검색 API

- `GET /api/places/autocomplete?query=...&limit=10`: DB 기반 자동완성 후보 조회
- `POST /api/places/search`: 카카오 Local API 기반 목적지 검색, DB 캐시 우선 사용

자동완성은 외부 API를 직접 호출하지 않습니다. 사용자가 검색을 확정했을 때만 검색 API가 카카오 Local API를 호출하며, 같은 요청은 `place_search_cache`를 우선 사용합니다.

### 관리자 API

- `GET /api/admin/sectors`: 섹터 목록 조회
- `POST /api/admin/sectors/generate/custom-hex?sideLengthMeters=5000`: 5km 기준 커스텀 헥스 섹터 생성
- `POST /api/admin/sectors/enabled?enabled=true|false`: 전체 섹터 활성/비활성 전환
- `POST /api/admin/sectors/{sectorId}/status?syncTier=HOT|WARM|COLD|DISABLED`: 섹터 등급 변경
- `POST /api/admin/sync/sectors/due?limit=10`: 자동 갱신 대상 섹터 수동 처리
- `POST /api/admin/sync/sectors/force?scope=HOT_ONLY|HOT_WARM|WARM_ONLY|COLD_ONLY&fuelTypes=...`: 관리자 수동 재호출
- `POST /api/admin/sync/sectors/{sectorId}`: 단일 섹터 수동 동기화
- `POST /api/admin/sync/sectors/{sectorId}/report`: 단일 섹터 동기화 리포트 조회
- `GET /api/admin/sync/status`: 현재 실행/대기 중인 동기화 상태 조회
- `GET /api/admin/sync/logs/recent`: 동기화 로그 조회
- `POST /api/admin/sync/logs/clear`: 화면용 동기화 로그를 백업 후 삭제

예전의 모호한 `/api/admin/sync/stations`, `/api/admin/sync/fuels` 경로는 사용하지 않습니다. 섹터 기반 동기화는 `/api/admin/sync/sectors/*` 경로로만 다룹니다.

### 경로 API 반환 모드

`POST /api/stations/route` 요청에는 `routeResultMode`를 선택값으로 보낼 수 있습니다.

- `ROUTE_ONLY`: 네이버 Directions 경로만 계산해 `route` 객체를 반환하고, 주유소 DB 조회는 생략합니다.
- `ROUTE_WITH_STATIONS`: 기본 경로를 계산한 뒤 경로 주변 주유소를 추천해 `stations[]`와 `route`를 반환합니다.
- 값을 보내지 않으면 기존 호환을 위해 `ROUTE_WITH_STATIONS`로 처리합니다.

`ROUTE_WITH_STATIONS`는 다음 순서로 동작합니다.

1. 기본 현위치-목적지 경로를 네이버 Directions로 계산합니다.
2. PostGIS로 경로 주변 주유소를 조회하고, `estimatedTotalCostWon`이 낮은 후보 5개와 경로에서 가까운 후보 5개의 합집합을 경유 계산 후보로 고릅니다.
3. 중복을 제거한 경유 계산 후보에 대해서만 네이버 Directions `waypoints` 경유 경로를 호출합니다.
4. `경유 경로 거리 - 기본 경로 거리`를 `routeExtraDistanceMeters`로 기록한 뒤, `추가 이동비 + 예상 주유비`로 다시 계산한 `estimatedTotalCostWon`이 낮은 최종 top 5를 반환합니다.
5. 각 주유소 항목의 `detourRoute`에는 현위치-주유소-목적지 경로 정보가 들어갑니다.

목적지 확정 직후 지도에 경로선만 빠르게 그릴 때는 `ROUTE_ONLY`, 경로 주변 주유소 추천 화면을 구성할 때는 `ROUTE_WITH_STATIONS`를 사용합니다.
## 오피넷 동기화 정책

- 기본 유종은 보통휘발유, 고급휘발유, 경유입니다.
- 오피넷 호출 간격 기본값은 `OPINET_CALL_INTERVAL_MILLIS=30000`입니다.
- 한 섹터 안에서도 유종별 호출은 순차 실행합니다.
- 실패한 유종 호출은 실패 큐에 넣고, 모든 1차 호출이 끝난 뒤 한 번만 재시도합니다.
- 재시도까지 실패하면 세 번째 호출은 하지 않습니다.
- `HOT`은 매일 자동 갱신 대상입니다.
- `WARM`은 3일 주기 자동 갱신 대상입니다.
- `COLD`는 자동 갱신하지 않고 수동 요청 또는 프론트엔드 요청 때만 갱신합니다.
- 요청 우선순위는 `FRONTEND` > `MANUAL` > `AUTO`입니다.
- 같은 source와 같은 요청 조건이 이미 대기 중이면 중복 enqueue하지 않습니다.
- 큐가 바뀌어도 다음 오피넷 호출 전 간격은 유지합니다.

## 좌표 처리

오피넷이 제공하는 `GIS_X_COOR`, `GIS_Y_COOR`는 KATEC 계열 좌표입니다. 백엔드는 KATEC에서 WGS84 위도/경도로 변환한 뒤 프론트엔드에 반환합니다. 지도 마커 오차가 의심되면 좌표 변환 로직과 원본 도로명주소를 함께 검증합니다.

## 비밀 정보

실제 API 키는 Git에 올리지 않습니다. 로컬에서는 다음 파일 또는 환경변수를 사용합니다.

- 오피넷: `secrets/opinet-api-key.txt` 또는 `OPINET_API_KEY`
- 네이버 Directions: `secrets/naver_maps_client.txt`, 첫 줄 API Key ID, 둘째 줄 API Key
- 카카오 Local: `secrets/kakao_local_api.txt` 또는 `KAKAO_LOCAL_API_KEY`

외부 API 호출을 잠시 막고 서버만 확인하려면 다음 환경변수를 사용할 수 있습니다.

- `OPINET_ENABLED=false`
- `NAVER_DIRECTIONS_ENABLED=false`
- `KAKAO_LOCAL_ENABLED=false`

## Flyway 주의사항

이미 DB에 적용된 `src/main/resources/db/migration/V*.sql` 파일은 수정하지 않습니다. 적용된 마이그레이션을 수정하면 Flyway checksum mismatch로 서버가 시작되지 않습니다. 데이터나 스키마를 추가해야 하면 항상 새 버전의 마이그레이션을 만듭니다.

최근 지하철역 자동완성 시드는 기존 V5를 수정하지 않고 `V6__seed_subway_place_autocomplete.sql`에 분리했습니다.

## 테스트와 런타임 메모

테스트 코드는 `src/test/java` 아래에 있으며 `bootRun` 서버 런타임에는 포함되지 않습니다. 서버 구동이 이상하면 먼저 다음을 확인합니다.

- 8080 포트를 점유한 기존 Java 프로세스가 있는지
- Docker DB가 실행 중인지
- Flyway checksum mismatch가 있는지
- 자동 오피넷 스케줄이 실행 중인지
- 외부 API 키 파일 경로가 올바른지

전체 테스트는 다음 명령으로 실행합니다.

```powershell
.\gradlew.bat test
```

## 관리자 페이지

관리자 페이지는 다음 주소에서 확인합니다.

```text
http://localhost:8080/admin/sectors.html
```

관리자 페이지는 섹터 목록, 섹터 등급 변경, 수동 재호출, 현재 동기화 상태, 오피넷 호출 로그를 보여줍니다. 상단 상태는 백엔드의 `/api/admin/sync/status` 값을 기준으로 표시합니다.
## 유가 CSV 업로드 관리자 페이지

현재 판매가격 CSV 파일을 이용해 `fuel` 테이블을 수동 업데이트할 수 있습니다.

```text
http://localhost:8080/admin/fuel-price-upload.html
```

- 허니콤 맵 관리자 페이지(`/admin/sectors.html`)와 서로 이동할 수 있습니다.
- 업로드 API는 `POST /api/admin/import/fuel-prices`이며, `multipart/form-data`의 `file` 필드로 CSV를 받습니다.
- CSV 컬럼 중 `고유번호`, `고급휘발유`, `휘발유`, `경유`를 사용합니다.
- `고급휘발유`는 `fuel.gas_hign`, `휘발유`는 `fuel.gas_low`, `경유`는 `fuel.disl`에 반영합니다.
- CSV 가격이 `0`이거나 비어 있으면 해당 유종 가격은 `NULL`로 저장합니다.
- CSV 업로드는 `gas_station`의 주소, 전화번호, 좌표를 수정하지 않습니다.
- DB의 `gas_station`에 없는 고유번호는 FK 보호를 위해 건너뛰고, 결과 화면에 일부 목록을 표시합니다.
### 목적지 경로 스냅 fallback

`POST /api/stations/route`는 네이버 Directions 5가 선택 목적지 좌표를 차량 경로 목적지로 매칭하지 못하는 경우, 목적지 주변 후보 좌표를 가까운 순서로 재시도합니다.

- 1차 요청이 성공하면 `routeStatus`는 `OK`입니다.
- 목적지 주변 후보 좌표로 재시도해 성공하면 `routeStatus`는 `SNAPPED_TO_NEAREST_ROAD`입니다.
- 이 경우 `originalDestination`에는 사용자가 선택한 원래 목적지 좌표가 들어가고, `routeDestination`에는 실제 차량 경로 계산에 사용한 좌표가 들어갑니다.
- `accessDistanceMeters`는 원 목적지와 실제 경로 목적지 사이의 직선거리 추정값입니다.
- 모든 후보가 실패하면 `routeStatus`는 `ROUTE_UNAVAILABLE`이며, `routeMessage`에 실패 사유를 담습니다.
- 경로 주변 추천 주유소와 경유 경로 계산은 스냅이 성공한 경우 실제 경로 목적지인 `routeDestination` 기준으로 계산합니다.
