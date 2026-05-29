package com.oilpricedbmanager.domain;

import java.time.LocalDateTime;

public record StationFuelCandidate(
        String uniId,
        String pollDivCd,
        String stationName,
        String phone,
        String address,
        double lat,
        double lon,
        FuelType fuelType,
        int pricePerLiter,
        int distanceMeters,
        LocalDateTime fuelUpdatedAt
) {
}
