package com.oilpricedbmanager.service;

import com.oilpricedbmanager.config.RecommendationProperties;
import com.oilpricedbmanager.domain.StationFuelSnapshot;
import com.oilpricedbmanager.dto.DistanceBasis;
import com.oilpricedbmanager.dto.RouteStationSearchRequest;
import com.oilpricedbmanager.dto.StationSearchResponse;
import com.oilpricedbmanager.dto.StationSearchSortOrder;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class StationSearchServiceTest {
    private final StationRepository stationRepository = mock(StationRepository.class);
    private final CostCalculationService costCalculationService = new CostCalculationService();
    private final RecommendationProperties properties = new RecommendationProperties(5000, 30, 10);
    private final StationSearchService service = new StationSearchService(
            stationRepository,
            costCalculationService,
            properties
    );

    @Test
    void searchRoute_builds_route_wkt_and_maps_result() {
        StationFuelSnapshot snapshot = new StationFuelSnapshot(
                "UNI-001",
                "B027",
                "테스트 주유소",
                "02-0000-0000",
                "서울특별시 중구",
                37.5665,
                126.9780,
                1730,
                1550,
                1490,
                null,
                1200,
                LocalDateTime.of(2026, 5, 28, 10, 0)
        );

        when(stationRepository.findNearbySnapshots(anyDouble(), anyDouble(), anyInt(), anyString()))
                .thenReturn(List.of(snapshot));

        RouteStationSearchRequest request = new RouteStationSearchRequest(
                37.5665,
                126.9780,
                37.5000,
                127.1000,
                "37.5665,126.9780;37.5300,127.0200;37.5000,127.1000",
                5000,
                null,
                30.0,
                10.0,
                List.of(),
                StationSearchSortOrder.DISTANCE_ASC,
                "출발지",
                "목적지"
        );

        StationSearchResponse response = service.searchRoute(request);

        ArgumentCaptor<String> routeWktCaptor = ArgumentCaptor.forClass(String.class);
        verify(stationRepository).findNearbySnapshots(
                anyDouble(),
                anyDouble(),
                anyInt(),
                routeWktCaptor.capture()
        );

        assertThat(routeWktCaptor.getValue()).isEqualTo("LINESTRING (126.978000 37.566500, 127.020000 37.530000, 127.100000 37.500000)");
        assertThat(response.searchMode()).isEqualTo(com.oilpricedbmanager.dto.StationSearchMode.ROUTE);
        assertThat(response.stations()).hasSize(1);
        assertThat(response.stations().get(0).distanceBasis()).isEqualTo(DistanceBasis.ROUTE_LINE);
        assertThat(response.stations().get(0).routeExtraDistanceMeters()).isEqualTo(1200);
    }

    @Test
    void getStationDetail_returns_full_fuel_snapshot() {
        StationFuelSnapshot snapshot = new StationFuelSnapshot(
                "UNI-002",
                "B034",
                "고급 주유소",
                "02-1111-2222",
                "서울특별시 서초구",
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
