package com.oilpricedbmanager.dto;

import java.util.List;

public record PlaceSearchResponse(
        PlaceSearchMode searchMode,
        PlaceSearchSortOrder sortOrder,
        String query,
        int page,
        int size,
        int totalCount,
        int pageableCount,
        boolean end,
        boolean cached,
        List<PlaceSearchItem> items
) {
}
