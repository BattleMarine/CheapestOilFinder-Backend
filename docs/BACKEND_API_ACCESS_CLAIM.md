# BACKEND API ACCESS CLAIM

이 문서는 프론트엔드가 지적한 API 접근 문제를 백엔드에서 어떻게 해결했는지 기록합니다.

## 원인

- `GET /api/stations/nearby` 는 살아 있었지만, 프론트가 보내는 최신 파라미터 이름과 백엔드가 기대하는 이름이 달라 `400 Bad Request` 가 발생했습니다.
- `POST /api/stations/route` 와 `GET /api/stations/{stationId}` 는 백엔드에 없어서 `404 Not Found` 가 발생했습니다.
- 주변 검색과 경로 검색 모두 응답 계약이 분리되어 있지 않아 프론트에서 예측 가능한 형태로 사용하기 어려웠습니다.

## 수정 내용

### 추가/수정된 엔드포인트

- `GET /api/stations/nearby`
- `GET /api/stations/search/nearby`  (호환용 별칭)
- `POST /api/stations/route`
- `POST /api/stations/search/route`  (호환용 별칭)
- `GET /api/stations/{stationId}`

### 지원하는 주변 검색 파라미터

- `latitude`, `longitude`
- `radiusKm` 또는 `radiusMeters`
- `fuelAmountLiters`
- `fuelEfficiencyKmPerLiter`
- `fuelTypes` 반복 파라미터
- `sortOrder`
- `referenceLabel`

### 지원하는 레거시 파라미터

- `lat`, `lon`
- `radiusMeters`
- `fuelType`
- `refuelLiters`

### 응답 계약

주변 검색과 경로 검색 응답은 다음 구조를 사용합니다.

- `searchMode`
- `coordinateSystem`
- `radiusKm`
- `resultCount`
- `referenceLabel`
- `stations[]`

각 `stations[]` 항목에는 다음 정보가 포함됩니다.

- `stationId`
- `stationName`
- `brandName`
- `address`
- `latitude`, `longitude`
- `coordinateSystem`
- `distanceMeters`
- `distanceBasis`
- `fuelPrices`
- `cheapestFuelType`
- `cheapestFuelPriceWon`
- `estimatedTravelFuelCostWon`
- `estimatedTotalCostWon`
- `routeExtraDistanceMeters`
- `updatedAt`

### 경로 polyline 처리

- `routePolyline` 는 `lat,lon;lat,lon;...` 형식 또는 `lon lat` 입력도 허용하도록 파싱합니다.
- polyline 이 없거나 파싱에 실패하면 출발지와 목적지를 잇는 직선으로 대체합니다.

## 테스트 결과

- `StationControllerTest`
  - 새 주변 검색 파라미터를 사용한 `GET /api/stations/nearby` 성공 확인
  - 레거시 파라미터를 사용한 `GET /api/stations/search/nearby` 성공 확인
  - `POST /api/stations/route` 성공 확인
  - `GET /api/stations/{stationId}` 성공 확인
- `StationSearchServiceTest`
  - route polyline 이 WKT `LINESTRING` 으로 변환되는 것 확인
  - station detail 이 전체 유종 가격을 포함해 반환되는 것 확인

## 프론트엔드가 알아두면 좋은 점

- 이제 프론트는 새 파라미터 이름으로 호출해도 되고, 당분간은 레거시 파라미터도 그대로 동작합니다.
- 기본 유종은 보통휘발유, 고급휘발유, 경유입니다.
- 좌표계는 `WGS84` 로 반환됩니다.
- 검색 결과는 기본적으로 거리 기준으로 정렬되며, `sortOrder` 로 최저가 순 또는 예상 총비용 순 정렬을 요청할 수 있습니다.
- `GET /api/stations/{stationId}` 는 상세 화면용 단건 조회로 사용하면 됩니다.

## Codex 메모

- 이번 변경은 단순 문서 보강이 아니라, 실제 백엔드 API 계약을 프론트 기준으로 맞춘 수정입니다.
- 기존 호출 코드가 남아 있어도 당분간은 호환용 별칭으로 버틸 수 있게 해두었습니다.
