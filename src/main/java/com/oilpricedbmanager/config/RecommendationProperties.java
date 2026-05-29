package com.oilpricedbmanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.recommendation")
public record RecommendationProperties(
        int defaultRadiusMeters,
        double defaultRefuelLiters,
        double defaultFuelEfficiencyKmPerLiter
) {
    public RecommendationProperties {
        if (defaultRadiusMeters <= 0) {
            defaultRadiusMeters = 5000;
        }
        if (defaultRefuelLiters <= 0) {
            defaultRefuelLiters = 30;
        }
        if (defaultFuelEfficiencyKmPerLiter <= 0) {
            defaultFuelEfficiencyKmPerLiter = 10;
        }
    }
}
