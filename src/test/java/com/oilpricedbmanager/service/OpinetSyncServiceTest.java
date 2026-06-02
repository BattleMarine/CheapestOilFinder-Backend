package com.oilpricedbmanager.service;

import com.oilpricedbmanager.domain.CoordinatePoint;
import com.oilpricedbmanager.domain.ForceSyncScope;
import com.oilpricedbmanager.domain.FuelType;
import com.oilpricedbmanager.domain.OpinetStationPrice;
import com.oilpricedbmanager.domain.SyncSector;
import com.oilpricedbmanager.domain.SyncRequestSource;
import com.oilpricedbmanager.domain.SyncTier;
import com.oilpricedbmanager.dto.OpinetSectorSyncReport;
import com.oilpricedbmanager.dto.SyncResponse;
import com.oilpricedbmanager.external.opinet.OpinetCallPauseStrategy;
import com.oilpricedbmanager.external.opinet.OpinetClient;
import com.oilpricedbmanager.external.opinet.OpinetXmlParser;
import com.oilpricedbmanager.repository.OpinetStationRepository;
import com.oilpricedbmanager.repository.SectorSyncLogRepository;
import com.oilpricedbmanager.repository.SyncLogRepository;
import com.oilpricedbmanager.repository.SyncSectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpinetSyncServiceTest {
    @Mock
    OpinetClient opinetClient;

    @Mock
    OpinetXmlParser opinetXmlParser;

    @Mock
    CoordinateService coordinateService;

    @Mock
    OpinetStationRepository opinetStationRepository;

    @Mock
    SyncSectorRepository syncSectorRepository;

    @Mock
    SectorSyncLogRepository sectorSyncLogRepository;

    @Mock
    SyncLogRepository syncLogRepository;

    @Mock
    OpinetCallPauseStrategy callPauseStrategy;

    @InjectMocks
    OpinetSyncService opinetSyncService;

    private SyncSector sector;
    private final AtomicLong logIdSequence = new AtomicLong(1L);

    @BeforeEach
    void setUp() {
        sector = new SyncSector(
                1132L,
                "HEX5K-R0061-C0031",
                36.0,
                127.0,
                365164.6609066215,
                516105.7426332737,
                5000,
                5000,
                "MANUAL",
                "POLYGON((0 0,1 0,1 1,0 1,0 0))",
                SyncTier.WARM,
                true,
                600,
                LocalDateTime.now(),
                LocalDateTime.now(),
                "memo"
        );

        lenient().when(syncSectorRepository.findById(1132L)).thenReturn(java.util.Optional.of(sector));
        when(sectorSyncLogRepository.start(anyLong(), any())).thenAnswer(invocation -> logIdSequence.getAndIncrement());
        doNothing().when(sectorSyncLogRepository).finish(anyLong(), anyString(), anyInt(), any());
        doNothing().when(opinetStationRepository).upsertStationAndFuel(any(), any(), any());
        when(coordinateService.toWgs84(anyDouble(), anyDouble())).thenReturn(new CoordinatePoint(37.0, 127.0));
    }

    @Test
    void syncSectorWithReport_retries_failed_fuel_once_and_succeeds_without_third_call() {
        when(opinetClient.fetchAroundAllXml(anyDouble(), anyDouble(), anyInt(), any(), anyInt()))
                .thenReturn("REGULAR_XML", "PREMIUM_XML", "DIESEL_FAIL_XML", "DIESEL_RETRY_XML");
        when(opinetXmlParser.parseAroundAll("REGULAR_XML"))
                .thenReturn(List.of(price("REG-1", "B027", "Regular", 1000, 365165.0, 516106.0)));
        when(opinetXmlParser.parseAroundAll("PREMIUM_XML"))
                .thenReturn(List.of(price("PRE-1", "B034", "Premium", 1100, 365166.0, 516107.0)));
        when(opinetXmlParser.parseAroundAll("DIESEL_RETRY_XML"))
                .thenReturn(List.of(price("DSL-1", "D047", "Diesel", 1200, 365167.0, 516108.0)));
        when(opinetXmlParser.parseAroundAll("DIESEL_FAIL_XML"))
                .thenThrow(new IllegalArgumentException("blocked"));

        OpinetSectorSyncReport report = opinetSyncService.syncSectorWithReport(1132L);

        assertThat(report.success()).isTrue();
        assertThat(report.stationCount()).isEqualTo(3);
        assertThat(report.fuelCalls()).hasSize(4);
        verify(opinetClient, times(4)).fetchAroundAllXml(anyDouble(), anyDouble(), anyInt(), any(), anyInt());
        verify(syncSectorRepository).markSynced(1132L, SyncTier.WARM);
        verify(opinetStationRepository, times(3)).upsertStationAndFuel(any(), any(), any());
    }

    @Test
    void syncSectorWithReport_does_not_attempt_a_third_call_after_retry_failure() {
        when(opinetClient.fetchAroundAllXml(anyDouble(), anyDouble(), anyInt(), any(), anyInt()))
                .thenReturn("REGULAR_XML", "PREMIUM_XML", "DIESEL_FAIL_XML", "DIESEL_RETRY_FAIL_XML");
        when(opinetXmlParser.parseAroundAll("REGULAR_XML"))
                .thenReturn(List.of(price("REG-1", "B027", "Regular", 1000, 365165.0, 516106.0)));
        when(opinetXmlParser.parseAroundAll("PREMIUM_XML"))
                .thenReturn(List.of(price("PRE-1", "B034", "Premium", 1100, 365166.0, 516107.0)));
        when(opinetXmlParser.parseAroundAll("DIESEL_FAIL_XML"))
                .thenThrow(new IllegalArgumentException("blocked"));
        when(opinetXmlParser.parseAroundAll("DIESEL_RETRY_FAIL_XML"))
                .thenThrow(new IllegalArgumentException("blocked again"));

        OpinetSectorSyncReport report = opinetSyncService.syncSectorWithReport(1132L);

        assertThat(report.success()).isFalse();
        assertThat(report.stationCount()).isEqualTo(2);
        verify(opinetClient, times(4)).fetchAroundAllXml(anyDouble(), anyDouble(), anyInt(), any(), anyInt());
        verify(syncSectorRepository, never()).markSynced(anyLong(), any());
        verify(opinetStationRepository, times(2)).upsertStationAndFuel(any(), any(), any());
    }

    @Test
    void forceSync_priority_queue_switches_to_frontend_request_between_calls() throws Exception {
        SyncSector hotSector = testSector(2001L, "HOT-2001", SyncTier.HOT, 366000.0, 516200.0);
        SyncSector coldSector = testSector(3001L, "COLD-3001", SyncTier.COLD, 367000.0, 516300.0);
        when(syncSectorRepository.findEnabledSectorsByTiers(List.of(SyncTier.HOT))).thenReturn(List.of(hotSector));
        when(syncSectorRepository.findEnabledSectorsByTiers(List.of(SyncTier.COLD))).thenReturn(List.of(coldSector));

        List<String> callOrder = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch firstCallStarted = new CountDownLatch(1);
        CountDownLatch allowFirstCallToFinish = new CountDownLatch(1);

        when(opinetClient.fetchAroundAllXml(anyDouble(), anyDouble(), anyInt(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    double katecX = invocation.getArgument(0, Double.class);
                    FuelType fuelType = invocation.getArgument(3, FuelType.class);
                    String requestLabel = Double.compare(katecX, hotSector.katecX()) == 0 ? "MANUAL" : "FRONTEND";
                    callOrder.add(requestLabel + "-" + fuelType.name());
                    if (firstCallStarted.getCount() > 0) {
                        firstCallStarted.countDown();
                        if (!allowFirstCallToFinish.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting for the first call to finish");
                        }
                    }
                    return "XML-" + callOrder.size();
                });
        when(opinetXmlParser.parseAroundAll(anyString()))
                .thenReturn(List.of(price("P-1", "HDO", "Station", 1000, 366010.0, 516210.0)));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<SyncResponse> manualFuture = executor.submit(() ->
                    opinetSyncService.forceSync(
                            ForceSyncScope.HOT_ONLY,
                            List.of(FuelType.REGULAR_GASOLINE, FuelType.DIESEL),
                            SyncRequestSource.MANUAL
                    )
            );

            assertThat(firstCallStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<SyncResponse> frontendFuture = executor.submit(() ->
                    opinetSyncService.forceSync(
                            ForceSyncScope.COLD_ONLY,
                            List.of(FuelType.REGULAR_GASOLINE),
                            SyncRequestSource.FRONTEND
                    )
            );

            allowFirstCallToFinish.countDown();

            assertThat(manualFuture.get(5, TimeUnit.SECONDS).status()).isEqualTo("SUCCESS");
            assertThat(frontendFuture.get(5, TimeUnit.SECONDS).status()).isEqualTo("SUCCESS");
            assertThat(callOrder).containsExactly(
                    "MANUAL-REGULAR_GASOLINE",
                    "FRONTEND-REGULAR_GASOLINE",
                    "MANUAL-DIESEL"
            );
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void forceSync_ignores_duplicate_requests_with_same_source_and_parameters() throws Exception {
        SyncSector hotSector = testSector(2002L, "HOT-2002", SyncTier.HOT, 368000.0, 516400.0);
        when(syncSectorRepository.findEnabledSectorsByTiers(List.of(SyncTier.HOT))).thenReturn(List.of(hotSector));

        CountDownLatch firstCallStarted = new CountDownLatch(1);
        CountDownLatch allowFirstCallToFinish = new CountDownLatch(1);

        when(opinetClient.fetchAroundAllXml(anyDouble(), anyDouble(), anyInt(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    firstCallStarted.countDown();
                    if (!allowFirstCallToFinish.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for the first call to finish");
                    }
                    return "XML";
                });
        when(opinetXmlParser.parseAroundAll(anyString()))
                .thenReturn(List.of(price("P-2", "HDO", "Station", 1000, 368010.0, 516410.0)));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<SyncResponse> firstFuture = executor.submit(() ->
                    opinetSyncService.forceSync(
                            ForceSyncScope.HOT_ONLY,
                            List.of(FuelType.REGULAR_GASOLINE),
                            SyncRequestSource.MANUAL
                    )
            );

            assertThat(firstCallStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<SyncResponse> duplicateFuture = executor.submit(() ->
                    opinetSyncService.forceSync(
                            ForceSyncScope.HOT_ONLY,
                            List.of(FuelType.REGULAR_GASOLINE),
                            SyncRequestSource.MANUAL
                    )
            );

            allowFirstCallToFinish.countDown();

            assertThat(firstFuture.get(5, TimeUnit.SECONDS).status()).isEqualTo("SUCCESS");
            assertThat(duplicateFuture.get(5, TimeUnit.SECONDS).status()).isEqualTo("SUCCESS");
            verify(opinetClient, times(1)).fetchAroundAllXml(anyDouble(), anyDouble(), anyInt(), any(), anyInt());
        } finally {
            executor.shutdownNow();
        }
    }

    private static OpinetStationPrice price(String uniId, String pollDivCd, String stationName, int price, double x, double y) {
        return new OpinetStationPrice(uniId, pollDivCd, stationName, price, 10, x, y);
    }

    private static SyncSector testSector(long sectorId, String sectorCode, SyncTier syncTier, double katecX, double katecY) {
        return new SyncSector(
                sectorId,
                sectorCode,
                36.0,
                127.0,
                katecX,
                katecY,
                5000,
                5000,
                "MANUAL",
                "POLYGON((0 0,1 0,1 1,0 1,0 0))",
                syncTier,
                true,
                600,
                LocalDateTime.now(),
                LocalDateTime.now(),
                "memo"
        );
    }
}
