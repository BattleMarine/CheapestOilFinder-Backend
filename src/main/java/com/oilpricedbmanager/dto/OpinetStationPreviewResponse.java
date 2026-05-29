package com.oilpricedbmanager.dto;

public record OpinetStationPreviewResponse(
        String uniId,
        String stationName,
        String pollDivCd,
        int price,
        double katecX,
        double katecY
) {
}
