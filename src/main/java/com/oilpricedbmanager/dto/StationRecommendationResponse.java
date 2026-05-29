package com.oilpricedbmanager.dto;

import com.oilpricedbmanager.domain.FuelType;

import java.time.LocalDateTime;

public record StationRecommendationResponse(
        int rank,
        String stationId,
        String stationName,
        String brandCode,
        String phone,
        String address,
        double lat,
        double lon,
        FuelType fuelType,
        int pricePerLiter,
        int distanceMeters,
        int refuelCostWon,
        int travelCostWon,
        int discountAmountWon,
        int estimatedTotalCostWon,
        LocalDateTime fuelUpdatedAt
) {
}
