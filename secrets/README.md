# 비밀 정보 파일 안내

이 디렉터리는 로컬 개발과 서버 실행에 필요한 비밀 정보를 보관합니다.
실제 값은 절대 Git에 커밋하지 않습니다.

## 현재 사용 중인 파일

### `opinet-api-key.txt`
- 1행: 오피넷 API 키

### `naver_maps_client.txt`
- 1행: 네이버 Directions API Key ID
- 2행: 네이버 Directions API Key

### `kakao_local_api.txt`
- 1행: 카카오 Local REST API Key

## 주의

- 파일명과 형식만 문서로 관리합니다.
- 실제 키 값은 여기에 적지 않습니다.
- 파일은 로컬 환경에서만 사용하고 Git에 포함하지 않습니다.
