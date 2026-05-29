package com.oilpricedbmanager.dto;

import com.oilpricedbmanager.domain.SyncTier;

import java.time.LocalDateTime;
import java.util.List;

public record SyncSectorResponse(
        long sectorId,
        String sectorCode,
        double centerLat,
        double centerLon,
        double katecX,
        double katecY,
        int radiusMeters,
        int sideLengthMeters,
        String sectorSource,
        List<SectorPointResponse> boundary,
        SyncTier syncTier,
        boolean enabled,
        int priorityScore,
        LocalDateTime lastSyncedAt,
        LocalDateTime nextSyncAt,
        String memo
) {
}
