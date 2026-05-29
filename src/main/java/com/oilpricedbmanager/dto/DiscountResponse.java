package com.oilpricedbmanager.dto;

import com.oilpricedbmanager.domain.DiscountType;
import com.oilpricedbmanager.domain.FuelType;

public record DiscountResponse(
        long discountId,
        String pollDivCd,
        String discountName,
        DiscountType discountType,
        int discountValue,
        FuelType fuelType
) {
}
