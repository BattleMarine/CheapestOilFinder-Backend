package com.oilpricedbmanager.domain;

public record GeneratedHexSector(
        String sectorCode,
        double centerLat,
        double centerLon,
        double katecX,
        double katecY,
        int radiusMeters,
        int sideLengthMeters,
        String sectorSource,
        String sectorShapeWkt,
        SyncTier syncTier,
        boolean enabled,
        int priorityScore,
        String memo
) {
}
