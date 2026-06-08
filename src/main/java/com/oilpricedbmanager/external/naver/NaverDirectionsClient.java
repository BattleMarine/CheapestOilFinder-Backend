package com.oilpricedbmanager.external.naver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oilpricedbmanager.dto.RouteNavigationResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class NaverDirectionsClient {
    private static final String DEFAULT_ROUTE_OPTION = "traoptimal";
    private static final String DEFAULT_FUEL_TYPE = "gasoline";
    private static final int DEFAULT_CAR_TYPE = 1;

    private final NaverDirectionsProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public NaverDirectionsClient(
            NaverDirectionsProperties properties,
            RestClient.Builder builder,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.objectMapper = objectMapper;
    }

    public RouteNavigationResponse fetchDrivingRoute(
            double startLongitude,
            double startLatitude,
            double goalLongitude,
            double goalLatitude,
            double mileageKmPerLiter
    ) {
        ensureEnabled();

        String response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/driving")
                        .queryParam("start", formatCoordinate(startLongitude, startLatitude))
                        .queryParam("goal", formatCoordinate(goalLongitude, goalLatitude))
                        .queryParam("option", DEFAULT_ROUTE_OPTION)
                        .queryParam("cartype", DEFAULT_CAR_TYPE)
                        .queryParam("fueltype", DEFAULT_FUEL_TYPE)
                        .queryParam("mileage", formatMileage(mileageKmPerLiter))
                        .queryParam("lang", "ko")
                        .build())
                .header("x-ncp-apigw-api-key-id", properties.resolvedApiKeyId())
                .header("x-ncp-apigw-api-key", properties.resolvedApiKey())
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Naver Directions returned an empty response.");
        }

        return parseRouteResponse(response);
    }

    private RouteNavigationResponse parseRouteResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String message = root.path("message").asText("Unknown error");
                throw new IllegalStateException("Naver Directions request failed. code=" + code + ", message=" + message);
            }

            JsonNode routeNode = root.path("route").path(DEFAULT_ROUTE_OPTION);
            if (!routeNode.isArray() || routeNode.isEmpty()) {
                throw new IllegalStateException("Naver Directions response did not contain route." + DEFAULT_ROUTE_OPTION);
            }

            JsonNode routeItem = routeNode.get(0);
            JsonNode summary = routeItem.path("summary");
            int distanceMeters = summary.path("distance").asInt();
            int durationSeconds = (int) Math.max(0L, Math.round(summary.path("duration").asLong() / 1000.0));
            Integer tollFeeWon = summary.hasNonNull("tollFare") ? summary.path("tollFare").asInt() : null;
            Integer fuelPriceWon = summary.hasNonNull("fuelPrice") ? summary.path("fuelPrice").asInt() : null;
            String routePolyline = toPolyline(routeItem.path("path"));
            String currentDateTime = root.path("currentDateTime").asText(null);

            return new RouteNavigationResponse(
                    routePolyline,
                    distanceMeters,
                    durationSeconds,
                    tollFeeWon,
                    fuelPriceWon,
                    DEFAULT_ROUTE_OPTION,
                    currentDateTime
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse Naver Directions response.", exception);
        }
    }

    private String toPolyline(JsonNode pathNode) {
        if (!pathNode.isArray() || pathNode.isEmpty()) {
            return "";
        }

        List<String> points = new ArrayList<>();
        for (JsonNode pointNode : pathNode) {
            if (!pointNode.isArray() || pointNode.size() < 2) {
                continue;
            }

            double first = pointNode.get(0).asDouble(Double.NaN);
            double second = pointNode.get(1).asDouble(Double.NaN);
            if (Double.isNaN(first) || Double.isNaN(second)) {
                continue;
            }

            if (isLongitude(first) && isLatitude(second)) {
                points.add(formatPolylinePoint(second, first));
            } else if (isLatitude(first) && isLongitude(second)) {
                points.add(formatPolylinePoint(first, second));
            }
        }

        return String.join(";", points);
    }

    private String formatCoordinate(double longitude, double latitude) {
        return String.format(Locale.US, "%.6f,%.6f", longitude, latitude);
    }

    private String formatPolylinePoint(double latitude, double longitude) {
        return String.format(Locale.US, "%.6f,%.6f", latitude, longitude);
    }

    private String formatMileage(double mileageKmPerLiter) {
        return String.format(Locale.US, "%.1f", mileageKmPerLiter);
    }

    private boolean isLatitude(double value) {
        return value >= -90.0 && value <= 90.0;
    }

    private boolean isLongitude(double value) {
        return value >= -180.0 && value <= 180.0;
    }

    private void ensureEnabled() {
        if (!properties.enabled() || !properties.hasCredentials()) {
            throw new IllegalStateException("Naver Directions is disabled or NAVER_DIRECTIONS_API credentials are missing.");
        }
    }
}
