package com.oilpricedbmanager.service;

import com.oilpricedbmanager.config.RecommendationProperties;
import com.oilpricedbmanager.domain.StationFuelSnapshot;
import com.oilpricedbmanager.dto.DistanceBasis;
import com.oilpricedbmanager.dto.RouteNavigationResponse;
import com.oilpricedbmanager.dto.RouteResultMode;
import com.oilpricedbmanager.dto.RouteStationSearchRequest;
import com.oilpricedbmanager.dto.StationSearchResponse;
import com.oilpricedbmanager.dto.StationSearchSortOrder;
import com.oilpricedbmanager.external.naver.NaverDirectionsClient;
import com.oilpricedbmanager.repository.StationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StationSearchServiceTest {
    private final StationRepository stationRepository = mock(StationRepository.class);
    private final NaverDirectionsClient naverDirectionsClient = mock(NaverDirectionsClient.class);
    private final CostCalculationService costCalculationService = new CostCalculationService();
    private final RecommendationProperties properties = new RecommendationProperties(5000, 30, 10);
    private final StationSearchService service = new StationSearchService(
            stationRepository,
            costCalculationService,
            properties,
            naverDirectionsClient
    );

    @Test
    void searchRoute_builds_route_wkt_and_maps_result() {
        StationFuelSnapshot snapshot = new StationFuelSnapshot(
                "UNI-001",
                "B027",
                "Test Station",
                "02-0000-0000",
                "Seoul Jung-gu",
                37.5665,
                126.9780,
                1730,
                1550,
                1490,
                null,
                1200,
                LocalDateTime.of(2026, 5, 28, 10, 0)
        );

        when(stationRepository.findNearbySnapshots(anyDouble(), anyDouble(), anyInt(), anyString(), anyList()))
                .thenReturn(List.of(snapshot));
        when(naverDirectionsClient.fetchDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteNavigationResponse(
                        "37.566500,126.978000;37.530000,127.020000;37.500000,127.100000",
                        12345,
                        678,
                        2000,
                        1500,
                        "traoptimal",
                        "2026-05-28T10:00:00"
                ));
        when(naverDirectionsClient.fetchDrivingRouteViaWaypoint(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteNavigationResponse(
                        "37.566500,126.978000;37.560000,126.990000;37.500000,127.100000",
                        13545,
                        720,
                        2000,
                        1600,
                        "traoptimal",
                        "2026-05-28T10:00:00"
                ));

        RouteStationSearchRequest request = new RouteStationSearchRequest(
                37.5665,
                126.9780,
                37.5000,
                127.1000,
                null,
                5000,
                null,
                30.0,
                10.0,
                List.of(),
                StationSearchSortOrder.DISTANCE_ASC,
                RouteResultMode.ROUTE_WITH_STATIONS,
                "Origin",
                "Destination"
        );

        StationSearchResponse response = service.searchRoute(request);

        ArgumentCaptor<String> routeWktCaptor = ArgumentCaptor.forClass(String.class);
        verify(stationRepository).findNearbySnapshots(anyDouble(), anyDouble(), anyInt(), routeWktCaptor.capture(), anyList());
        verify(naverDirectionsClient).fetchDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(naverDirectionsClient).fetchDrivingRouteViaWaypoint(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());

        assertThat(routeWktCaptor.getValue()).isEqualTo("LINESTRING (126.978000 37.566500, 127.020000 37.530000, 127.100000 37.500000)");
        assertThat(response.searchMode()).isEqualTo(com.oilpricedbmanager.dto.StationSearchMode.ROUTE);
        assertThat(response.stations()).hasSize(1);
        assertThat(response.stations().get(0).distanceBasis()).isEqualTo(DistanceBasis.ROUTE_LINE);
        assertThat(response.stations().get(0).routeExtraDistanceMeters()).isEqualTo(1200);
        assertThat(response.stations().get(0).detourRoute()).isNotNull();
        assertThat(response.route()).isNotNull();
        assertThat(response.route().distanceMeters()).isEqualTo(12345);
        assertThat(response.route().durationSeconds()).isEqualTo(678);
        assertThat(response.route().routeOption()).isEqualTo("traoptimal");
    }

    @Test
    void searchRoute_route_only_skips_station_lookup() {
        when(naverDirectionsClient.fetchDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteNavigationResponse(
                        "37.566500,126.978000;37.500000,127.100000",
                        12345,
                        678,
                        2000,
                        1500,
                        "traoptimal",
                        "2026-05-28T10:00:00"
                ));

        RouteStationSearchRequest request = new RouteStationSearchRequest(
                37.5665,
                126.9780,
                37.5000,
                127.1000,
                null,
                5000,
                null,
                30.0,
                10.0,
                List.of(),
                StationSearchSortOrder.DISTANCE_ASC,
                RouteResultMode.ROUTE_ONLY,
                "Origin",
                "Destination"
        );

        StationSearchResponse response = service.searchRoute(request);

        verify(stationRepository, never()).findNearbySnapshots(anyDouble(), anyDouble(), anyInt(), anyString(), anyList());
        verify(naverDirectionsClient, never()).fetchDrivingRouteViaWaypoint(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertThat(response.resultCount()).isZero();
        assertThat(response.stations()).isEmpty();
        assertThat(response.route()).isNotNull();
        assertThat(response.route().routeOption()).isEqualTo("traoptimal");
    }

    @Test
    void searchRoute_returns_only_top_five_station_recommendations() {
        when(stationRepository.findNearbySnapshots(anyDouble(), anyDouble(), anyInt(), anyString(), anyList()))
                .thenReturn(List.of(
                        routeSnapshot("UNI-001", "Station 1", 600, 1550),
                        routeSnapshot("UNI-002", "Station 2", 500, 1560),
                        routeSnapshot("UNI-003", "Station 3", 400, 1570),
                        routeSnapshot("UNI-004", "Station 4", 300, 1580),
                        routeSnapshot("UNI-005", "Station 5", 200, 1590),
                        routeSnapshot("UNI-006", "Station 6", 100, 3000)
                ));
        when(naverDirectionsClient.fetchDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteNavigationResponse(
                        "37.566500,126.978000;37.500000,127.100000",
                        10000,
                        600,
                        0,
                        1000,
                        "traoptimal",
                        "2026-05-28T10:00:00"
                ));
        when(naverDirectionsClient.fetchDrivingRouteViaWaypoint(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteNavigationResponse(
                        "37.566500,126.978000;37.560000,126.990000;37.500000,127.100000",
                        11000,
                        700,
                        0,
                        1100,
                        "traoptimal",
                        "2026-05-28T10:00:00"
                ));

        RouteStationSearchRequest request = new RouteStationSearchRequest(
                37.5665,
                126.9780,
                37.5000,
                127.1000,
                null,
                5000,
                null,
                30.0,
                10.0,
                List.of(),
                StationSearchSortOrder.DISTANCE_ASC,
                RouteResultMode.ROUTE_WITH_STATIONS,
                "Origin",
                "Destination"
        );

        StationSearchResponse response = service.searchRoute(request);

        assertThat(response.resultCount()).isEqualTo(5);
        assertThat(response.stations()).hasSize(5);
        verify(naverDirectionsClient, times(6)).fetchDrivingRouteViaWaypoint(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }
    @Test
    void searchRoute_sorts_detour_results_by_estimated_total_cost() {
        when(stationRepository.findNearbySnapshots(anyDouble(), anyDouble(), anyInt(), anyString(), anyList()))
                .thenReturn(List.of(
                        routeSnapshot("UNI-EXPENSIVE", "On Route Expensive", 0, 2000),
                        routeSnapshot("UNI-CHEAP", "Small Detour Cheap", 10, 1000)
                ));
        when(naverDirectionsClient.fetchDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteNavigationResponse(
                        "37.566500,126.978000;37.500000,127.100000",
                        10000,
                        600,
                        0,
                        1000,
                        "traoptimal",
                        "2026-05-28T10:00:00"
                ));
        when(naverDirectionsClient.fetchDrivingRouteViaWaypoint(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(
                        new RouteNavigationResponse(
                                "37.566500,126.978000;37.560000,126.990000;37.500000,127.100000",
                                10500,
                                650,
                                0,
                                1050,
                                "traoptimal",
                                "2026-05-28T10:00:00"
                        ),
                        new RouteNavigationResponse(
                                "37.566500,126.978000;37.500000,127.100000",
                                10000,
                                600,
                                0,
                                1000,
                                "traoptimal",
                                "2026-05-28T10:00:00"
                        )
                );

        RouteStationSearchRequest request = new RouteStationSearchRequest(
                37.5665,
                126.9780,
                37.5000,
                127.1000,
                null,
                5000,
                null,
                30.0,
                10.0,
                List.of(),
                StationSearchSortOrder.DISTANCE_ASC,
                RouteResultMode.ROUTE_WITH_STATIONS,
                "Origin",
                "Destination"
        );

        StationSearchResponse response = service.searchRoute(request);

        assertThat(response.stations()).hasSize(2);
        assertThat(response.stations().get(0).stationId()).isEqualTo("UNI-CHEAP");
        assertThat(response.stations().get(0).routeExtraDistanceMeters()).isEqualTo(500);
        assertThat(response.stations().get(0).estimatedTotalCostWon()).isEqualTo(30050);
        assertThat(response.stations().get(1).stationId()).isEqualTo("UNI-EXPENSIVE");
        assertThat(response.stations().get(1).routeExtraDistanceMeters()).isEqualTo(0);
        assertThat(response.stations().get(1).estimatedTotalCostWon()).isEqualTo(60000);
    }
    @Test
    void searchRoute_orders_final_recommendations_by_total_cost_not_travel_cost_descending() {
        when(stationRepository.findNearbySnapshots(anyDouble(), anyDouble(), anyInt(), anyString(), anyList()))
                .thenReturn(List.of(
                        routeSnapshot("UNI-HIGH-TRAVEL", "High Travel Cost", 10, 2000),
                        routeSnapshot("UNI-LOW-TOTAL", "Low Total Cost", 20, 1000)
                ));
        when(naverDirectionsClient.fetchDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteNavigationResponse(
                        "37.566500,126.978000;37.500000,127.100000",
                        10000,
                        600,
                        0,
                        1000,
                        "traoptimal",
                        "2026-05-28T10:00:00"
                ));
        when(naverDirectionsClient.fetchDrivingRouteViaWaypoint(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(
                        detourRoute(11000),
                        detourRoute(60000)
                );

        RouteStationSearchRequest request = new RouteStationSearchRequest(
                37.5665,
                126.9780,
                37.5000,
                127.1000,
                null,
                5000,
                null,
                30.0,
                10.0,
                List.of(),
                StationSearchSortOrder.DISTANCE_ASC,
                RouteResultMode.ROUTE_WITH_STATIONS,
                "Origin",
                "Destination"
        );

        StationSearchResponse response = service.searchRoute(request);

        assertThat(response.stations()).hasSize(2);
        assertThat(response.stations().get(0).stationId()).isEqualTo("UNI-LOW-TOTAL");
        assertThat(response.stations().get(0).estimatedTravelFuelCostWon()).isEqualTo(100);
        assertThat(response.stations().get(0).estimatedTotalCostWon()).isEqualTo(30100);
        assertThat(response.stations().get(1).stationId()).isEqualTo("UNI-HIGH-TRAVEL");
        assertThat(response.stations().get(1).estimatedTravelFuelCostWon()).isEqualTo(10000);
        assertThat(response.stations().get(1).estimatedTotalCostWon()).isEqualTo(70000);
    }
    @Test
    void searchRoute_keeps_nearest_candidates_for_short_routes() {
        when(stationRepository.findNearbySnapshots(anyDouble(), anyDouble(), anyInt(), anyString(), anyList()))
                .thenReturn(List.of(
                        routeSnapshot("UNI-CHEAP-1", "Cheap 1", 100, 1000),
                        routeSnapshot("UNI-CHEAP-2", "Cheap 2", 101, 1000),
                        routeSnapshot("UNI-CHEAP-3", "Cheap 3", 102, 1000),
                        routeSnapshot("UNI-CHEAP-4", "Cheap 4", 103, 1000),
                        routeSnapshot("UNI-CHEAP-5", "Cheap 5", 104, 1000),
                        routeSnapshot("UNI-NEAR", "Near Route", 0, 2000)
                ));
        when(naverDirectionsClient.fetchDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteNavigationResponse(
                        "37.566500,126.978000;37.500000,127.100000",
                        10000,
                        600,
                        0,
                        1000,
                        "traoptimal",
                        "2026-05-28T10:00:00"
                ));
        when(naverDirectionsClient.fetchDrivingRouteViaWaypoint(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(
                        detourRoute(410000),
                        detourRoute(410000),
                        detourRoute(410000),
                        detourRoute(410000),
                        detourRoute(410000),
                        detourRoute(10000)
                );

        RouteStationSearchRequest request = new RouteStationSearchRequest(
                37.5665,
                126.9780,
                37.5000,
                127.1000,
                null,
                5000,
                null,
                30.0,
                10.0,
                List.of(),
                StationSearchSortOrder.DISTANCE_ASC,
                RouteResultMode.ROUTE_WITH_STATIONS,
                "Origin",
                "Destination"
        );

        StationSearchResponse response = service.searchRoute(request);

        assertThat(response.stations()).hasSize(5);
        assertThat(response.stations().get(0).stationId()).isEqualTo("UNI-NEAR");
        assertThat(response.stations().get(0).routeExtraDistanceMeters()).isEqualTo(0);
        assertThat(response.stations().get(0).estimatedTotalCostWon()).isEqualTo(60000);
        verify(naverDirectionsClient, times(6)).fetchDrivingRouteViaWaypoint(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }
    @Test
    void searchRoute_snaps_destination_to_nearby_road_when_direct_route_fails() {
        when(naverDirectionsClient.fetchDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new IllegalStateException("No route to selected destination"))
                .thenReturn(new RouteNavigationResponse(
                        "37.529900,126.929500;37.535899,126.919000",
                        2100,
                        420,
                        0,
                        1000,
                        "traoptimal",
                        "2026-06-10T12:00:00"
                ));

        RouteStationSearchRequest request = new RouteStationSearchRequest(
                37.5299,
                126.9295,
                37.5350,
                126.9190,
                null,
                5000,
                null,
                30.0,
                12.0,
                List.of(),
                StationSearchSortOrder.ESTIMATED_TOTAL_COST_ASC,
                RouteResultMode.ROUTE_ONLY,
                "Origin",
                "Bamseom"
        );

        StationSearchResponse response = service.searchRoute(request);

        verify(naverDirectionsClient, times(2)).fetchDrivingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(stationRepository, never()).findNearbySnapshots(anyDouble(), anyDouble(), anyInt(), anyString(), anyList());
        assertThat(response.route()).isNotNull();
        assertThat(response.routeStatus()).isEqualTo("SNAPPED_TO_NEAREST_ROAD");
        assertThat(response.accessDistanceMeters()).isNotNull();
        assertThat(response.accessDistanceMeters()).isGreaterThan(0);
        assertThat(response.originalDestination().latitude()).isEqualTo(37.5350);
        assertThat(response.originalDestination().longitude()).isEqualTo(126.9190);
        assertThat(response.routeDestination().latitude()).isNotEqualTo(37.5350);
    }
    @Test
    void getStationDetail_returns_full_fuel_snapshot() {
        StationFuelSnapshot snapshot = new StationFuelSnapshot(
                "UNI-002",
                "B034",
                "Premium Station",
                "02-1111-2222",
                "Seoul Seocho-gu",
                37.5000,
                127.0000,
                1820,
                1600,
                1510,
                900,
                800,
                LocalDateTime.of(2026, 5, 28, 10, 0)
        );

        when(stationRepository.findSnapshotById("UNI-002")).thenReturn(Optional.of(snapshot));

        var response = service.getStationDetail("UNI-002");

        assertThat(response.coordinateSystem()).isEqualTo("WGS84");
        assertThat(response.station().stationId()).isEqualTo("UNI-002");
        assertThat(response.station().fuelPrices().lpgWon()).isEqualTo(900);
        assertThat(response.station().distanceBasis()).isEqualTo(DistanceBasis.REFERENCE_POINT);
    }

    private RouteNavigationResponse detourRoute(int distanceMeters) {
        return new RouteNavigationResponse(
                "37.566500,126.978000;37.500000,127.100000",
                distanceMeters,
                600,
                0,
                1000,
                "traoptimal",
                "2026-05-28T10:00:00"
        );
    }
    private StationFuelSnapshot routeSnapshot(String stationId, String stationName, int distanceMeters, int regularGasolineWon) {
        return new StationFuelSnapshot(
                stationId,
                "B027",
                stationName,
                "02-0000-0000",
                "Seoul Jung-gu",
                37.5665,
                126.9780,
                null,
                regularGasolineWon,
                null,
                null,
                distanceMeters,
                LocalDateTime.of(2026, 5, 28, 10, 0)
        );
    }
}

