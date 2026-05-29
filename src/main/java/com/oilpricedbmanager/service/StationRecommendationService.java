package com.oilpricedbmanager.service;

import com.oilpricedbmanager.config.RecommendationProperties;
import com.oilpricedbmanager.domain.Discount;
import com.oilpricedbmanager.domain.StationFuelCandidate;
import com.oilpricedbmanager.dto.NearbyStationRequest;
import com.oilpricedbmanager.dto.StationRecommendationResponse;
import com.oilpricedbmanager.repository.DiscountRepository;
import com.oilpricedbmanager.repository.StationRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class StationRecommendationService {
    private final StationRepository stationRepository;
    private final DiscountRepository discountRepository;
    private final CostCalculationService costCalculationService;
    private final RecommendationProperties properties;

    public StationRecommendationService(
            StationRepository stationRepository,
            DiscountRepository discountRepository,
            CostCalculationService costCalculationService,
            RecommendationProperties properties
    ) {
        this.stationRepository = stationRepository;
        this.discountRepository = discountRepository;
        this.costCalculationService = costCalculationService;
        this.properties = properties;
    }

    public List<StationRecommendationResponse> recommendNearby(NearbyStationRequest request) {
        int radiusMeters = request.resolvedRadiusMeters(properties.defaultRadiusMeters());
        double refuelLiters = request.resolvedRefuelLiters(properties.defaultRefuelLiters());
        double fuelEfficiency = request.resolvedFuelEfficiency(properties.defaultFuelEfficiencyKmPerLiter());

        List<StationFuelCandidate> candidates = stationRepository.findNearby(
                request.lat(),
                request.lon(),
                radiusMeters,
                request.fuelType()
        );

        List<StationRecommendationResponse> calculated = new ArrayList<>();
        for (StationFuelCandidate candidate : candidates) {
            int refuelCostWon = costCalculationService.refuelCostWon(candidate.pricePerLiter(), refuelLiters);
            int travelCostWon = costCalculationService.travelCostWon(
                    candidate.pricePerLiter(),
                    candidate.distanceMeters(),
                    fuelEfficiency
            );
            List<Discount> discounts = discountRepository.findApplicable(
                    candidate.pollDivCd(),
                    request.fuelType(),
                    request.discountIds()
            );
            int discountAmountWon = costCalculationService.discountAmountWon(discounts, refuelCostWon, refuelLiters);
            int estimatedTotalCostWon = refuelCostWon + travelCostWon - discountAmountWon;

            calculated.add(new StationRecommendationResponse(
                    0,
                    candidate.uniId(),
                    candidate.stationName(),
                    candidate.pollDivCd(),
                    candidate.phone(),
                    candidate.address(),
                    candidate.lat(),
                    candidate.lon(),
                    candidate.fuelType(),
                    candidate.pricePerLiter(),
                    candidate.distanceMeters(),
                    refuelCostWon,
                    travelCostWon,
                    discountAmountWon,
                    estimatedTotalCostWon,
                    candidate.fuelUpdatedAt()
            ));
        }

        calculated.sort(Comparator
                .comparingInt(StationRecommendationResponse::estimatedTotalCostWon)
                .thenComparingInt(StationRecommendationResponse::distanceMeters));

        List<StationRecommendationResponse> ranked = new ArrayList<>();
        for (int i = 0; i < calculated.size(); i++) {
            StationRecommendationResponse item = calculated.get(i);
            ranked.add(new StationRecommendationResponse(
                    i + 1,
                    item.stationId(),
                    item.stationName(),
                    item.brandCode(),
                    item.phone(),
                    item.address(),
                    item.lat(),
                    item.lon(),
                    item.fuelType(),
                    item.pricePerLiter(),
                    item.distanceMeters(),
                    item.refuelCostWon(),
                    item.travelCostWon(),
                    item.discountAmountWon(),
                    item.estimatedTotalCostWon(),
                    item.fuelUpdatedAt()
            ));
        }
        return ranked;
    }
}
