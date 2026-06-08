package com.oilpricedbmanager.dto;

import java.util.List;

public record PlaceAutocompleteResponse(
        String query,
        int resultCount,
        List<PlaceAutocompleteItem> items
) {
}
