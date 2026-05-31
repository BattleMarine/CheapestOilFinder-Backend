package com.oilpricedbmanager.service;

import com.oilpricedbmanager.domain.CoordinatePoint;
import com.oilpricedbmanager.domain.ForceSyncScope;
import com.oilpricedbmanager.domain.FuelType;
import com.oilpricedbmanager.domain.OpinetStationPrice;
import com.oilpricedbmanager.domain.SyncSector;
import com.oilpricedbmanager.dto.OpinetFuelSyncReport;
import com.oilpricedbmanager.dto.OpinetSectorSyncReport;
import com.oilpricedbmanager.dto.OpinetStationPreviewResponse;
import com.oilpricedbmanager.dto.SyncResponse;
import com.oilpricedbmanager.external.opinet.OpinetClient;
import com.oilpricedbmanager.external.opinet.OpinetXmlParser;
import com.oilpricedbmanager.repository.OpinetStationRepository;
import com.oilpricedbmanager.repository.SectorSyncLogRepository;
import com.oilpricedbmanager.repository.SyncLogRepository;
import com.oilpricedbmanager.repository.SyncSectorRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpinetSyncService {
    private static final List<FuelType> MVP_FUEL_TYPES = List.of(
            FuelType.REGULAR_GASOLINE,
            FuelType.PREMIUM_GASOLINE,
            FuelType.DIESEL
    );

    private final OpinetClient opinetClient;
    private final OpinetXmlParser opinetXmlParser;
    private final CoordinateService coordinateService;
    private final OpinetStationRepository opinetStationRepository;
    private final SyncSectorRepository syncSectorRepository;
    private final SectorSyncLogRepository sectorSyncLogRepository;
    private final SyncLogRepository syncLogRepository;

    public OpinetSyncService(
            OpinetClient opinetClient,
            OpinetXmlParser opinetXmlParser,
            CoordinateService coordinateService,
            OpinetStationRepository opinetStationRepository,
            SyncSectorRepository syncSectorRepository,
            SectorSyncLogRepository sectorSyncLogRepository,
            SyncLogRepository syncLogRepository
    ) {
        this.opinetClient = opinetClient;
        this.opinetXmlParser = opinetXmlParser;
        this.coordinateService = coordinateService;
        this.opinetStationRepository = opinetStationRepository;
        this.syncSectorRepository = syncSectorRepository;
        this.sectorSyncLogRepository = sectorSyncLogRepository;
        this.syncLogRepository = syncLogRepository;
    }

    public SyncResponse syncStations() {
        return syncDueSectors(10);
    }

    public SyncResponse syncFuels() {
        return syncDueSectors(10);
    }

    public SyncResponse syncDueSectors(int sectorLimit) {
        return runSync("DUE_SECTORS", () -> {
            List<SyncSector> sectors = syncSectorRepository.findAutoDueSectors(sectorLimit);
            return syncSectorBatch("자동 동기화", sectors);
        });
    }

    public SyncResponse forceSync(ForceSyncScope scope) {
        return runSync(scope.syncType(), () -> {
            List<SyncSector> sectors = syncSectorRepository.findEnabledSectorsByTiers(scope.tiers());
            return syncSectorBatch(scope.displayName(), sectors);
        });
    }

    public SyncResponse syncSector(long sectorId) {
        return runSync("SECTOR_" + sectorId, () -> {
            SyncSector sector = syncSectorRepository.findById(sectorId)
                    .orElseThrow(() -> new IllegalArgumentException("Sector not found: " + sectorId));
            int stationCount = 0;
            for (FuelType fuelType : MVP_FUEL_TYPES) {
                stationCount += syncSectorFuel(sector, fuelType);
            }
            syncSectorRepository.markSynced(sector.sectorId(), sector.syncTier());
            return "Synced sector=" + sector.sectorCode() + ", calls=" + MVP_FUEL_TYPES.size() + ", stationRows=" + stationCount;
        });
    }

    public OpinetSectorSyncReport syncSectorWithReport(long sectorId) {
        SyncSector sector = syncSectorRepository.findById(sectorId)
                .orElseThrow(() -> new IllegalArgumentException("Sector not found: " + sectorId));
        List<OpinetFuelSyncReport> calls = new java.util.ArrayList<>();
        int stationCount = 0;
        boolean success = true;
        for (FuelType fuelType : MVP_FUEL_TYPES) {
            OpinetFuelSyncReport report = syncSectorFuelWithReport(sector, fuelType);
            calls.add(report);
            stationCount += report.stationCount();
            if (!report.success()) {
                success = false;
            }
        }
        if (success) {
            syncSectorRepository.markSynced(sector.sectorId(), sector.syncTier());
        }
        return new OpinetSectorSyncReport(
                sector.sectorId(),
                sector.sectorCode(),
                sector.centerLat(),
                sector.centerLon(),
                sector.katecX(),
                sector.katecY(),
                sector.radiusMeters(),
                success,
                stationCount,
                calls
        );
    }

    @Scheduled(cron = "0 20 1 * * *", zone = "Asia/Seoul")
    public void scheduledAutoSectorSync() {
        syncDueSectors(100);
    }

    private int syncSectorFuel(SyncSector sector, FuelType fuelType) {
        OpinetFuelSyncReport report = syncSectorFuelWithReport(sector, fuelType);
        if (!report.success()) {
            throw new IllegalStateException(report.errorMessage());
        }
        return report.stationCount();
    }

    private String syncSectorBatch(String label, List<SyncSector> sectors) {
        int sectorCount = 0;
        int callCount = 0;
        int stationCount = 0;
        for (SyncSector sector : sectors) {
            for (FuelType fuelType : MVP_FUEL_TYPES) {
                stationCount += syncSectorFuel(sector, fuelType);
                callCount++;
            }
            syncSectorRepository.markSynced(sector.sectorId(), sector.syncTier());
            sectorCount++;
        }
        return label + " sectors=" + sectorCount + ", calls=" + callCount + ", stationRows=" + stationCount;
    }

    private OpinetFuelSyncReport syncSectorFuelWithReport(SyncSector sector, FuelType fuelType) {
        long logId = sectorSyncLogRepository.start(sector.sectorId(), fuelType);
        String xml = null;
        try {
            xml = opinetClient.fetchAroundAllXml(
                    sector.katecX(),
                    sector.katecY(),
                    sector.radiusMeters(),
                    fuelType,
                    1
            );
            List<OpinetStationPrice> prices = opinetXmlParser.parseAroundAll(xml);
            for (OpinetStationPrice price : prices) {
                CoordinatePoint coordinatePoint = coordinateService.toWgs84(price.katecX(), price.katecY());
                opinetStationRepository.upsertStationAndFuel(price, fuelType, coordinatePoint);
            }
            sectorSyncLogRepository.finish(logId, "SUCCESS", prices.size(), null);
            return new OpinetFuelSyncReport(
                    fuelType,
                    fuelType.opinetProductCode(),
                    sector.katecX(),
                    sector.katecY(),
                    sector.radiusMeters(),
                    1,
                    true,
                    prices.size(),
                    prices.stream()
                            .limit(5)
                            .map(price -> new OpinetStationPreviewResponse(
                                    price.uniId(),
                                    price.stationName(),
                                    price.pollDivCd(),
                                    price.price(),
                                    price.katecX(),
                                    price.katecY()
                            ))
                            .toList(),
                    null,
                    null,
                    null
            );
        } catch (Exception exception) {
            String responsePreview = xml == null ? null : compactPreview(xml);
            String errorMessage = responsePreview == null
                    ? exception.getMessage()
                    : exception.getMessage() + " | responsePreview=" + responsePreview;
            sectorSyncLogRepository.finish(logId, "FAILED", 0, errorMessage);
            return new OpinetFuelSyncReport(
                    fuelType,
                    fuelType.opinetProductCode(),
                    sector.katecX(),
                    sector.katecY(),
                    sector.radiusMeters(),
                    1,
                    false,
                    0,
                    List.of(),
                    errorMessage,
                    responsePreview,
                    xml == null ? null : xml.length()
            );
        }
    }

    private static String compactPreview(String xml) {
        String compact = xml.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 300) {
            return compact;
        }
        return compact.substring(0, 300) + "...";
    }

    private SyncResponse runSync(String syncType, SyncWork work) {
        long syncId = syncLogRepository.start(syncType);
        try {
            String message = work.run();
            syncLogRepository.finish(syncId, "SUCCESS", message);
            return new SyncResponse(syncType, "SUCCESS", message);
        } catch (Exception exception) {
            String message = exception.getMessage();
            syncLogRepository.finish(syncId, "FAILED", message);
            return new SyncResponse(syncType, "FAILED", message);
        }
    }

    @FunctionalInterface
    private interface SyncWork {
        String run();
    }
}
