package com.oilpricedbmanager.domain;

public record OpinetStationPrice(
        String uniId,
        String pollDivCd,
        String stationName,
        int price,
        Integer distanceMeters,
        double katecX,
        double katecY
) {
}