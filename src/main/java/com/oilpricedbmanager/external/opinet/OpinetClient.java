package com.oilpricedbmanager.external.opinet;

import com.oilpricedbmanager.domain.FuelType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpinetClient {
    private final OpinetProperties properties;
    private final RestClient restClient;

    public OpinetClient(OpinetProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    public String fetchAroundAllXml(double katecX, double katecY, int radiusMeters, FuelType fuelType, int sort) {
        ensureEnabled();
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/aroundAll.do")
                        .queryParam("code", properties.resolvedApiKey())
                        .queryParam("out", "xml")
                        .queryParam("x", String.format(java.util.Locale.US, "%.1f", katecX))
                        .queryParam("y", String.format(java.util.Locale.US, "%.1f", katecY))
                        .queryParam("radius", radiusMeters)
                        .queryParam("sort", sort)
                        .queryParam("prodcd", fuelType.opinetProductCode())
                        .build())
                .retrieve()
                .body(String.class);
    }

    public String fetchStationDetailXml(String stationId) {
        ensureEnabled();
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/detailById.do")
                        .queryParam("code", properties.resolvedApiKey())
                        .queryParam("out", "xml")
                        .queryParam("id", stationId)
                        .build())
                .retrieve()
                .body(String.class);
    }

    private void ensureEnabled() {
        if (!properties.enabled() || !properties.hasApiKey()) {
            throw new IllegalStateException("Opinet sync is disabled or OPINET_API_KEY is missing.");
        }
    }
}