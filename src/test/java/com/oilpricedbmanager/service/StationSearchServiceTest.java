package com.oilpricedbmanager.service;

import com.oilpricedbmanager.config.RecommendationProperties;
import com.oilpricedbmanager.domain.StationFuelSnapshot;
import com.oilpricedbmanager.dto.DistanceBasis;
import com.oilpricedbmanager.dto.RouteNavigationResponse;
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
import static org.mockito.Mockito.verify;
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
        when(naverDirectionsClient.fetchDrivingRoute(
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyDouble()
        )).thenReturn(new RouteNavigationResponse(
                "37.566500,126.978000;37.530000,127.020000;37.500000,127.100000",
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
                "Origin",
                "Destination"
        );

        StationSearchResponse response = service.searchRoute(request);

        ArgumentCaptor<String> routeWktCaptor = ArgumentCaptor.forClass(String.class);
        verify(stationRepository).findNearbySnapshots(
                anyDouble(),
                anyDouble(),
                anyInt(),
                routeWktCaptor.capture(),
                anyList()
        );
        verify(naverDirectionsClient).fetchDrivingRoute(
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyDouble()
        );

        assertThat(routeWktCaptor.getValue()).isEqualTo("LINESTRING (126.978000 37.566500, 127.020000 37.530000, 127.100000 37.500000)");
        assertThat(response.searchMode()).isEqualTo(com.oilpricedbmanager.dto.StationSearchMode.ROUTE);
        assertThat(response.stations()).hasSize(1);
        assertThat(response.stations().get(0).distanceBasis()).isEqualTo(DistanceBasis.ROUTE_LINE);
        assertThat(response.stations().get(0).routeExtraDistanceMeters()).isEqualTo(1200);
        assertThat(response.route()).isNotNull();
        assertThat(response.route().distanceMeters()).isEqualTo(12345);
        assertThat(response.route().durationSeconds()).isEqualTo(678);
        assertThat(response.route().routeOption()).isEqualTo("traoptimal");
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
}
