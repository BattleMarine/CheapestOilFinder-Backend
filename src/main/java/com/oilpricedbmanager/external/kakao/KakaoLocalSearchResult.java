package com.oilpricedbmanager.external.kakao;

import com.oilpricedbmanager.dto.PlaceSearchItem;

import java.util.List;

public record KakaoLocalSearchResult(
        int totalCount,
        int pageableCount,
        boolean end,
        List<PlaceSearchItem> items
) {
}
