package com.oilpricedbmanager.dto;

public record PlaceSearchRequest(
        String query,
        PlaceSearchMode searchMode,
        PlaceSearchSortOrder sortOrder,
        Integer page,
        Integer size,
        Double centerLatitude,
        Double centerLongitude,
        Integer radiusMeters
) {
    public PlaceSearchMode resolvedSearchMode() {
        return searchMode == null ? PlaceSearchMode.AUTO : searchMode;
    }

    public PlaceSearchSortOrder resolvedSortOrder() {
        return sortOrder == null ? PlaceSearchSortOrder.ACCURACY : sortOrder;
    }

    public int resolvedPage() {
        if (page == null || page < 1) {
            return 1;
        }
        return Math.min(page, 45);
    }

    public int resolvedSize() {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, 15);
    }

    public Integer resolvedRadiusMeters() {
        if (radiusMeters == null || radiusMeters < 1) {
            return null;
        }
        return Math.min(radiusMeters, 20000);
    }

    public String resolvedQuery() {
        return query == null ? "" : query.trim();
    }
}
