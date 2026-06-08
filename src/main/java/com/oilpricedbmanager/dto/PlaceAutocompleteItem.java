package com.oilpricedbmanager.dto;

import java.util.List;

public record PlaceAutocompleteItem(
        String entryType,
        String displayText,
        String primaryText,
        String secondaryText,
        String sourceType,
        String sourceRef,
        Double latitude,
        Double longitude
) {
    public static PlaceAutocompleteItem of(String entryType, String displayText, String primaryText, String secondaryText, String sourceType, String sourceRef, Double latitude, Double longitude) {
        return new PlaceAutocompleteItem(entryType, displayText, primaryText, secondaryText, sourceType, sourceRef, latitude, longitude);
    }
}
