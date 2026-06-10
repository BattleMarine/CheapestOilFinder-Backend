package com.oilpricedbmanager.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StationSearchResponse(
        StationSearchMode searchMode,
        String coordinateSystem,
        double radiusKm,
        int resultCount,
        String referenceLabel,
        List<StationSearchItem> stations,
        RouteNavigationResponse route,
        String routeStatus,
        String routeMessage,
        RouteEndpointResponse originalDestination,
        RouteEndpointResponse routeDestination,
        Integer accessDistanceMeters
) {
    public StationSearchResponse(
            StationSearchMode searchMode,
            String coordinateSystem,
            double radiusKm,
            int resultCount,
            String referenceLabel,
            List<StationSearchItem> stations,
            RouteNavigationResponse route
    ) {
        this(searchMode, coordinateSystem, radiusKm, resultCount, referenceLabel, stations, route, null, null, null, null, null);
    }
}
