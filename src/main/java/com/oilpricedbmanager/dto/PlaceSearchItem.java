package com.oilpricedbmanager.dto;

public record PlaceSearchItem(
        String placeId,
        String placeName,
        String roadAddressName,
        String addressName,
        String categoryName,
        String phone,
        String placeUrl,
        Double latitude,
        Double longitude,
        Integer distanceMeters
) {
}
