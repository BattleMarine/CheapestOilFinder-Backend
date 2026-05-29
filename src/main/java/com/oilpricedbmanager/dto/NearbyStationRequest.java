package com.oilpricedbmanager.dto;

import com.oilpricedbmanager.domain.FuelType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record NearbyStationRequest(
        @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
        @DecimalMin("-180.0") @DecimalMax("180.0") double lon,
        @Min(100) @Max(50000) Integer radiusMeters,
        @NotNull FuelType fuelType,
        @DecimalMin("1.0") @DecimalMax("200.0") Double refuelLiters,
        @DecimalMin("1.0") @DecimalMax("100.0") Double fuelEfficiencyKmPerLiter,
        List<Long> discountIds
) {
    public int resolvedRadiusMeters(int defaultValue) {
        return radiusMeters == null ? defaultValue : radiusMeters;
    }

    public double resolvedRefuelLiters(double defaultValue) {
        return refuelLiters == null ? defaultValue : refuelLiters;
    }

    public double resolvedFuelEfficiency(double defaultValue) {
        return fuelEfficiencyKmPerLiter == null ? defaultValue : fuelEfficiencyKmPerLiter;
    }
}
