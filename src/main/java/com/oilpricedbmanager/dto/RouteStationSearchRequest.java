package com.oilpricedbmanager.dto;

import com.oilpricedbmanager.domain.FuelType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record RouteStationSearchRequest(
        double originLatitude,
        double originLongitude,
        double destinationLatitude,
        double destinationLongitude,
        String routePolyline,
        Integer radiusMeters,
        Double radiusKm,
        Double fuelAmountLiters,
        Double fuelEfficiencyKmPerLiter,
        List<FuelType> fuelTypes,
        StationSearchSortOrder sortOrder,
        RouteResultMode routeResultMode,
        String originLabel,
        String destinationLabel
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

    public RouteResultMode resolvedRouteResultMode() {
        return routeResultMode == null ? RouteResultMode.ROUTE_WITH_STATIONS : routeResultMode;
    }

    public String resolvedOriginLabel() {
        return originLabel == null || originLabel.isBlank() ? "출발지" : originLabel;
    }

    public String resolvedDestinationLabel() {
        return destinationLabel == null || destinationLabel.isBlank() ? "목적지" : destinationLabel;
    }
}
