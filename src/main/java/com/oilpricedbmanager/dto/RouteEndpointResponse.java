package com.oilpricedbmanager.dto;

public record RouteEndpointResponse(
        double latitude,
        double longitude,
        String label
) {
}
