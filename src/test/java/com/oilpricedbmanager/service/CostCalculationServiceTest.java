package com.oilpricedbmanager.service;

import com.oilpricedbmanager.domain.Discount;
import com.oilpricedbmanager.domain.DiscountType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CostCalculationServiceTest {
    private final CostCalculationService service = new CostCalculationService();

    @Test
    void calculatesRefuelCost() {
        assertThat(service.refuelCostWon(1650, 30)).isEqualTo(49500);
    }

    @Test
    void calculatesTravelCost() {
        assertThat(service.travelCostWon(1650, 1000, 10)).isEqualTo(165);
    }

    @Test
    void calculatesDiscounts() {
        Discount perLiter = new Discount(1, "SKE", "per liter", DiscountType.FIXED_PER_LITER, 30, null, true, null, null);
        Discount fixed = new Discount(2, "SKE", "fixed", DiscountType.FIXED_AMOUNT, 1000, null, true, null, null);

        assertThat(service.discountAmountWon(List.of(perLiter, fixed), 49500, 30)).isEqualTo(1900);
    }
}
