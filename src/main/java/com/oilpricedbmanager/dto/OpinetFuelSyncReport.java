package com.oilpricedbmanager.dto;

import com.oilpricedbmanager.domain.FuelType;

import java.util.List;

public record OpinetFuelSyncReport(
        FuelType fuelType,
        String opinetProdcd,
        double katecX,
        double katecY,
        int radiusMeters,
        int sort,
        boolean success,
        int stationCount,
        List<OpinetStationPreviewResponse> preview,
        String errorMessage,
        String rawResponsePreview,
        Integer rawResponseLength
) {
}
