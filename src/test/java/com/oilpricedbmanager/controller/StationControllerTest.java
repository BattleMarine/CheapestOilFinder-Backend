package com.oilpricedbmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oilpricedbmanager.domain.FuelType;
import com.oilpricedbmanager.dto.DistanceBasis;
import com.oilpricedbmanager.dto.FuelPriceSummary;
import com.oilpricedbmanager.dto.RouteNavigationResponse;
import com.oilpricedbmanager.dto.RouteStationSearchRequest;
import com.oilpricedbmanager.dto.StationDetailResponse;
import com.oilpricedbmanager.dto.StationSearchItem;
import com.oilpricedbmanager.dto.StationSearchMode;
import com.oilpricedbmanager.dto.StationSearchResponse;
import com.oilpricedbmanager.dto.StationSearchSortOrder;
import com.oilpricedbmanager.service.StationSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StationController.class)
class StationControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    StationSearchService stationSearchService;

    @Test
    void nearby_accepts_new_parameter_contract() throws Exception {
        when(stationSearchService.searchNearby(any()))
                .thenReturn(sampleNearbyResponse());

        mockMvc.perform(get("/api/stations/nearby")
                        .param("latitude", "37.5665")
                        .param("longitude", "126.9780")
                        .param("radiusKm", "5")
                        .param("fuelAmountLiters", "30")
                        .param("fuelEfficiencyKmPerLiter", "10")
                        .param("fuelTypes", "REGULAR_GASOLINE")
                        .param("fuelTypes", "DIESEL")
                        .param("sortOrder", "ESTIMATED_TOTAL_COST_ASC")
                        .param("referenceLabel", "Current Location"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchMode").value("NEARBY"))
                .andExpect(jsonPath("$.resultCount").value(1))
                .andExpect(jsonPath("$.stations[0].stationId").value("UNI-001"));
    }

    @Test
    void nearby_legacy_parameter_aliases_still_work() throws Exception {
        when(stationSearchService.searchNearby(any()))
                .thenReturn(sampleNearbyResponse());

        mockMvc.perform(get("/api/stations/search/nearby")
                        .param("lat", "37.5665")
                        .param("lon", "126.9780")
                        .param("radiusMeters", "5000")
                        .param("fuelType", "REGULAR_GASOLINE")
                        .param("refuelLiters", "30")
                        .param("fuelEfficiencyKmPerLiter", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchMode").value("NEARBY"));
    }

    @Test
    void route_endpoint_is_available() throws Exception {
        when(stationSearchService.searchRoute(any()))
                .thenReturn(sampleRouteResponse());

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
                "Origin",
                "Destination"
        );

        mockMvc.perform(post("/api/stations/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchMode").value("ROUTE"))
                .andExpect(jsonPath("$.stations[0].distanceBasis").value("ROUTE_LINE"))
                .andExpect(jsonPath("$.route.routeOption").value("traoptimal"));
    }

    @Test
    void station_detail_endpoint_is_available() throws Exception {
        when(stationSearchService.getStationDetail("UNI-001"))
                .thenReturn(new StationDetailResponse("WGS84", sampleStationItem()));

        mockMvc.perform(get("/api/stations/UNI-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coordinateSystem").value("WGS84"))
                .andExpect(jsonPath("$.station.stationId").value("UNI-001"));
    }

    private StationSearchResponse sampleNearbyResponse() {
        return new StationSearchResponse(
                StationSearchMode.NEARBY,
                "WGS84",
                5.0,
                1,
                "Current Location",
                List.of(sampleStationItem()),
                null
        );
    }

    private StationSearchResponse sampleRouteResponse() {
        return new StationSearchResponse(
                StationSearchMode.ROUTE,
                "WGS84",
                5.0,
                1,
                "Origin -> Destination",
                List.of(new StationSearchItem(
                        "UNI-001",
                        "Test Station",
                        "B027",
                        "Seoul Jung-gu",
                        "02-1111-2222",
                        37.5665,
                        126.9780,
                        "WGS84",
                        1200,
                DistanceBasis.ROUTE_LINE,
                new FuelPriceSummary(1550, 1730, 1490, null, LocalDateTime.of(2026, 5, 28, 10, 0)),
                FuelType.REGULAR_GASOLINE,
                1550,
                1860,
                33600,
                1200,
                List.of("좌표계: WGS84", "거리 기준: ROUTE_LINE"),
                LocalDateTime.of(2026, 5, 28, 10, 0)
        )),
                new RouteNavigationResponse(
                        "37.566500,126.978000;37.530000,127.020000;37.500000,127.100000",
                        12345,
                        678,
                        2000,
                        1500,
                        "traoptimal",
                        "2026-05-28T10:00:00"
                )
        );
    }

    private StationSearchItem sampleStationItem() {
        return new StationSearchItem(
                "UNI-001",
                "Test Station",
                "B027",
                "Seoul Jung-gu",
                "02-1111-2222",
                37.5665,
                126.9780,
                "WGS84",
                750,
                DistanceBasis.REFERENCE_POINT,
                new FuelPriceSummary(1550, 1730, 1490, null, LocalDateTime.of(2026, 5, 28, 10, 0)),
                FuelType.REGULAR_GASOLINE,
                1550,
                1163,
                47663,
                null,
                List.of("좌표계: WGS84", "거리 기준: REFERENCE_POINT"),
                LocalDateTime.of(2026, 5, 28, 10, 0)
        );
    }
}
