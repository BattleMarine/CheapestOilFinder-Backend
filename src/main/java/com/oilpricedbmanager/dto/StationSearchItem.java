package com.oilpricedbmanager.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oilpricedbmanager.domain.FuelType;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StationSearchItem(
        String stationId,
        String stationName,
        String brandName,
        String address,
        String phone,
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
        RouteNavigationResponse detourRoute,
        List<String> notes,
        LocalDateTime updatedAt
) {
}