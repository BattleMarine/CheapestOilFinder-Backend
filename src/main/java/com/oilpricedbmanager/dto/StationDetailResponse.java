package com.oilpricedbmanager.dto;

public record StationDetailResponse(
        String coordinateSystem,
        StationSearchItem station
) {
}
