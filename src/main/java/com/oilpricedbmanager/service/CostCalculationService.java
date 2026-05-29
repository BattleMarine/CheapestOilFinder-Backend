package com.oilpricedbmanager.service;

import com.oilpricedbmanager.domain.Discount;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CostCalculationService {
    public int refuelCostWon(int pricePerLiter, double refuelLiters) {
        return (int) Math.round(pricePerLiter * refuelLiters);
    }

    public int travelCostWon(int pricePerLiter, int distanceMeters, double fuelEfficiencyKmPerLiter) {
        double distanceKm = distanceMeters / 1000.0;
        double consumedLiters = distanceKm / fuelEfficiencyKmPerLiter;
        return (int) Math.round(consumedLiters * pricePerLiter);
    }

    public int discountAmountWon(List<Discount> discounts, int refuelCostWon, double refuelLiters) {
        int total = 0;
        for (Discount discount : discounts) {
            total += switch (discount.discountType()) {
                case FIXED_AMOUNT -> discount.discountValue();
                case FIXED_PER_LITER -> (int) Math.round(discount.discountValue() * refuelLiters);
                case PERCENT -> (int) Math.round(refuelCostWon * (discount.discountValue() / 100.0));
            };
        }
        return Math.min(total, refuelCostWon);
    }
}
