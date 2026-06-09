# AGENTS.md

이 문서는 CheapestOilFinder Backend 저장소에서 Codex가 따라야 할 작업 규칙과 현재 합의된 백엔드 구조를 기록합니다. 작업 전에 반드시 읽고, 기능이나 운영 규칙이 바뀌면 `README.md`와 함께 갱신합니다.

## 문서 규칙

- `README.md`와 `AGENTS.md`는 한국어로 작성합니다.
- 문서와 SQL seed 파일은 UTF-8 인코딩을 유지합니다.
- 깨진 인코딩이 보이면 부분 수정으로 덮지 말고 원인을 확인한 뒤 정상 한글로 복구합니다.
- 프론트엔드가 참고해야 하는 API 계약은 `docs/FRONTEND_BACKEND_API_GUIDE.md`에 반영합니다.

## 담당 범위

- 이 저장소는 CheapestOilFinder Android 앱을 위한 Spring Boot 백엔드입니다.
- Android 앱은 오피넷, 네이버 Directions, 카카오 Local API를 직접 호출하지 않습니다.
- 외부 API 호출, DB 저장, 좌표 변환, 검색 캐시, 관리자 동기화는 백엔드가 담당합니다.
- 데이터 저장소는 PostgreSQL/PostGIS를 기준으로 합니다.

## 실행과 검증

- 로컬 DB는 `docker compose up -d`로 실행합니다.
- 서버는 Windows PowerShell 기준 `./gradlew`가 아니라 `.\gradlew.bat bootRun` 형식으로 실행합니다.

```powershell
.\gradlew.bat bootRun
```

- 변경 후 가능한 경우 다음 명령으로 검증합니다.

```powershell
.\gradlew.bat test
```

- 문서만 변경했다면 테스트를 생략할 수 있지만, 최종 응답에 생략 사실을 적습니다.

## Git 규칙

- 사용자가 만든 변경을 되돌리지 않습니다.
- 로그, 빌드 산출물, 로컬 비밀정보, IDE 파일은 Git에 포함하지 않습니다.
- `logs/`, `secrets/*.txt`, `secrets/*.properties`, `secrets/*.key`, `*.jks`, `*.keystore`는 커밋하지 않습니다.
- `oilpricedbmanager.lnk` 같은 로컬 바로가기나 개인 작업환경 파일은 커밋하지 않습니다.
- 커밋 전에는 `git status --short`로 변경 범위를 확인하고, 이번 변경이 문서, 기능, 버그 수정 중 어디에 속하는지 먼저 분류합니다.
- 커밋에는 현재 작업과 직접 관련된 파일만 포함합니다.
- 이미 적용된 Flyway 마이그레이션을 수정한 변경은 커밋하지 않습니다. 스키마나 seed 수정은 새 마이그레이션 파일로 커밋합니다.

## 커밋 메시지 규칙

- 커밋 메시지는 기본적으로 다음 형식을 따릅니다.

```text
docs: 문서 위주 변경 요약

- 세세한 변화내용1
- 세세한 변화내용2
```

```text
feat: 기능 구현 위주 변경 요약

- 세세한 변화내용1
- 세세한 변화내용2
```

```text
fix: 버그 수정 위주 변경 요약

- 세세한 변화내용1
- 세세한 변화내용2
```

- 문서 위주의 변경은 `docs:`, 기능 구현 위주의 변경은 `feat:`, 버그 수정 위주의 변경은 `fix:`를 사용합니다.
- 커밋 제목은 한국어로 간결하게 작성합니다.
- 커밋 본문에는 핵심 변화 1줄과 세부 변경 bullet 2개 이상을 적습니다.
- 백엔드 작업 커밋은 반드시 `CheapestOilFinder Backend` 저장소에서 실행합니다. Android 프론트엔드 저장소에서 백엔드 변경을 커밋하지 않습니다.
- 커밋 전 검증이 가능하면 `.\gradlew.bat test`를 실행하고, 실행하지 못했으면 최종 응답에 이유를 남깁니다.

## Flyway 규칙

- 이미 적용된 `V*.sql` 마이그레이션 파일은 수정하지 않습니다.
- 적용된 마이그레이션을 수정하면 Flyway checksum mismatch로 서버가 시작되지 않습니다.
- 스키마나 seed 데이터를 추가해야 하면 새 버전 파일을 만듭니다.
- 예: 기존 `V5__seed_korean_place_autocomplete.sql`에 지하철역을 덧붙이지 않고 `V6__seed_subway_place_autocomplete.sql`을 새로 만듭니다.

## 오피넷 동기화 규칙

- 섹터 등급은 `HOT`, `WARM`, `COLD`, `DISABLED`를 사용합니다.
- `HOT`은 매일, `WARM`은 3일 주기, `COLD`는 수동 또는 프론트엔드 요청 시에만 갱신합니다.
- 기본 유종은 보통휘발유, 고급휘발유, 경유입니다.
- 오피넷 호출은 섹터별, 유종별로 순차 실행합니다.
- 호출 간격 기본값은 30초이며 `OPINET_CALL_INTERVAL_MILLIS`로 조정합니다.
- 실패한 호출은 실패 큐에 넣고 전체 1차 호출 후 한 번만 재시도합니다.
- 재시도 실패 시 세 번째 호출은 하지 않습니다.
- 요청 우선순위는 `FRONTEND` > `MANUAL` > `AUTO`입니다.
- 같은 source와 같은 요청 조건이 이미 대기 중이면 중복 enqueue하지 않습니다.
- 관리자 수동 호출도 같은 큐와 호출 간격 정책을 따라야 합니다.
- 모호한 예전 관리자 동기화 경로(`/api/admin/sync/stations`, `/api/admin/sync/fuels`)는 사용하지 않습니다.

## 관리자 페이지 규칙

- 관리자 페이지는 `/admin/sectors.html`입니다.
- 페이지 전체가 불필요하게 스크롤되지 않도록 우측 패널만 내부 스크롤을 사용합니다.
- 상단 현재 상태는 프론트 타이머가 아니라 백엔드 `/api/admin/sync/status` 응답을 기준으로 표시합니다.
- 호출 로그 패널은 백엔드 로그 API를 사용하되, 화면에서 지운 로그는 백업 후 DB 로그만 비웁니다.
- 실제 서버 로그 파일을 임의로 삭제하지 않습니다.

## 좌표 변환 규칙

- 오피넷 `GIS_X_COOR`, `GIS_Y_COOR`는 KATEC 계열 좌표로 보고 WGS84로 변환합니다.
- 프론트엔드에는 WGS84 위도/경도만 반환합니다.
- 위치 오차를 수정할 때는 임의 오프셋보다 변환식, datum 보정, 원본 도로명주소 검증을 우선합니다.

## 목적지 검색 규칙

- 자동완성은 DB의 `place_autocomplete_entry`를 우선 사용하며 외부 API를 직접 호출하지 않습니다.
- 검색 확정 시에만 카카오 Local API를 백엔드가 호출합니다.
- 검색 결과는 `place_search_cache`에 저장하고 TTL 안에서는 캐시를 우선 반환합니다.
- 자동완성 seed는 `gas_station` 동기화와 별도로 유지합니다.

## 비밀 정보 규칙

- API 키는 코드, 테스트 fixture, README 예시에 직접 쓰지 않습니다.
- 오피넷 키는 `secrets/opinet-api-key.txt` 또는 환경변수로 주입합니다.
- 네이버 Directions 키는 `secrets/naver_maps_client.txt`에 두 줄로 둡니다. 첫 줄은 Key ID, 둘째 줄은 Key입니다.
- 카카오 Local 키는 `secrets/kakao_local_api.txt`에 둡니다.
- 외부 API를 막고 서버 구조만 검증할 때는 `OPINET_ENABLED=false`, `NAVER_DIRECTIONS_ENABLED=false`, `KAKAO_LOCAL_ENABLED=false`를 사용합니다.

## 테스트 코드 판단 기준

- `src/test/java`의 테스트 코드는 서버 런타임에 포함되지 않습니다.
- 테스트가 느리거나 불안정하면 삭제보다 mock, timeout, 외부 의존성 차단을 먼저 검토합니다.
- 서버 구동 실패 원인을 테스트 코드로 단정하지 말고 포트 점유, DB 상태, Flyway, 스케줄러, 외부 API 설정을 먼저 확인합니다.

