package com.oilpricedbmanager.dto;

public record RouteNavigationResponse(
        String routePolyline,
        int distanceMeters,
        int durationSeconds,
        Integer tollFeeWon,
        Integer fuelPriceWon,
        String routeOption,
        String currentDateTime
) {
}
