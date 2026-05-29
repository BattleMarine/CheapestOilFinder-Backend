# CheapestOilFinder Backend

## Git tracking

Commit code, configuration, migrations, docs, and helper scripts that are needed to build or run the backend.
Do not commit build outputs, logs, local secrets, IDE files, or other machine-specific artifacts.

CheapestOilFinder Android 앱을 위한 Spring Boot 백엔드 서버입니다.

이 서버는 오피넷 API 호출, PostgreSQL/PostGIS 저장소 관리, 유가 데이터 동기화, 추천 계산 API 제공을 담당합니다. Android 앱은 오피넷 API를 직접 호출하지 않고, 이 백엔드 API만 호출해야 합니다.

## 주요 역할

- 주유소 기본 정보와 최신 유가를 PostgreSQL/PostGIS에 저장합니다.
- 오피넷 XML API 데이터를 주기적으로 동기화합니다.
- Android 앱이 사용할 REST API를 제공합니다.
- 주유비, 이동비, 할인액을 반영한 추천 비용을 서버에서 계산합니다.
- 오피넷 무료 API 호출 제한을 고려해 섹터 기반 동기화 정책을 관리합니다.

## 로컬 실행

```powershell
docker compose up -d
.\gradlew.bat bootRun
```

기본 DB 설정:

- URL: `jdbc:postgresql://localhost:15432/oil_price_db`
- User: `oil_user`
- Password: `oil_password`

## 주요 API

```http
GET /api/stations/nearby?latitude=37.5665&longitude=126.9780&radiusKm=5.0&fuelAmountLiters=30.0&fuelEfficiencyKmPerLiter=10.0&fuelTypes=REGULAR_GASOLINE&fuelTypes=PREMIUM_GASOLINE&fuelTypes=DIESEL&sortOrder=DISTANCE_ASC&referenceLabel=%ED%98%84%EC%9E%AC%20%EC%9C%84%EC%B9%98
```

`lat/lon` 와 `radiusMeters` 예시는 이전 호환용입니다. 현재 프론트엔드 가이드와 최신 백엔드는 `latitude`, `longitude`, `radiusKm` 기준으로 맞춰져 있습니다.

## 오피넷 연동 정책

오피넷 무료 API는 하루 호출 제한이 약 `1,500 calls/day`입니다. 사용자 요청이 들어올 때마다 오피넷을 호출하는 방식은 기본 구조로 사용하지 않습니다.

그 이유는 다음과 같습니다.

- 사용자가 늘어나면 오피넷 호출량도 같이 증가합니다.
- 인기 지역만 자주 갱신되고, 비인기 지역은 데이터가 오래될 수 있습니다.
- 목적지 기반 추천이나 경로 주변 검색에서 DB에 데이터가 없으면 안정적인 서비스를 제공하기 어렵습니다.
- 앱에 오피넷 API 키를 넣으면 키가 노출될 수 있습니다.

따라서 백엔드는 오피넷 데이터를 미리 수집해 DB에 저장하고, 앱 요청은 DB만 조회하는 구조를 원칙으로 합니다.

```text
Android 앱
  -> CheapestOilFinder Backend API
  -> PostgreSQL/PostGIS

CheapestOilFinder Backend 스케줄러
  -> 오피넷 API
  -> PostgreSQL/PostGIS upsert
```

## 허니콤 섹터 전략

대한민국 영역을 허니콤, 즉 육각형에 가까운 섹터 구조로 나누고, 각 섹터의 중심점을 기준으로 오피넷 `aroundAll.do` API를 호출합니다.

오피넷 `aroundAll.do` 제약:

- 최대 반경: `5000m`
- 좌표계: `KATEC x/y`
- 유종 코드 `prodcd` 필수
- 유종별로 별도 호출 필요

이 프로젝트에서 사용하는 유종 코드:

- `B027`: 보통휘발유
- `B034`: 고급휘발유
- `D047`: 자동차경유
- `K015`: LPG, DB에는 저장하되 MVP 추천 화면에서는 제외

`prodcd`가 필수이므로 MVP 기준 섹터 하나를 갱신하려면 보통 다음처럼 `3 calls`가 필요합니다.

```text
섹터 1개 x 보통휘발유/고급휘발유/경유 = 3 calls
```

## 섹터 등급

모든 섹터를 같은 주기로 갱신하지 않습니다. 섹터마다 `sync_tier`를 두고, 우선순위와 남은 호출 예산에 따라 갱신 대상을 선택합니다.

```text
HOT
- 서울, 인천, 경기 주요 지역
- 광역시
- 고속도로, 휴게소, IC, 주요 경로 주변
- 오피넷 가격 갱신 시각 이후 자주 갱신

WARM
- 중소도시
- 주요 국도, 간선도로, 지역 이동축 주변
- 하루 1회 또는 며칠에 1회 갱신

COLD
- 이용량이 낮은 농어촌, 산악, 저밀도 지역
- 순환 방식으로 긴 주기 갱신

DISABLED
- 바다 섹터
- 서비스 제외 섬 지역
- 주유소가 없거나 호출 가치가 낮은 영역
```

## 페인팅 섹터 전략

수도권이나 광역시는 자동으로 높은 등급을 부여하고, 그 외 지역이라도 중요도가 높은 섹터는 수동으로 칠하듯이 활성화합니다.

예시:

```text
경부고속도로 축 주변: HOT 또는 WARM
휴게소와 주요 IC 주변: HOT 또는 WARM
수요가 낮은 내륙 지역: COLD
바다와 제외 대상 섬: DISABLED
```

이후 관리자 도구를 만들면 지도에서 섹터를 선택해 등급을 바꾸는 방식으로 확장할 수 있습니다. 앱 사용 로그가 쌓이면 자동 승격/하향도 가능합니다.

```text
최근 요청량 증가
  -> WARM 섹터를 HOT으로 승격

오랜 기간 요청 없음
  -> WARM 섹터를 COLD로 하향
```

## 호출 예산 관리

오피넷 무료 API 제한을 고려해 하루 호출량을 예산처럼 관리합니다.

예시:

```text
daily_call_budget = 1500
reserved_manual_budget = 100
scheduler_budget = 1400
```

스케줄러는 갱신 시간이 된 섹터 중 우선순위가 높은 섹터부터 선택합니다.

```sql
WHERE enabled = true
  AND next_sync_at <= now()
ORDER BY priority_score DESC, last_synced_at ASC
LIMIT remaining_call_budget
```

단, 섹터 하나가 실제로 몇 번의 호출을 소비하는지 반드시 계산해야 합니다. MVP 유종 3개를 모두 갱신하면 섹터 1개당 `3 calls`입니다.

## 권장 갱신 주기

오피넷 현재 판매가격 갱신 시각은 대략 다음과 같습니다.

```text
01:00, 02:00, 09:00, 12:00, 16:00, 19:00
```

권장 정책:

```text
HOT:
  선택한 오피넷 가격 갱신 시각 이후 갱신합니다.
  수도권, 광역시, 고속도로 섹터를 우선합니다.

WARM:
  하루 1회 또는 며칠에 1회 갱신합니다.

COLD:
  3~7일 정도의 긴 주기로 순환 갱신합니다.

DISABLED:
  수동으로 다시 활성화하지 않는 한 갱신하지 않습니다.
```

## 커버리지 계획

전국 전체를 하루에 안정적으로 갱신하는 것은 무료 API 제한 안에서는 어렵습니다. 특히 보통휘발유, 고급휘발유, 경유를 모두 수집하면 유종별 호출 때문에 호출량이 3배가 됩니다.

초기 구축과 운영은 다음 순서로 진행합니다.

1. 서울, 인천, 경기 섹터를 우선 구축합니다.
2. 광역시 섹터를 추가합니다.
3. 고속도로와 주요 경로 주변 섹터를 페인팅해 HOT/WARM으로 둡니다.
4. 나머지 비수도권 섹터는 순환 갱신합니다.

서울/인천/경기 기준 예상 섹터 수는 약 `250~300개`입니다. MVP 유종 3개 기준으로는 약 `750~900 calls`가 필요하므로, 하루 무료 호출 제한 안에서 갱신 가능합니다.

## 향후 추가할 테이블

현재 DB 스키마에 섹터 관리용 테이블을 추가해야 합니다.

```text
sync_sector
- sector_id
- center_lat
- center_lon
- katec_x
- katec_y
- radius_m
- geom
- sync_tier
- enabled
- priority_score
- last_synced_at
- next_sync_at
- memo

sector_sync_log
- sync_log_id
- sector_id
- fuel_type
- opinet_prodcd
- status
- station_count
- started_at
- finished_at
- error_message
```

## 구현 방향

다음 백엔드 작업은 아래 순서를 따릅니다.

1. `sync_sector`, `sector_sync_log` 마이그레이션을 추가합니다.
2. WGS84 위도/경도를 KATEC 좌표로 변환하는 기능을 추가합니다.
3. 서비스 대상 지역의 허니콤 섹터를 생성합니다.
4. 서울/인천/경기, 광역시, 고속도로 페인팅 섹터의 초기 tier를 seed합니다.
5. 섹터와 유종별로 오피넷 `aroundAll.do` 호출을 구현합니다.
6. `UNI_ID` 기준으로 `gas_station`, `fuel`을 upsert합니다.
7. Android 추천 API는 계속 DB 조회 중심으로 유지합니다.
## 현재 구현 상태

현재 백엔드에는 다음 기능이 구현되어 있습니다.

- 기본 추천 API: `/api/stations/nearby`
- 할인 목록 API: `/api/discounts`
- 관리자 동기화 API
  - `POST /api/admin/sync/sectors/due?limit=10`
  - `POST /api/admin/sync/sectors/{sectorId}`
- `gas_station`, `fuel`, `discount`, `sync_log` 기본 테이블
- `sync_sector`, `sector_sync_log` 섹터 동기화 테이블
- 오피넷 `aroundAll.do` 호출 클라이언트
- 오피넷 XML `OIL` 노드 파서
- 섹터별/유종별 수집 로그 기록
- `UNI_ID` 기준 주유소/유가 upsert
- WGS84 위도/경도와 KATEC 좌표 간 변환 서비스
- 초기 HOT 섹터 seed
  - 서울 시청 주변
  - 인천 시청 주변
  - 수원 시청 주변

오피넷 실제 호출은 환경변수가 설정되어야 동작합니다.

```powershell
$env:OPINET_ENABLED="true"
$env:OPINET_API_KEY="발급받은_오피넷_API_KEY"
.\gradlew.bat bootRun
```

8080 포트가 이미 사용 중이면 다음처럼 임시 포트를 지정할 수 있습니다.

```powershell
.\gradlew.bat bootRun --args="--server.port=18080"
```

## 현재 주의사항

- 현재 KATEC 변환은 서버 내부 구현으로 처리합니다. 실제 오피넷 좌표와 충분히 맞는지 샘플 데이터로 추가 검증이 필요합니다.
- 전국 허니콤 섹터 자동 생성은 아직 구현 전입니다.
- 현재 seed는 테스트용 HOT 섹터 3개뿐입니다.
- 운영 전에는 수도권/광역시/고속도로 페인팅 섹터 seed를 확장해야 합니다.
## 오피넷 API 키 관리

오피넷 API 키는 코드, Git 저장소, Docker 이미지, Android 앱 릴리즈 패키지에 포함하면 안 됩니다.

로컬 개발에서는 아래 파일에 API 키를 한 줄로 적습니다.

```text
secrets/opinet-api-key.txt
```

파일 예시:

```text
발급받은_오피넷_API_KEY
```

`secrets/` 폴더는 `.gitignore`와 `.dockerignore`에 등록되어 있습니다. 따라서 이 폴더의 파일은 Git 커밋 대상이나 Docker 빌드 컨텍스트에 포함되면 안 됩니다.

키 해석 우선순위:

1. 환경변수 `OPINET_API_KEY`
2. 파일 `OPINET_API_KEY_FILE`, 기본값 `./secrets/opinet-api-key.txt`

실제 오피넷 호출을 켜려면 다음처럼 실행합니다.

```powershell
$env:OPINET_ENABLED="true"
.\gradlew.bat bootRun
```

운영 환경에서는 파일 대신 Secret Manager, Docker secret, 서버 환경변수 등 배포 환경의 secret 주입 방식을 사용하는 것을 권장합니다.
## 관리자 섹터 맵

백엔드 서버가 실행 중일 때 아래 페이지에서 허니콤 섹터 상태를 시각적으로 확인할 수 있습니다.

```text
http://localhost:8080/admin/sectors.html
```

8080 포트가 사용 중이라 다른 포트로 실행했다면 해당 포트를 사용합니다.

```text
http://localhost:18080/admin/sectors.html
```

섹터 데이터 API:

```http
GET /api/admin/sectors
```

지도는 외부 지도 타일 없이 동작하는 지명 포함 백지도 스타일 SVG입니다. `sync_sector` 테이블의 중심 좌표와 반경을 사용해 섹터를 육각형으로 표시하고, `sync_tier`에 따라 색상을 다르게 칠합니다. 허니콤 섹터는 반투명 레이어로 표시되어 아래의 지명과 지역 윤곽을 함께 볼 수 있습니다.

색상 기준:

```text
HOT      빨강
WARM     주황
COLD     파랑
DISABLED 회색
```

현재 백지도 윤곽은 운영용 정밀 지도가 아니라 관리 화면용 단순 배경입니다. 향후 전국 섹터가 늘어나면 실제 행정구역/해안선 GeoJSON 또는 백지도 SVG로 교체할 수 있습니다.
## 5km 커스텀 헥사곤 섹터 지도
관리자 섹터 맵은 H3를 사용하지 않고, 대한민국 GeoJSON 백지도 위에 한 변 5km인 커스텀 헥사곤을 투영합니다.

페이지:

```text
http://localhost:8080/admin/sectors.html
```

섹터 목록 API:

```http
GET /api/admin/sectors
```

5km 헥사곤 생성 API:

```http
POST /api/admin/sectors/generate/custom-hex?sideLengthMeters=5000
```

생성된 섹터는 `sync_sector` 테이블에 저장됩니다. 각 섹터는 다음 값을 함께 보관합니다.

```text
sector_code       섹터 코드
center_lat        중심점 WGS84 위도
center_lon        중심점 WGS84 경도
katec_x           오피넷 aroundAll.do 호출용 KATEC X
katec_y           오피넷 aroundAll.do 호출용 KATEC Y
radius_m          오피넷 호출 반경, 기본 5000m
side_length_m     헥사곤 한 변 길이, 기본 5000m
sector_shape      지도 표시용 PostGIS Polygon
sector_source     CUSTOM_5KM_HEX 또는 MANUAL
sync_tier         HOT, WARM, COLD, DISABLED
enabled           동기화 대상 여부
priority_score    호출 우선순위
```

헥사곤 생성 방식:

- 대한민국 GeoJSON 폴리곤을 읽습니다.
- WGS84 좌표를 KATEC 평면 좌표로 변환합니다.
- KATEC 좌표계에서 한 변 5km인 pointy-top 헥사곤 격자를 생성합니다.
- 중심점이 대한민국 폴리곤 내부에 있는 헥사곤만 저장합니다.
- 헥사곤 꼭짓점은 다시 WGS84로 변환하여 `sector_shape`에 저장합니다.
- 수도권은 초기 `HOT`, 주요 광역시권은 초기 `WARM`, 나머지는 `COLD`로 분류합니다.

주의:

- 현재 KATEC 변환은 서버 내부 구현입니다. 오피넷 샘플 좌표와 실제 응답으로 추가 검증이 필요합니다.
- 해안선은 중심점 기준으로 필터링하므로 일부 해안 헥사곤은 바다 영역을 포함할 수 있습니다.
- 고속도로/휴게소/주요 경로 섹터는 이후 관리자 도구에서 페인팅하듯 `HOT` 또는 `WARM`으로 승격하는 방향으로 확장합니다.
## 관리자 지도 조작
- 섹터를 클릭한 뒤 상태 버튼으로 `HOT`, `WARM`, `COLD`, `DISABLED`를 바꿀 수 있습니다.
- `DISABLED`는 화면에서 비활성 상태로 보이게 하는 표시 상태이며, 저장된 `sync_tier`는 유지되고 `enabled`만 꺼집니다.
- 지도는 하단과 우측에 표시되는 스크롤바로 이동할 수 있고, 마우스 휠과 우하단 안쪽에 고정된 `+ / -` 버튼으로 확대/축소할 수 있습니다.
- 상단 및 우측 패널의 `전체 활성화`, `전체 비활성화` 버튼은 오피넷 호출량을 줄이거나 소수 섹터만 테스트할 때 쓰는 임시 운영 도구입니다.

## 오피넷 섹터 상세 리포트
관리자 검증용으로 특정 섹터 1개에 대해 오피넷 호출 결과를 상세하게 돌려주는 테스트 엔드포인트가 있습니다.

```http
POST /api/admin/sync/sectors/{sectorId}/report
```

이 응답에는 섹터 좌표, 유종별 호출 조건, 성공 여부, 받은 데이터 수, 그리고 각 유종별 상위 5개 미리보기가 포함됩니다.
## 로그 저장 위치
제한 동기화 기록 파일은 프로젝트 루트가 아니라 `logs/` 폴더 아래에 저장합니다. 현재 기록 파일은 [logs/OPINET_LIMITED_SYNC_2026-05-28.md](C:/Users/ktsvc/Documents/CheapestOilFinder%20Backend/logs/OPINET_LIMITED_SYNC_2026-05-28.md)입니다.
## API contract update

The station search contract has been aligned with the frontend guide.

- `GET /api/stations/nearby`
- `GET /api/stations/search/nearby` for backward compatibility
- `POST /api/stations/route`
- `POST /api/stations/search/route` for backward compatibility
- `GET /api/stations/{stationId}`

The full issue report and the verification notes are documented in
[docs/BACKEND_API_ACCESS_CLAIM.md](docs/BACKEND_API_ACCESS_CLAIM.md).

## Admin map note

- `/admin/sectors.html` now uses the provided Korea national map image as a pure background layer.
- The background photo and the hex overlay share the same scroll/zoom canvas so they move together.
- The hex overlay keeps the South Korea territory ratio as its base projection, and final visual alignment is adjusted manually in the page.
- The right-side `Mode` panel acts as a paint mode selector.
- When `HOT` is active, clicking a sector immediately paints that sector `HOT`.

## Admin page encoding note

- `/admin/sectors.html` strings were normalized back to UTF-8 Korean so the title, toolbar, and side panel render correctly in the browser.
- If the page ever looks garbled again, first verify the static HTML file encoding and the backend instance serving it.

## Admin map alignment note

- The admin map now uses a shared canvas for the Korea background image and the hex overlay so zoom changes both together.
- The overlay projection keeps the vertical scale intact, applies a separate horizontal compression factor, and allows a slight rotation so the hex grid can be nudged toward the photo aspect without shifting its anchor too far.
- The page still leaves final photo-to-hex alignment to the operator.
