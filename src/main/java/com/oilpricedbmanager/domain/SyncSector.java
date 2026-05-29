package com.oilpricedbmanager.domain;

import java.time.LocalDateTime;

public record SyncSector(
        long sectorId,
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
        LocalDateTime lastSyncedAt,
        LocalDateTime nextSyncAt,
        String memo
) {
}
