package com.oilpricedbmanager.external.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oilpricedbmanager.dto.PlaceSearchItem;
import com.oilpricedbmanager.dto.PlaceSearchSortOrder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class KakaoLocalClient {
    private final KakaoLocalProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public KakaoLocalClient(KakaoLocalProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.objectMapper = objectMapper;
    }

    public KakaoLocalSearchResult searchKeyword(
            String query,
            int page,
            int size,
            PlaceSearchSortOrder sortOrder,
            Double centerLatitude,
            Double centerLongitude,
            Integer radiusMeters
    ) {
        ensureEnabled();
        String response = restClient.get()
                .uri(uriBuilder -> {
                    var uri = uriBuilder.path("/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .queryParam("sort", sortOrder == null ? PlaceSearchSortOrder.ACCURACY.kakaoValue() : sortOrder.kakaoValue());
                    if (centerLongitude != null && centerLatitude != null) {
                        uri.queryParam("x", formatCoordinate(centerLongitude))
                           .queryParam("y", formatCoordinate(centerLatitude));
                    }
                    if (radiusMeters != null && radiusMeters > 0) {
                        uri.queryParam("radius", radiusMeters);
                    }
                    return uri.build();
                })
                .header("Authorization", "KakaoAK " + properties.resolvedApiKey())
                .retrieve()
                .body(String.class);
        return parse(response);
    }

    public KakaoLocalSearchResult searchAddress(String query, int page, int size) {
        ensureEnabled();
        String response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/address.json")
                        .queryParam("query", query)
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .header("Authorization", "KakaoAK " + properties.resolvedApiKey())
                .retrieve()
                .body(String.class);
        return parse(response);
    }

    public KakaoLocalSearchResult parse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode meta = root.path("meta");
            int totalCount = meta.path("total_count").asInt(0);
            int pageableCount = meta.path("pageable_count").asInt(0);
            boolean end = meta.path("is_end").asBoolean(false);

            List<PlaceSearchItem> items = new ArrayList<>();
            for (JsonNode doc : root.path("documents")) {
                items.add(new PlaceSearchItem(
                        firstNonBlank(doc.path("id").asText(null), null),
                        firstNonBlank(doc.path("place_name").asText(null), doc.path("address_name").asText(null), doc.path("road_address_name").asText(null)),
                        blankToNull(doc.path("road_address_name").asText(null)),
                        blankToNull(doc.path("address_name").asText(null)),
                        blankToNull(doc.path("category_name").asText(null)),
                        blankToNull(doc.path("phone").asText(null)),
                        blankToNull(doc.path("place_url").asText(null)),
                        parseDouble(doc.path("y").asText(null)),
                        parseDouble(doc.path("x").asText(null)),
                        parseInteger(doc.path("distance").asText(null))
                ));
            }

            return new KakaoLocalSearchResult(totalCount, pageableCount, end, items);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse Kakao Local API response.", exception);
        }
    }

    private void ensureEnabled() {
        if (!properties.enabled() || !properties.hasCredentials()) {
            throw new IllegalStateException("Kakao Local API is disabled or KAKAO_LOCAL_API_KEY is missing.");
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String formatCoordinate(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
