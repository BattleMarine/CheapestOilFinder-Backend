# AGENTS.md

## Git rules

Keep source, configuration, migrations, docs, and helper scripts in Git.
Keep logs, build artifacts, local secrets, and IDE files out of Git.

## Project name

The backend project is now named `CheapestOilFinder Backend`.

프로젝트를 수정하기 전에 반드시 `README.md`를 먼저 읽고, 문서에 적힌 개발 방향을 따릅니다.

중요 지침:

- 이 프로젝트는 CheapestOilFinder Android 앱을 위한 Spring Boot 백엔드입니다.
- Android 앱은 오피넷 API를 직접 호출하면 안 됩니다.
- 오피넷 데이터는 이 백엔드가 수집해서 PostgreSQL/PostGIS에 저장해야 합니다.
- 앱 API는 로컬 DB를 조회하고, 추천 계산은 서버에서 수행해야 합니다.
- 오피넷 수집 로직을 구현할 때는 README의 허니콤 섹터 동기화 정책을 따릅니다.
- 섹터 등급은 `HOT`, `WARM`, `COLD`, `DISABLED` 구조를 유지합니다.
- 오피넷 무료 API 일일 호출 제한을 고려하고, 사용자 요청 시점의 오피넷 호출을 기본 데이터 경로로 삼지 않습니다.
- 변경은 작고 명확하게 유지하며, `docker compose up -d`와 `.\gradlew.bat bootRun`으로 실행 가능한 상태를 유지합니다.
- 프로젝트가 진행됨에 따라 추가된 기능을 README에 업데이트하고, AGENTS 파일 또한 갱신합니다.
현재 구현된 백엔드 기능:

- `sync_sector`, `sector_sync_log` 기반 섹터 동기화 구조가 추가되어 있습니다.
- 오피넷 `aroundAll.do` 호출과 XML 파싱, 주유소/유가 upsert 흐름이 구현되어 있습니다.
- 관리자 API로 due 섹터 또는 특정 섹터를 수동 동기화할 수 있습니다.
- 신규 기능을 추가할 때는 README의 현재 구현 상태와 주의사항도 함께 갱신합니다.
API 키 관리 지침:

- 오피넷 API 키는 `secrets/opinet-api-key.txt` 또는 환경변수로만 제공합니다.
- `secrets/` 폴더는 Git 커밋, Docker 이미지, Android 앱 릴리즈 패키지에 포함되면 안 됩니다.
- API 키를 코드, 테스트 fixture, README 예시 실제값, 로그에 직접 기록하지 않습니다.
관리자 섹터 맵:

- `/admin/sectors.html`은 `sync_sector` 상태를 시각적으로 보여주는 관리자용 정적 페이지입니다.
- `/api/admin/sectors` API는 지도 렌더링용 섹터 목록을 반환합니다.
- 섹터 시각화 관련 변경이 생기면 README의 관리자 섹터 맵 섹션도 갱신합니다.
섹터 지도 UI 지침:

- `/admin/sectors.html`은 지명 포함 대한민국 백지도 위에 반투명 허니콤 섹터를 올려 표시합니다.
- 섹터 색상이나 투명도를 바꿀 때는 백지도 지명 가독성이 유지되는지 확인합니다.
5km 커스텀 헥사곤 섹터 지침:

- H3는 사용하지 않습니다. 대한민국 GeoJSON 백지도와 자체 5km 헥사곤 생성 로직을 사용합니다.
- `/admin/sectors.html`은 `sync_sector` 테이블에 저장된 헥사곤 폴리곤과 상태를 시각화합니다.
- `/api/admin/sectors/generate/custom-hex?sideLengthMeters=5000`는 대한민국 폴리곤 내부 중심점 기준으로 헥사곤 섹터를 생성/upsert합니다.
- 각 섹터는 WGS84 중심 좌표, KATEC 중심 좌표, PostGIS Polygon, tier, enabled 상태를 함께 저장해야 합니다.
- 오피넷 `aroundAll.do` 호출은 `sync_sector.katec_x`, `sync_sector.katec_y`, `sync_sector.radius_m` 값을 기준으로 수행합니다.
- 프로젝트가 진행됨에 따라 추가된 기능을 README에 업데이트하고, AGENTS 파일 또한 갱신합니다.
관리자 지도 조작 정책:

- `/admin/sectors.html`에서는 섹터 클릭 후 상태 버튼으로 `HOT`, `WARM`, `COLD`, `DISABLED`를 변경합니다.
- `DISABLED`는 표시 상태이며, `sync_tier`는 유지하고 `enabled`만 끕니다.
- 지도는 하단과 우측 스크롤바로 이동하며, 휠 확대/축소와 우하단 안쪽에 고정된 `+ / -` 버튼은 계속 지원합니다. 확대/축소의 기준점은 지도 중앙입니다.
- 상단 및 사이드바의 `전체 활성화`, `전체 비활성화` 버튼은 소수 섹터만 테스트할 수 있도록 돕는 임시 운영 기능입니다.
- `POST /api/admin/sync/sectors/{sectorId}/report`는 관리자 검증용 상세 리포트 API이며, 섹터 좌표, 유종별 호출 조건, 성공 여부, 데이터 수, 상위 5개 미리보기를 반환합니다.
- 제한 동기화 기록 파일은 프로젝트 루트가 아니라 `logs/` 폴더 아래에 저장합니다.
## API contract note

The station search API now follows the frontend-facing contract described in
`docs/BACKEND_API_ACCESS_CLAIM.md`. When this contract changes again, update
the README and this file together so the frontend and backend stay in sync.

## Admin map note

- Keep the `/admin/sectors.html` behavior in sync with the README.
- The page uses the provided Korea background image and a persistent paint mode selector.
- When a paint mode is active, clicking a sector applies that tier immediately.

## Admin page encoding note

- Keep the admin sectors page strings in UTF-8 Korean.
- If the UI ever becomes garbled again, check the static file encoding before changing backend logic.

## Admin map alignment note

- Keep the background image and the hex overlay inside the same canvas so zoom stays synchronized.
- Recompute overlay projection from the South Korea GeoJSON bounds when adjusting the admin map layout, keep the vertical scale intact, and use a separate horizontal compression factor plus only a slight rotation when you need to tune the photo-to-hex fit.
