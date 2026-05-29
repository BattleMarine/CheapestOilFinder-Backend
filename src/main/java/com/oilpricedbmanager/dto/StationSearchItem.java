package com.oilpricedbmanager.dto;

import com.oilpricedbmanager.domain.FuelType;

import java.time.LocalDateTime;

public record StationSearchItem(
        String stationId,
        String stationName,
        String brandName,
        String address,
        double latitude,
        double longitude,
        String coordinateSystem,
        int distanceMeters,
        DistanceBasis distanceBasis,
        FuelPriceSummary fuelPrices,
        FuelType cheapestFuelType,
        Integer cheapestFuelPriceWon,
        Integer estimatedTravelFuelCostWon,
        Integer estimatedTotalCostWon,
        Integer routeExtraDistanceMeters,
        LocalDateTime updatedAt
) {
}
