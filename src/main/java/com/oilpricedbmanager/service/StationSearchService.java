package com.oilpricedbmanager.service;

import com.oilpricedbmanager.config.RecommendationProperties;
import com.oilpricedbmanager.domain.FuelType;
import com.oilpricedbmanager.domain.StationFuelSnapshot;
import com.oilpricedbmanager.dto.DistanceBasis;
import com.oilpricedbmanager.dto.FuelPriceSummary;
import com.oilpricedbmanager.dto.NearbyStationSearchRequest;
import com.oilpricedbmanager.dto.RouteEndpointResponse;
import com.oilpricedbmanager.dto.RouteNavigationResponse;
import com.oilpricedbmanager.dto.RouteResultMode;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class StationSearchService {
    private static final String COORDINATE_SYSTEM = "WGS84";
    private static final String ROUTE_STATUS_OK = "OK";
    private static final String ROUTE_STATUS_SNAPPED = "SNAPPED_TO_NEAREST_ROAD";
    private static final String ROUTE_STATUS_UNAVAILABLE = "ROUTE_UNAVAILABLE";
    private static final int ROUTE_RESULT_LIMIT = 5;
    private static final int ROUTE_COST_CANDIDATE_LIMIT = 5;
    private static final int ROUTE_DISTANCE_CANDIDATE_LIMIT = 5;
    private static final int[] SNAP_RADII_METERS = {100, 200, 300, 500, 800, 1200};
    private static final double[][] SNAP_DIRECTIONS = {
            {1.0, 0.0}, {-1.0, 0.0}, {0.0, 1.0}, {0.0, -1.0},
            {0.7071, 0.7071}, {0.7071, -0.7071}, {-0.7071, 0.7071}, {-0.7071, -0.7071}
    };
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
        RouteResolution routeResolution = resolveRouteNavigation(request, fuelEfficiency);
        RouteNavigationResponse routeNavigation = routeResolution.routeNavigation();
        String routePolyline = routeNavigation != null && routeNavigation.routePolyline() != null
                && !routeNavigation.routePolyline().isBlank()
                ? routeNavigation.routePolyline()
                : request.routePolyline();
        String referenceLabel = request.resolvedOriginLabel() + " -> " + request.resolvedDestinationLabel();

        if (request.resolvedRouteResultMode() == RouteResultMode.ROUTE_ONLY) {
            return new StationSearchResponse(
                    StationSearchMode.ROUTE,
                    COORDINATE_SYSTEM,
                    radiusMeters / 1000.0,
                    0,
                    referenceLabel,
                    List.of(),
                    routeNavigation,
                    routeResolution.routeStatus(),
                    routeResolution.routeMessage(),
                    routeResolution.originalDestination(),
                    routeResolution.routeDestination(),
                    routeResolution.accessDistanceMeters()
            );
        }

        String routeWkt = buildRouteWkt(
                request,
                routePolyline,
                routeResolution.routeDestination().latitude(),
                routeResolution.routeDestination().longitude()
        );
        List<FuelType> fuelTypes = request.resolvedFuelTypes();
        List<StationSearchItem> routeCandidates = stationRepository.findNearbySnapshots(
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
                .collect(Collectors.toList());

        List<StationSearchItem> stations = selectRouteCandidates(routeCandidates)
                .stream()
                .map(station -> attachDetourRoute(station, request, routeResolution, fuelEfficiency, fuelAmountLiters))
                .sorted(routeRecommendationComparator())
                .limit(ROUTE_RESULT_LIMIT)
                .collect(Collectors.toList());

        return new StationSearchResponse(
                StationSearchMode.ROUTE,
                COORDINATE_SYSTEM,
                radiusMeters / 1000.0,
                stations.size(),
                referenceLabel,
                stations,
                routeNavigation,
                routeResolution.routeStatus(),
                routeResolution.routeMessage(),
                routeResolution.originalDestination(),
                routeResolution.routeDestination(),
                routeResolution.accessDistanceMeters()
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
                null,
                buildNotes(snapshot, distanceBasis, routeExtraDistanceMeters),
                snapshot.fuelUpdatedAt()
        ));
    }

    private List<StationSearchItem> selectRouteCandidates(List<StationSearchItem> stations) {
        var selected = new LinkedHashMap<String, StationSearchItem>();

        stations.stream()
                .sorted(routeRecommendationComparator())
                .limit(ROUTE_COST_CANDIDATE_LIMIT)
                .forEach(station -> selected.putIfAbsent(station.stationId(), station));

        stations.stream()
                .sorted(Comparator
                        .comparingInt(StationSearchItem::distanceMeters)
                        .thenComparing(StationSearchItem::estimatedTotalCostWon, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(StationSearchItem::stationName))
                .limit(ROUTE_DISTANCE_CANDIDATE_LIMIT)
                .forEach(station -> selected.putIfAbsent(station.stationId(), station));

        return new ArrayList<>(selected.values());
    }

    private Comparator<StationSearchItem> routeRecommendationComparator() {
        return Comparator
                .comparing(StationSearchItem::estimatedTotalCostWon, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(StationSearchItem::routeExtraDistanceMeters, Comparator.nullsLast(Integer::compareTo))
                .thenComparingLong(this::routeApproximationScore)
                .thenComparing(StationSearchItem::stationName);
    }

    private long routeApproximationScore(StationSearchItem station) {
        int fuelPriceWon = station.cheapestFuelPriceWon() == null ? Integer.MAX_VALUE : station.cheapestFuelPriceWon();
        return (long) station.distanceMeters() * fuelPriceWon;
    }

    private StationSearchItem attachDetourRoute(
            StationSearchItem station,
            RouteStationSearchRequest request,
            RouteResolution routeResolution,
            double fuelEfficiencyKmPerLiter,
            double fuelAmountLiters
    ) {
        RouteNavigationResponse baseRoute = routeResolution.routeNavigation();
        if (baseRoute == null) {
            return station;
        }

        try {
            RouteNavigationResponse detourRoute = naverDirectionsClient.fetchDrivingRouteViaWaypoint(
                    request.originLongitude(),
                    request.originLatitude(),
                    station.longitude(),
                    station.latitude(),
                    routeResolution.routeDestination().longitude(),
                    routeResolution.routeDestination().latitude(),
                    fuelEfficiencyKmPerLiter
            );
            int extraDistanceMeters = Math.max(0, detourRoute.distanceMeters() - baseRoute.distanceMeters());
            int travelCostWon = station.cheapestFuelPriceWon() == null
                    ? 0
                    : costCalculationService.travelCostWon(
                            station.cheapestFuelPriceWon(),
                            extraDistanceMeters,
                            fuelEfficiencyKmPerLiter
                    );
            int refuelCostWon = station.cheapestFuelPriceWon() == null
                    ? 0
                    : costCalculationService.refuelCostWon(station.cheapestFuelPriceWon(), fuelAmountLiters);
            return withDetourRoute(station, extraDistanceMeters, detourRoute, travelCostWon, travelCostWon + refuelCostWon);
        } catch (RuntimeException exception) {
            log.warn(
                    "Naver Directions waypoint route lookup failed. stationId={}, origin=({}, {}), waypoint=({}, {}), destination=({}, {})",
                    station.stationId(),
                    request.originLatitude(),
                    request.originLongitude(),
                    station.latitude(),
                    station.longitude(),
                    routeResolution.routeDestination().latitude(),
                    routeResolution.routeDestination().longitude(),
                    exception
            );
            return station;
        }
    }

    private StationSearchItem withDetourRoute(
            StationSearchItem station,
            Integer routeExtraDistanceMeters,
            RouteNavigationResponse detourRoute,
            Integer estimatedTravelFuelCostWon,
            Integer estimatedTotalCostWon
    ) {
        return new StationSearchItem(
                station.stationId(),
                station.stationName(),
                station.brandName(),
                station.address(),
                station.phone(),
                station.latitude(),
                station.longitude(),
                station.coordinateSystem(),
                station.distanceMeters(),
                station.distanceBasis(),
                station.fuelPrices(),
                station.cheapestFuelType(),
                station.cheapestFuelPriceWon(),
                estimatedTravelFuelCostWon,
                estimatedTotalCostWon,
                routeExtraDistanceMeters,
                detourRoute,
                buildDetourNotes(station.notes(), routeExtraDistanceMeters),
                station.updatedAt()
        );
    }

    private List<String> buildDetourNotes(List<String> notes, Integer routeExtraDistanceMeters) {
        var updatedNotes = new ArrayList<>(notes == null ? List.of() : notes);
        if (routeExtraDistanceMeters != null) {
            updatedNotes.add("detour extra distance: " + routeExtraDistanceMeters + "m");
        }
        return updatedNotes;
    }

    private List<String> buildNotes(
            StationFuelSnapshot snapshot,
            DistanceBasis distanceBasis,
            Integer routeExtraDistanceMeters
    ) {
        var notes = new ArrayList<String>();
        notes.add("coordinate system: " + COORDINATE_SYSTEM);
        notes.add("distance basis: " + distanceBasis.name());
        if (snapshot.fuelUpdatedAt() != null) {
            notes.add("fuel updated at: " + snapshot.fuelUpdatedAt());
        }
        if (routeExtraDistanceMeters != null) {
            notes.add("route extra distance: " + routeExtraDistanceMeters + "m");
        }
        if (snapshot.phone() != null && !snapshot.phone().isBlank()) {
            notes.add("phone registered");
        }
        return notes;
    }

    private RouteResolution resolveRouteNavigation(RouteStationSearchRequest request, double fuelEfficiencyKmPerLiter) {
        RouteEndpointResponse originalDestination = destinationEndpoint(
                request.destinationLatitude(),
                request.destinationLongitude(),
                request.resolvedDestinationLabel()
        );
        try {
            RouteNavigationResponse route = naverDirectionsClient.fetchDrivingRoute(
                    request.originLongitude(),
                    request.originLatitude(),
                    request.destinationLongitude(),
                    request.destinationLatitude(),
                    fuelEfficiencyKmPerLiter
            );
            return RouteResolution.ok(route, originalDestination);
        } catch (RuntimeException exception) {
            log.warn(
                    "Naver Directions route lookup failed. origin=({}, {}), destination=({}, {})",
                    request.originLatitude(),
                    request.originLongitude(),
                    request.destinationLatitude(),
                    request.destinationLongitude(),
                    exception
            );
            return resolveSnappedRouteNavigation(request, fuelEfficiencyKmPerLiter, originalDestination, exception);
        }
    }

    private RouteResolution resolveSnappedRouteNavigation(
            RouteStationSearchRequest request,
            double fuelEfficiencyKmPerLiter,
            RouteEndpointResponse originalDestination,
            RuntimeException originalException
    ) {
        for (RouteDestinationCandidate candidate : destinationCandidates(originalDestination.latitude(), originalDestination.longitude())) {
            try {
                RouteNavigationResponse route = naverDirectionsClient.fetchDrivingRoute(
                        request.originLongitude(),
                        request.originLatitude(),
                        candidate.longitude(),
                        candidate.latitude(),
                        fuelEfficiencyKmPerLiter
                );
                RouteEndpointResponse routeDestination = destinationEndpoint(
                        candidate.latitude(),
                        candidate.longitude(),
                        originalDestination.label()
                );
                log.info(
                        "Naver Directions destination snapped. original=({}, {}), snapped=({}, {}), accessDistanceMeters={}",
                        originalDestination.latitude(),
                        originalDestination.longitude(),
                        routeDestination.latitude(),
                        routeDestination.longitude(),
                        candidate.distanceMeters()
                );
                return RouteResolution.snapped(route, originalDestination, routeDestination, candidate.distanceMeters());
            } catch (RuntimeException exception) {
                log.debug(
                        "Naver Directions snapped route candidate failed. candidate=({}, {}), distanceMeters={}",
                        candidate.latitude(),
                        candidate.longitude(),
                        candidate.distanceMeters(),
                        exception
                );
            }
        }

        String message = originalException.getMessage() == null
                ? "Route lookup failed. No drivable nearby destination was found."
                : originalException.getMessage();
        return RouteResolution.unavailable(originalDestination, message);
    }

    private RouteEndpointResponse destinationEndpoint(double latitude, double longitude, String label) {
        return new RouteEndpointResponse(latitude, longitude, label);
    }

    private List<RouteDestinationCandidate> destinationCandidates(double latitude, double longitude) {
        List<RouteDestinationCandidate> candidates = new ArrayList<>();
        for (int radiusMeters : SNAP_RADII_METERS) {
            for (double[] direction : SNAP_DIRECTIONS) {
                double candidateLatitude = latitude + metersToLatitudeDegrees(radiusMeters * direction[0]);
                double candidateLongitude = longitude + metersToLongitudeDegrees(radiusMeters * direction[1], latitude);
                if (isLatitude(candidateLatitude) && isLongitude(candidateLongitude)) {
                    candidates.add(new RouteDestinationCandidate(
                            candidateLatitude,
                            candidateLongitude,
                            (int) Math.round(distanceMeters(latitude, longitude, candidateLatitude, candidateLongitude))
                    ));
                }
            }
        }
        candidates.sort(Comparator.comparingInt(RouteDestinationCandidate::distanceMeters));
        return candidates;
    }

    private double metersToLatitudeDegrees(double meters) {
        return meters / 111_320.0;
    }

    private double metersToLongitudeDegrees(double meters, double latitude) {
        double cosine = Math.cos(Math.toRadians(latitude));
        double denominator = 111_320.0 * Math.max(0.1, Math.abs(cosine));
        return meters / denominator;
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusMeters = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusMeters * c;
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

    private String buildRouteWkt(
            RouteStationSearchRequest request,
            String routePolyline,
            double routeDestinationLatitude,
            double routeDestinationLongitude
    ) {
        List<double[]> parsed = parsePolyline(routePolyline);
        if (parsed.size() < 2) {
            parsed = List.of(
                    new double[]{request.originLatitude(), request.originLongitude()},
                    new double[]{routeDestinationLatitude, routeDestinationLongitude}
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

    private record RouteDestinationCandidate(double latitude, double longitude, int distanceMeters) {
    }

    private record RouteResolution(
            RouteNavigationResponse routeNavigation,
            String routeStatus,
            String routeMessage,
            RouteEndpointResponse originalDestination,
            RouteEndpointResponse routeDestination,
            Integer accessDistanceMeters
    ) {
        private static RouteResolution ok(RouteNavigationResponse routeNavigation, RouteEndpointResponse destination) {
            return new RouteResolution(routeNavigation, ROUTE_STATUS_OK, null, destination, destination, 0);
        }

        private static RouteResolution snapped(
                RouteNavigationResponse routeNavigation,
                RouteEndpointResponse originalDestination,
                RouteEndpointResponse routeDestination,
                int accessDistanceMeters
        ) {
            return new RouteResolution(
                    routeNavigation,
                    ROUTE_STATUS_SNAPPED,
                    "Showing route to the nearest drivable point around the selected destination.",
                    originalDestination,
                    routeDestination,
                    accessDistanceMeters
            );
        }

        private static RouteResolution unavailable(RouteEndpointResponse destination, String routeMessage) {
            return new RouteResolution(
                    null,
                    ROUTE_STATUS_UNAVAILABLE,
                    routeMessage,
                    destination,
                    destination,
                    null
            );
        }
    }
}
