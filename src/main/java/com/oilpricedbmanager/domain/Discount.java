package com.oilpricedbmanager.domain;

import java.time.LocalDateTime;

public record Discount(
        long discountId,
        String pollDivCd,
        String discountName,
        DiscountType discountType,
        int discountValue,
        FuelType fuelType,
        boolean active,
        LocalDateTime startsAt,
        LocalDateTime endsAt
) {
}
