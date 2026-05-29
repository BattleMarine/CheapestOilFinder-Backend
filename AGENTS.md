# AGENTS.md

## 문서 규칙

- `README.md`와 `AGENTS.md`는 항상 한국어로 작성합니다.
- 기능이나 구조가 바뀌면 `README.md`와 `AGENTS.md`를 함께 갱신합니다.
- 저장 시 UTF-8 인코딩을 유지해 한글이 깨지지 않게 합니다.

## Git 규칙

- 소스 코드, 설정, 마이그레이션, 문서, 실행에 필요한 보조 스크립트는 Git에 포함합니다.
- 로그, 빌드 산출물, 로컬 비밀정보, IDE 전용 파일은 Git에 포함하지 않습니다.
- 문서성 클레임 기록은 `docs/`에 두되, 오래된 기록은 `docs/archive/`로 옮깁니다.
- 제한 동기화 기록은 루트가 아니라 `logs/` 폴더 아래에 저장합니다.

## 프로젝트 역할

- 이 프로젝트는 CheapestOilFinder Android 앱을 위한 Spring Boot 백엔드입니다.
- Android 앱은 오피넷 API를 직접 호출하지 않습니다.
- 오피넷 데이터 수집과 정규화는 이 백엔드가 담당합니다.
- 주유소 검색, 경로 주변 검색, 추천 계산용 데이터 준비는 서버가 담당합니다.
- 앱은 백엔드 API만 호출하고, 백엔드는 PostgreSQL/PostGIS를 조회하거나 갱신합니다.

## 실행 전제

- 로컬 DB는 Docker로 실행합니다.
- Spring 서버는 `.\gradlew.bat bootRun`으로 실행 가능한 상태를 유지합니다.
- 개발 흐름이 바뀌면 README에도 동일한 실행 방법을 반영합니다.

## 오피넷 및 비밀정보

- 오피넷 API 키는 `secrets/opinet-api-key.txt` 또는 환경변수로만 제공합니다.
- `secrets/` 폴더는 Git, Docker 이미지, Android 릴리즈 패키지에 포함되면 안 됩니다.
- API 키를 코드, 테스트 fixture, README 예시 실제값, 로그에 직접 적지 않습니다.

## 섹터 동기화 정책

- `sync_sector`, `sector_sync_log` 기반 섹터 동기화 구조를 유지합니다.
- 섹터 등급은 `HOT`, `WARM`, `COLD`, `DISABLED` 구조를 유지합니다.
- 오피넷 무료 API 호출 제한을 항상 고려합니다.
- 사용자 요청 시점의 오피넷 호출을 기본 데이터 경로로 삼지 않습니다.
- `sync_sector.katec_x`, `sync_sector.katec_y`, `sync_sector.radius_m` 값을 기준으로 `aroundAll.do`를 호출합니다.

## 관리자 섹터 맵

- `/admin/sectors.html`은 `sync_sector` 상태를 시각적으로 보여주는 정적 관리자 페이지입니다.
- 지도는 지명 포함 대한민국 백지도 위에 반투명 허니콤 섹터를 올리는 방식입니다.
- 백지도와 허니콤은 같은 캔버스에서 함께 움직여야 합니다.
- 휠 확대/축소와 우하단 `+ / -` 버튼은 유지하되, 확대/축소 기준점은 지도 중앙으로 둡니다.
- 상태 버튼으로 `HOT`, `WARM`, `COLD`, `DISABLED`를 변경할 수 있어야 합니다.
- `DISABLED`는 표시 상태이며, `sync_tier`는 유지하고 `enabled`만 끕니다.
- `전체 활성화`, `전체 비활성화`는 소수 섹터 테스트를 위한 임시 운영 기능입니다.
- 섹터 시각화 관련 변경이 생기면 README의 관리자 섹터 맵 섹션도 함께 갱신합니다.

## 5km 커스텀 헥사곤

- H3는 사용하지 않습니다.
- 대한민국 GeoJSON과 자체 5km 헥사곤 생성 로직을 사용합니다.
- `/api/admin/sectors/generate/custom-hex?sideLengthMeters=5000`는 대한민국 폴리곤 내부 중심점 기준으로 헥사곤 섹터를 생성하거나 갱신합니다.
- 각 섹터는 WGS84 중심 좌표, KATEC 중심 좌표, PostGIS Polygon, tier, enabled 상태를 함께 저장합니다.
- 오피넷 호출은 섹터 중심 좌표와 반경을 기준으로 수행합니다.

## 백엔드 계약

- 주유소 검색 API는 프론트엔드 가이드와 맞는 상태를 유지합니다.
- 계약이 바뀌면 README와 이 파일을 함께 갱신합니다.
- 프론트엔드가 이해해야 하는 요청/응답 예시는 `docs/FRONTEND_BACKEND_API_GUIDE.md`에 유지합니다.
