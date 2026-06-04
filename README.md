# CheapestOilFinder Backend

CheapestOilFinder Backend는 Android 프론트엔드가 주유소 데이터를 직접 처리하지 않도록 중간에서 역할을 맡는 Spring Boot 서버입니다. 오피넷 API를 호출해 주유소와 유가 데이터를 수집하고, PostgreSQL/PostGIS에 저장한 뒤, Android 앱이 사용할 검색 API와 관리자 페이지를 제공합니다.

## 무엇을 담당하나요

- 오피넷 XML API 호출
- PostgreSQL/PostGIS 저장 및 조회
- 주변 주유소 검색
- 경로 주변 주유소 검색
- 주유소 데이터 최신성 관리
- 추천 계산에 필요한 서버 측 데이터 준비
- 관리자 섹터 지도와 섹터 상태 관리

## 프로젝트 구조

```text
src/main/java/
  com/oilpricedbmanager/
    controller/      # REST API와 관리자 API
    service/         # 비즈니스 로직
    repository/      # DB 접근
    scheduler/       # 정기 동기화 작업
    external/        # 오피넷 연동 클라이언트
    dto/             # 요청/응답 DTO
    config/          # 환경 설정

src/main/resources/
  db/migration/      # Flyway 마이그레이션
  static/            # 관리자 페이지 HTML, CSS, JS, 이미지

docs/               # API 접근 기록, 검증 문서, 아카이브
logs/               # 동기화 기록 로그
secrets/            # 로컬 전용 비밀정보 안내 파일
README.md
AGENTS.md
```

## 백엔드 실행 방법

1. Docker로 PostgreSQL/PostGIS를 실행합니다.

```powershell
docker compose up -d
```

2. Spring Boot 서버를 실행합니다.

```powershell
.\gradlew.bat bootRun
```

기본 DB 설정은 다음과 같습니다.

- 주소: `jdbc:postgresql://localhost:15432/oil_price_db`
- 사용자: `oil_user`
- 비밀번호: `oil_password`

## 주요 API

### 주유소 검색

- `GET /api/stations/nearby`
- `POST /api/stations/route`
- `GET /api/stations/{stationId}`

이 API들은 Android 프론트엔드가 요청한 위치나 경로를 기준으로 주유소를 찾고, 거리와 가격 정보를 함께 돌려줍니다.

### 관리자 API

- `GET /api/admin/sectors`
- `POST /api/admin/sectors/generate/custom-hex?sideLengthMeters=5000`
- `POST /api/admin/sync/sectors/due?limit=10`
- `POST /api/admin/sync/sectors/force?scope=HOT_ONLY|HOT_WARM|WARM_ONLY|COLD_ONLY&fuelTypes=REGULAR_GASOLINE&fuelTypes=PREMIUM_GASOLINE&fuelTypes=DIESEL`
- `POST /api/admin/sync/sectors/{sectorId}`
- `POST /api/admin/sync/sectors/{sectorId}/report`

## 오피넷 동기화 정책

이 서버는 오피넷 API를 직접 호출해 주유소 데이터를 수집합니다. 가용 호출량을 아껴 쓰기 위해 섹터 단위로 관리하며, 우선순위와 최신성에 따라 호출 순서를 정합니다.

대표 유종 코드는 다음과 같습니다.

- `B027`: 보통휘발유
- `B034`: 고급휘발유
- `D047`: 자동차경유

`K015`는 LPG 코드이지만, 현재 MVP에서는 우선순위가 낮습니다.

### 자동 동기화 주기

- `HOT`: 하루 1회 자동 갱신
- `WARM`: 3일 1회 자동 갱신
- `COLD`: 자동 갱신하지 않고, 프론트엔드 또는 관리자 요청이 있을 때만 수동 갱신
- 섹터 간 오피넷 호출은 너무 빠르게 몰리지 않도록 순차 실행 후 기본 30초 간격을 둡니다.
- 강제 재호출 버튼을 누르면 상단 상태 표시가 백엔드가 내려주는 현재 실행 작업 정보와 경과시간으로 갱신됩니다.
- 관리자 페이지는 새로고침 시와 약 1분 주기로 `/api/admin/sync/status`를 조회해 상태를 표시합니다.
- 수동 재호출이 이미 실행 중이면 같은 스코프의 요청은 waiting queue에 추가하고, 같은 버튼을 중복으로 눌러도 이미 대기열에 있으면 중복 enqueue하지 않습니다.
- 한 섹터 안에서도 유종별 호출을 순차 실행하고, 각 호출 사이에 기본 30초 간격을 둡니다.
- 1차 호출에서 실패한 유종은 실패 큐에 넣고, 모든 1차 호출이 끝난 뒤에 1회만 재시도합니다.
- 재시도까지 실패하면 더는 호출하지 않습니다.

주유소와 유가 데이터는 업서트할 때마다 각 테이블의 `updated_at`가 갱신됩니다. 검색 API는 이 갱신 시각을 함께 내려주므로, 프론트엔드에서 데이터가 얼마나 오래됐는지 확인할 수 있습니다.

오피넷 주유소 데이터의 식별자는 `uni_id`이며, `gas_station.uni_id`와 `fuel.uni_id`는 기본키로 관리됩니다. 따라서 오피넷 동기화는 메모리에서 순차 탐색하는 방식이 아니라, PostgreSQL의 기본키 인덱스와 `ON CONFLICT (uni_id)` 업서트로 처리합니다. 별도 이진 탐색 로직을 둘 필요가 없습니다.

### 섹터 상태

섹터는 다음 상태 중 하나를 가집니다.

- `HOT`: 자주 갱신해야 하는 중요 섹터
- `WARM`: 보통 수준의 갱신 대상
- `COLD`: 덜 자주 갱신하는 섹터
- `DISABLED`: 현재 동기화 대상에서 제외

관리자 페이지에서 섹터를 클릭하면 상태를 바꿀 수 있고, 상태에 따라 자동 동기화 대상이 달라집니다.

## 섹터 동기화 방식

현재 백엔드는 남한 영역을 기준으로 섹터를 나누고, 섹터별 중심 좌표를 이용해 오피넷 주변 주유소 정보를 요청합니다. 각 섹터에는 다음 값이 저장됩니다.

- 섹터 코드
- 중심 위도/경도
- KATEC 좌표
- 반경
- 섹터 도형
- 동기화 상태
- 활성화 여부
- 우선순위
- 마지막 동기화 시각
- 다음 동기화 예정 시각

이 데이터를 바탕으로 섹터 최신성을 판단하고, 오래된 섹터부터 다시 갱신합니다.

## 관리자 페이지

관리자 페이지는 다음 주소에서 확인할 수 있습니다.

```text
http://localhost:8080/admin/sectors.html
```

페이지에서는 다음 작업을 할 수 있습니다.

- 섹터 목록 조회
- 섹터 상태 변경
- 5km 커스텀 헥사곤 섹터 생성
- 전체 활성화 / 비활성화
- HOT만 재호출, HOT+WARM 재호출, WARM만 재호출, COLD만 재호출과 유종 체크박스로 선택한 항목만 재호출
- 동기화 대상 섹터 확인

관리자 지도의 기본 흐름은 다음과 같습니다.

- 대한민국 전도 이미지를 배경으로 사용합니다.
- 그 위에 5km 헥사곤 섹터를 얹습니다.
- 배경과 섹터는 같은 캔버스에서 같이 움직입니다.
- `HOT`, `WARM`, `COLD`, `DISABLED` 상태에 따라 색상이 달라집니다.

## 로그 기록

동기화 결과와 문제 분석 기록은 `logs/` 폴더에 저장합니다. 예:

- `logs/OPINET_LIMITED_SYNC_2026-05-28.md`
- `logs/OPINET_HOT45_SYNC_2026-05-28.md`

문서성 클레임 기록은 `docs/` 아래에 두되, 활성 최신본만 유지하고 이전 기록은 `docs/archive/`로 옮깁니다.

## 비밀정보

실제 오피넷 API 키와 같은 비밀정보는 Git에 올리지 않습니다. 로컬에서는 백엔드 프로젝트 루트의 다음 경로를 사용합니다.

- `secrets/opinet-api-key.txt`

오피넷 자동 동기화는 기본적으로 활성화되어 있습니다. 필요하면 환경변수 `OPINET_ENABLED=false`로 끌 수 있습니다.

예시 파일과 안내 문서는 둘 수 있지만, 실제 키 값은 넣지 않습니다.

## 현재 상태 메모

- Spring Boot 서버와 Docker DB는 분리되어 있습니다.
- Android 프론트엔드는 이 백엔드 API만 호출합니다.
- 관리자 페이지는 서버가 내려주는 정적 HTML입니다.
- 오피넷 동기화는 자동화와 수동 입력이 함께 동작합니다.

## 문서 갱신 규칙

기능이나 구조가 바뀌면 이 README와 `AGENTS.md`를 함께 갱신합니다. 특히 API 계약, 섹터 정책, 동기화 규칙, 관리자 화면 동작이 바뀌면 반드시 문서에도 반영합니다.

## 관리자 수동 재호출과 최근 로그

- 관리자 페이지의 강제 재호출은 일반 휘발유, 고급 휘발유, 경유를 체크박스로 선택한 뒤 실행합니다.
- 선택된 유종만 `fuelTypes` 반복 쿼리 파라미터로 백엔드에 전달합니다.
- `GET /api/admin/sync/logs/recent?limit=12`는 최근 배치와 개별 섹터 호출 로그를 내려줍니다.
- 관리자 페이지 우측에는 최근 API 호출 로그 패널이 표시되고, 일정 주기로 자동 갱신됩니다.
- 관리자 페이지는 본문 전체가 아니라 우측 사이드패널만 내부 스크롤되도록 구성합니다.
## 오피넷 호출 우선순위 큐
- 오피넷 재호출은 `FRONTEND` > `MANUAL` > `AUTO` 순서로 우선순위를 둡니다.
- 자동 호출, 수동 재호출, 프론트엔드 재호출은 각각 큐에 들어가며, 현재 호출이 끝난 뒤 우선순위가 높은 큐부터 이어서 처리합니다.
- 같은 `source`와 같은 요청 조건이 이미 대기 중이면 중복 enqueue를 하지 않고 기존 요청을 그대로 사용합니다.
- 큐가 바뀌어도 오피넷 요청 간 기본 30초 간격은 유지합니다.
- 관리자 페이지의 로그 패널에서는 최근 배치와 섹터별 호출 결과를 함께 확인할 수 있습니다.
- ## 좌표 변환 보정 메모

- 오피넷이 제공하는 `GIS_X_COOR`, `GIS_Y_COOR`는 KATEC 계열 좌표이므로, 단순 투영 역산만 하지 않고 Bessel datum과 WGS84 사이의 변환 보정을 함께 적용합니다.
- 현재 백엔드는 KATEC -> WGS84 변환 시 datum shift까지 반영하고, 샘플 좌표 검증에서는 roundtrip 오차가 거의 0에 수렴하도록 맞췄습니다.
