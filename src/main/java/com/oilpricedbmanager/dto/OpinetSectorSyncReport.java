package com.oilpricedbmanager.dto;

import java.util.List;

public record OpinetSectorSyncReport(
        long sectorId,
        String sectorCode,
        double centerLat,
        double centerLon,
        double katecX,
        double katecY,
        int radiusMeters,
        boolean success,
        int stationCount,
        List<OpinetFuelSyncReport> fuelCalls
) {
}
