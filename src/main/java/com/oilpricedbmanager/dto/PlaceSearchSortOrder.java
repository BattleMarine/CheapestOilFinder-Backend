package com.oilpricedbmanager.dto;

public enum PlaceSearchSortOrder {
    ACCURACY,
    DISTANCE;

    public String kakaoValue() {
        return this == DISTANCE ? "distance" : "accuracy";
    }
}
