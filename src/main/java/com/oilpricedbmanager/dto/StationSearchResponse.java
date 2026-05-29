package com.oilpricedbmanager.dto;

import java.util.List;

public record StationSearchResponse(
        StationSearchMode searchMode,
        String coordinateSystem,
        double radiusKm,
        int resultCount,
        String referenceLabel,
        List<StationSearchItem> stations
) {
}
