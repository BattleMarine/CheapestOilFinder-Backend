package com.oilpricedbmanager.domain;

import java.time.LocalDateTime;

public record StationFuelSnapshot(
        String uniId,
        String pollDivCd,
        String stationName,
        String phone,
        String address,
        double lat,
        double lon,
        Integer gasHign,
        Integer gasLow,
        Integer disl,
        Integer lpg,
        int distanceMeters,
        LocalDateTime fuelUpdatedAt
) {
}
