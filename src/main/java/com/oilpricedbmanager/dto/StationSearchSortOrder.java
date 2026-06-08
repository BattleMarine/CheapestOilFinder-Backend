package com.oilpricedbmanager.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum StationSearchSortOrder {
    DISTANCE_ASC,
    CHEAPEST_FUEL_ASC,
    ESTIMATED_TOTAL_COST_ASC;

    @JsonCreator
    public static StationSearchSortOrder fromValue(String value) {
        if (value == null || value.isBlank()) {
            return DISTANCE_ASC;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PRICE_ASC", "CHEAPEST_PRICE_ASC" -> CHEAPEST_FUEL_ASC;
            default -> StationSearchSortOrder.valueOf(normalized);
        };
    }
}
