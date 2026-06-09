package com.oilpricedbmanager.service;

import com.oilpricedbmanager.config.RecommendationProperties;
import com.oilpricedbmanager.domain.FuelType;
import com.oilpricedbmanager.domain.StationFuelSnapshot;
import com.oilpricedbmanager.dto.DistanceBasis;
import com.oilpricedbmanager.dto.FuelPriceSummary;
import com.oilpricedbmanager.dto.NearbyStationSearchRequest;
import com.oilpricedbmanager.dto.RouteNavigationResponse;
import com.oilpricedbmanager.dto.RouteStationSearchRequest;
import com.oilpricedbmanager.dto.StationDetailResponse;
import com.oilpricedbmanager.dto.StationSearchItem;
import com.oilpricedbmanager.dto.StationSearchMode;
import com.oilpricedbmanager.dto.StationSearchResponse;
import com.oilpricedbmanager.dto.StationSearchSortOrder;
import com.oilpricedbmanager.external.naver.NaverDirectionsClient;
import com.oilpricedbmanager.repository.StationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class StationSearchService {
    private static final String COORDINATE_SYSTEM = "WGS84";
    private static final Logger log = LoggerFactory.getLogger(StationSearchService.class);

    private final StationRepository stationRepository;
    private final CostCalculationService costCalculationService;
    private final RecommendationProperties properties;
    private final NaverDirectionsClient naverDirectionsClient;

    public StationSearchService(
            StationRepository stationRepository,
            CostCalculationService costCalculationService,
            RecommendationProperties properties,
            NaverDirectionsClient naverDirectionsClient
    ) {
        this.stationRepository = stationRepository;
        this.costCalculationService = costCalculationService;
        this.properties = properties;
        this.naverDirectionsClient = naverDirectionsClient;
    }

    public StationSearchResponse searchNearby(NearbyStationSearchRequest request) {
        int radiusMeters = request.resolvedRadiusMeters(properties.defaultRadiusMeters());
        double fuelAmountLiters = request.resolvedFuelAmountLiters(properties.defaultRefuelLiters());
        double fuelEfficiency = request.resolvedFuelEfficiency(properties.defaultFuelEfficiencyKmPerLiter());

        List<FuelType> fuelTypes = request.resolvedFuelTypes();
        List<StationSearchItem> stations = stationRepository.findNearbySnapshots(
                        request.latitude(),
                        request.longitude(),
                        radiusMeters,
                        null,
                        fuelTypes
                )
                .stream()
                .map(snapshot -> mapSnapshot(
                        snapshot,
                        fuelTypes,
                        fuelAmountLiters,
                        fuelEfficiency,
                        DistanceBasis.REFERENCE_POINT,
                        null
                ))
                .flatMap(Optional::stream)
                .sorted(sortComparator(request.resolvedSortOrder()))
                .collect(Collectors.toList());

        return new StationSearchResponse(
                StationSearchMode.NEARBY,
                COORDINATE_SYSTEM,
                radiusMeters / 1000.0,
                stations.size(),
                request.resolvedReferenceLabel(),
                stations,
                null
        );
    }

    public StationSearchResponse searchRoute(RouteStationSearchRequest request) {
        int radiusMeters = request.resolvedRadiusMeters(properties.defaultRadiusMeters());
        double fuelAmountLiters = request.resolvedFuelAmountLiters(properties.defaultRefuelLiters());
        double fuelEfficiency = request.resolvedFuelEfficiency(properties.defaultFuelEfficiencyKmPerLiter());
        RouteNavigationResponse routeNavigation = resolveRouteNavigation(request, fuelEfficiency);
        String routePolyline = routeNavigation != null && routeNavigation.routePolyline() != null
                && !routeNavigation.routePolyline().isBlank()
                ? routeNavigation.routePolyline()
                : request.routePolyline();
        String routeWkt = buildRouteWkt(request, routePolyline);
        String referenceLabel = request.resolvedOriginLabel() + " -> " + request.resolvedDestinationLabel();

        List<FuelType> fuelTypes = request.resolvedFuelTypes();
        List<StationSearchItem> stations = stationRepository.findNearbySnapshots(
                        request.originLatitude(),
                        request.originLongitude(),
                        radiusMeters,
                        routeWkt,
                        fuelTypes
                )
                .stream()
                .map(snapshot -> mapSnapshot(
                        snapshot,
                        fuelTypes,
                        fuelAmountLiters,
                        fuelEfficiency,
                        DistanceBasis.ROUTE_LINE,
                        snapshot.distanceMeters()
                ))
                .flatMap(Optional::stream)
                .sorted(sortComparator(request.resolvedSortOrder()))
                .collect(Collectors.toList());

        return new StationSearchResponse(
                StationSearchMode.ROUTE,
                COORDINATE_SYSTEM,
                radiusMeters / 1000.0,
                stations.size(),
                referenceLabel,
                stations,
                routeNavigation
        );
    }

    public StationDetailResponse getStationDetail(String stationId) {
        StationFuelSnapshot snapshot = stationRepository.findSnapshotById(stationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown stationId: " + stationId));

        StationSearchItem item = mapSnapshot(
                        snapshot,
                        allFuelTypes(),
                        properties.defaultRefuelLiters(),
                        properties.defaultFuelEfficiencyKmPerLiter(),
                        DistanceBasis.REFERENCE_POINT,
                        null
                )
                .orElseThrow(() -> new IllegalStateException("Station snapshot is missing usable fuel data: " + stationId));

        return new StationDetailResponse(COORDINATE_SYSTEM, item);
    }

    private Optional<StationSearchItem> mapSnapshot(
            StationFuelSnapshot snapshot,
            List<FuelType> requestedFuelTypes,
            double fuelAmountLiters,
            double fuelEfficiencyKmPerLiter,
            DistanceBasis distanceBasis,
            Integer routeExtraDistanceMeters
    ) {
        var priceCandidates = new ArrayList<FuelPriceCandidate>();
        for (FuelType fuelType : requestedFuelTypes) {
            Integer price = priceFor(snapshot, fuelType);
            if (price != null) {
                priceCandidates.add(new FuelPriceCandidate(fuelType, price));
            }
        }

        if (priceCandidates.isEmpty()) {
            return Optional.empty();
        }

        FuelPriceCandidate cheapest = priceCandidates.stream()
                .min(Comparator.comparingInt(FuelPriceCandidate::priceWon))
                .orElseThrow();

        int travelCostWon = costCalculationService.travelCostWon(
                cheapest.priceWon(),
                snapshot.distanceMeters(),
                fuelEfficiencyKmPerLiter
        );
        int refuelCostWon = costCalculationService.refuelCostWon(cheapest.priceWon(), fuelAmountLiters);
        int estimatedTotalCostWon = travelCostWon + refuelCostWon;

        return Optional.of(new StationSearchItem(
                snapshot.uniId(),
                snapshot.stationName(),
                snapshot.pollDivCd(),
                snapshot.address(),
                snapshot.phone(),
                snapshot.lat(),
                snapshot.lon(),
                COORDINATE_SYSTEM,
                snapshot.distanceMeters(),
                distanceBasis,
                new FuelPriceSummary(
                        snapshot.gasLow(),
                        snapshot.gasHign(),
                        snapshot.disl(),
                        snapshot.lpg(),
                        snapshot.fuelUpdatedAt()
                ),
                cheapest.fuelType(),
                cheapest.priceWon(),
                travelCostWon,
                estimatedTotalCostWon,
                routeExtraDistanceMeters,
                buildNotes(snapshot, distanceBasis, routeExtraDistanceMeters),
                snapshot.fuelUpdatedAt()
        ));
    }

    private List<String> buildNotes(
            StationFuelSnapshot snapshot,
            DistanceBasis distanceBasis,
            Integer routeExtraDistanceMeters
    ) {
        var notes = new ArrayList<String>();
        notes.add("좌표계: " + COORDINATE_SYSTEM);
        notes.add("거리 기준: " + distanceBasis.name());
        if (snapshot.fuelUpdatedAt() != null) {
            notes.add("유가 최신 시각: " + snapshot.fuelUpdatedAt());
        }
        if (routeExtraDistanceMeters != null) {
            notes.add("경로 추가거리: " + routeExtraDistanceMeters + "m");
        }
        if (snapshot.phone() != null && !snapshot.phone().isBlank()) {
            notes.add("전화번호 등록됨");
        }
        return notes;
    }

    private RouteNavigationResponse resolveRouteNavigation(RouteStationSearchRequest request, double fuelEfficiencyKmPerLiter) {
        try {
            return naverDirectionsClient.fetchDrivingRoute(
                    request.originLongitude(),
                    request.originLatitude(),
                    request.destinationLongitude(),
                    request.destinationLatitude(),
                    fuelEfficiencyKmPerLiter
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Naver Directions route lookup failed. origin=({}, {}), destination=({}, {})",
                    request.originLatitude(),
                    request.originLongitude(),
                    request.destinationLatitude(),
                    request.destinationLongitude(),
                    exception
            );
            return null;
        }
    }

    private Comparator<StationSearchItem> sortComparator(StationSearchSortOrder sortOrder) {
        return switch (sortOrder) {
            case CHEAPEST_FUEL_ASC -> Comparator
                    .comparing(StationSearchItem::cheapestFuelPriceWon, Comparator.nullsLast(Integer::compareTo))
                    .thenComparingInt(StationSearchItem::distanceMeters)
                    .thenComparing(StationSearchItem::stationName);
            case ESTIMATED_TOTAL_COST_ASC -> Comparator
                    .comparing(StationSearchItem::estimatedTotalCostWon, Comparator.nullsLast(Integer::compareTo))
                    .thenComparingInt(StationSearchItem::distanceMeters)
                    .thenComparing(StationSearchItem::stationName);
            case DISTANCE_ASC -> Comparator
                    .comparingInt(StationSearchItem::distanceMeters)
                    .thenComparing(StationSearchItem::estimatedTotalCostWon)
                    .thenComparing(StationSearchItem::stationName);
        };
    }

    private Integer priceFor(StationFuelSnapshot snapshot, FuelType fuelType) {
        return switch (fuelType) {
            case REGULAR_GASOLINE -> snapshot.gasLow();
            case PREMIUM_GASOLINE -> snapshot.gasHign();
            case DIESEL -> snapshot.disl();
            case LPG -> snapshot.lpg();
        };
    }

    private String buildRouteWkt(RouteStationSearchRequest request, String routePolyline) {
        List<double[]> parsed = parsePolyline(routePolyline);
        if (parsed.size() < 2) {
            parsed = List.of(
                    new double[]{request.originLatitude(), request.originLongitude()},
                    new double[]{request.destinationLatitude(), request.destinationLongitude()}
            );
        }

        String coordinates = parsed.stream()
                .map(point -> formatCoordinate(point[1], point[0]))
                .collect(Collectors.joining(", "));
        return "LINESTRING (" + coordinates + ")";
    }

    private List<double[]> parsePolyline(String routePolyline) {
        if (routePolyline == null || routePolyline.isBlank()) {
            return List.of();
        }

        List<double[]> points = new ArrayList<>();
        String[] tokens = routePolyline.split("[;|\\n]");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String[] parts = trimmed.contains(",")
                    ? trimmed.split(",")
                    : trimmed.split("\\s+");
            if (parts.length < 2) {
                continue;
            }

            Double first = parseDouble(parts[0]);
            Double second = parseDouble(parts[1]);
            if (first == null || second == null) {
                continue;
            }

            if (isLatitude(first) && isLongitude(second)) {
                points.add(new double[]{first, second});
            } else if (isLatitude(second) && isLongitude(first)) {
                points.add(new double[]{second, first});
            }
        }

        return points;
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isLatitude(double value) {
        return value >= -90.0 && value <= 90.0;
    }

    private boolean isLongitude(double value) {
        return value >= -180.0 && value <= 180.0;
    }

    private String formatCoordinate(double lon, double lat) {
        return String.format(Locale.US, "%.6f %.6f", lon, lat);
    }

    private List<FuelType> allFuelTypes() {
        return List.of(
                FuelType.REGULAR_GASOLINE,
                FuelType.PREMIUM_GASOLINE,
                FuelType.DIESEL,
                FuelType.LPG
        );
    }

    private record FuelPriceCandidate(FuelType fuelType, int priceWon) {
    }
}
