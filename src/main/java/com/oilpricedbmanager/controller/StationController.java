package com.oilpricedbmanager.controller;

import com.oilpricedbmanager.domain.FuelType;
import com.oilpricedbmanager.dto.NearbyStationSearchRequest;
import com.oilpricedbmanager.dto.RouteStationSearchRequest;
import com.oilpricedbmanager.dto.StationDetailResponse;
import com.oilpricedbmanager.dto.StationSearchResponse;
import com.oilpricedbmanager.dto.StationSearchSortOrder;
import com.oilpricedbmanager.service.StationSearchService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/stations")
public class StationController {
    private final StationSearchService stationSearchService;

    public StationController(StationSearchService stationSearchService) {
        this.stationSearchService = stationSearchService;
    }

    @GetMapping({"/nearby", "/search/nearby"})
    public StationSearchResponse nearby(
            @RequestParam(name = "latitude", required = false) @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @RequestParam(name = "lat", required = false) @DecimalMin("-90.0") @DecimalMax("90.0") Double legacyLat,
            @RequestParam(name = "longitude", required = false) @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @RequestParam(name = "lon", required = false) @DecimalMin("-180.0") @DecimalMax("180.0") Double legacyLon,
            @RequestParam(name = "radiusMeters", required = false) Integer radiusMeters,
            @RequestParam(name = "radiusKm", required = false) Double radiusKm,
            @RequestParam(name = "fuelAmountLiters", required = false) Double fuelAmountLiters,
            @RequestParam(name = "refuelLiters", required = false) Double legacyRefuelLiters,
            @RequestParam(name = "fuelEfficiencyKmPerLiter", required = false) Double fuelEfficiencyKmPerLiter,
            @RequestParam(name = "fuelTypes", required = false) List<FuelType> fuelTypes,
            @RequestParam(name = "fuelType", required = false) FuelType legacyFuelType,
            @RequestParam(name = "sortOrder", required = false) StationSearchSortOrder sortOrder,
            @RequestParam(name = "referenceLabel", required = false) String referenceLabel
    ) {
        List<FuelType> resolvedFuelTypes = resolveFuelTypes(fuelTypes, legacyFuelType);
        Double resolvedLatitude = firstNonNull(latitude, legacyLat);
        Double resolvedLongitude = firstNonNull(longitude, legacyLon);
        if (resolvedLatitude == null || resolvedLongitude == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude and longitude are required");
        }

        return stationSearchService.searchNearby(new NearbyStationSearchRequest(
                resolvedLatitude,
                resolvedLongitude,
                radiusMeters,
                radiusKm,
                firstNonNull(fuelAmountLiters, legacyRefuelLiters),
                fuelEfficiencyKmPerLiter,
                resolvedFuelTypes,
                sortOrder,
                referenceLabel
        ));
    }

    @PostMapping({"/route", "/search/route"})
    public StationSearchResponse route(@RequestBody RouteStationSearchRequest request) {
        return stationSearchService.searchRoute(request);
    }

    @GetMapping("/{stationId}")
    public StationDetailResponse getStation(@PathVariable String stationId) {
        return stationSearchService.getStationDetail(stationId);
    }

    private List<FuelType> resolveFuelTypes(List<FuelType> fuelTypes, FuelType legacyFuelType) {
        if (fuelTypes != null && !fuelTypes.isEmpty()) {
            return new ArrayList<>(fuelTypes);
        }
        if (legacyFuelType != null) {
            return new ArrayList<>(List.of(legacyFuelType));
        }
        return new ArrayList<>(List.of(
                FuelType.REGULAR_GASOLINE,
                FuelType.PREMIUM_GASOLINE,
                FuelType.DIESEL
        ));
    }

    private Double firstNonNull(Double first, Double second) {
        return first != null ? first : second;
    }
}
