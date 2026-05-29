package com.oilpricedbmanager.dto;

import com.oilpricedbmanager.domain.FuelType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record NearbyStationSearchRequest(
        double latitude,
        double longitude,
        Integer radiusMeters,
        Double radiusKm,
        Double fuelAmountLiters,
        Double fuelEfficiencyKmPerLiter,
        List<FuelType> fuelTypes,
        StationSearchSortOrder sortOrder,
        String referenceLabel
) {
    public int resolvedRadiusMeters(int defaultValue) {
        if (radiusMeters != null) {
            return radiusMeters;
        }
        if (radiusKm != null) {
            return (int) Math.round(radiusKm * 1000.0);
        }
        return defaultValue;
    }

    public double resolvedFuelAmountLiters(double defaultValue) {
        return fuelAmountLiters == null ? defaultValue : fuelAmountLiters;
    }

    public double resolvedFuelEfficiency(double defaultValue) {
        return fuelEfficiencyKmPerLiter == null ? defaultValue : fuelEfficiencyKmPerLiter;
    }

    public StationSearchSortOrder resolvedSortOrder() {
        return sortOrder == null ? StationSearchSortOrder.DISTANCE_ASC : sortOrder;
    }

    public List<FuelType> resolvedFuelTypes() {
        if (fuelTypes == null || fuelTypes.isEmpty()) {
            return new ArrayList<>(Arrays.asList(
                    FuelType.REGULAR_GASOLINE,
                    FuelType.PREMIUM_GASOLINE,
                    FuelType.DIESEL
            ));
        }
        return new ArrayList<>(fuelTypes);
    }

    public String resolvedReferenceLabel() {
        return referenceLabel == null || referenceLabel.isBlank() ? "현재 위치" : referenceLabel;
    }
}
