package com.oilpricedbmanager.service;

import com.oilpricedbmanager.domain.CoordinatePoint;
import com.oilpricedbmanager.domain.ForceSyncScope;
import com.oilpricedbmanager.domain.FuelType;
import com.oilpricedbmanager.domain.OpinetStationPrice;
import com.oilpricedbmanager.domain.SyncSector;
import com.oilpricedbmanager.domain.SyncRequestSource;
import com.oilpricedbmanager.dto.OpinetFuelSyncReport;
import com.oilpricedbmanager.dto.AdminSyncRuntimeStatus;
import com.oilpricedbmanager.dto.OpinetSectorSyncReport;
import com.oilpricedbmanager.dto.OpinetStationPreviewResponse;
import com.oilpricedbmanager.dto.SyncResponse;
import com.oilpricedbmanager.external.opinet.OpinetCallPauseStrategy;
import com.oilpricedbmanager.external.opinet.OpinetClient;
import com.oilpricedbmanager.external.opinet.OpinetXmlParser;
import com.oilpricedbmanager.repository.OpinetStationRepository;
import com.oilpricedbmanager.repository.SectorSyncLogRepository;
import com.oilpricedbmanager.repository.SyncLogRepository;
import com.oilpricedbmanager.repository.SyncSectorRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.HashMap;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;

@Service
public class OpinetSyncService {
    private static final List<FuelType> DEFAULT_FUEL_TYPES = List.of(
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
    private final OpinetCallPauseStrategy callPauseStrategy;
    @Value("${opinet.call-interval-millis:30000}")
    private long callIntervalMillis = 30000L;

    private final Object batchQueueMonitor = new Object();
    private final Map<String, BatchSyncTask> batchTaskByKey = new HashMap<>();
    private final PriorityQueue<BatchSyncTask> batchQueue = new PriorityQueue<>(
            Comparator.comparingInt(BatchSyncTask::priority)
                    .thenComparingLong(BatchSyncTask::sequence)
    );
    private long batchSequence = 0L;
    private long nextAllowedCallAtMillis = 0L;
    private BatchSyncTask activeTask = null;
    private boolean drainingBatchQueue = false;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter STATUS_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(KST);

    public OpinetSyncService(
            OpinetClient opinetClient,
            OpinetXmlParser opinetXmlParser,
            CoordinateService coordinateService,
            OpinetStationRepository opinetStationRepository,
            SyncSectorRepository syncSectorRepository,
            SectorSyncLogRepository sectorSyncLogRepository,
            SyncLogRepository syncLogRepository,
            OpinetCallPauseStrategy callPauseStrategy
    ) {
        this.opinetClient = opinetClient;
        this.opinetXmlParser = opinetXmlParser;
        this.coordinateService = coordinateService;
        this.opinetStationRepository = opinetStationRepository;
        this.syncSectorRepository = syncSectorRepository;
        this.sectorSyncLogRepository = sectorSyncLogRepository;
        this.syncLogRepository = syncLogRepository;
        this.callPauseStrategy = callPauseStrategy;
    }

    public SyncResponse syncDueSectors(int sectorLimit, SyncRequestSource source) {
        SyncRequestSource effectiveSource = source == null ? SyncRequestSource.MANUAL : source;
        return submitQueuedBatch(
                "DUE_SECTORS",
                effectiveSource,
                buildRequestKey("DUE_SECTORS", effectiveSource, "limit=" + sectorLimit),
                "자동 동기화",
                () -> syncSectorRepository.findAutoDueSectors(sectorLimit),
                DEFAULT_FUEL_TYPES,
                false
        );
    }

    public SyncResponse forceSync(ForceSyncScope scope, List<FuelType> fuelTypes, SyncRequestSource source) {
        SyncRequestSource effectiveSource = source == null ? SyncRequestSource.MANUAL : source;
        List<FuelType> normalizedFuelTypes = normalizeFuelTypes(fuelTypes);
        return submitQueuedBatch(
                scope.syncType(),
                effectiveSource,
                buildRequestKey(scope.syncType(), effectiveSource, scope.name() + "|" + canonicalFuelTypes(normalizedFuelTypes)),
                scope.displayName(),
                () -> syncSectorRepository.findEnabledSectorsByTiers(scope.tiers()),
                normalizedFuelTypes,
                false
        );
    }

    public AdminSyncRuntimeStatus getRuntimeStatus() {
        synchronized (batchQueueMonitor) {
            BatchSyncTask current = activeTask;
            if (current != null && !current.isCompleted()) {
                return current.snapshot("RUNNING", batchTaskByKey.size(), batchQueue.size(), System.currentTimeMillis());
            }
            BatchSyncTask waiting = batchQueue.peek();
            if (waiting != null) {
                return waiting.snapshot("WAITING", batchTaskByKey.size(), batchQueue.size(), System.currentTimeMillis());
            }
            return AdminSyncRuntimeStatus.idle(batchTaskByKey.size(), batchQueue.size());
        }
    }

    public SyncResponse syncSector(long sectorId) {
        return runSync("SECTOR_" + sectorId, () -> {
            SyncSector sector = syncSectorRepository.findById(sectorId)
                    .orElseThrow(() -> new IllegalArgumentException("Sector not found: " + sectorId));
            BatchSyncResult result = processBatch("섹터 " + sector.sectorCode(), List.of(sector), DEFAULT_FUEL_TYPES, false);
            if (!result.success()) {
                throw new IllegalStateException(result.summary());
            }
            return "Synced sector=" + sector.sectorCode() + ", calls=" + result.callCount() + ", stationRows=" + result.stationCount();
        });
    }

    public OpinetSectorSyncReport syncSectorWithReport(long sectorId) {
        SyncSector sector = syncSectorRepository.findById(sectorId)
                .orElseThrow(() -> new IllegalArgumentException("Sector not found: " + sectorId));
        BatchSyncResult result = processBatch("섹터 " + sector.sectorCode(), List.of(sector), DEFAULT_FUEL_TYPES, true);
        return new OpinetSectorSyncReport(
                sector.sectorId(),
                sector.sectorCode(),
                sector.centerLat(),
                sector.centerLon(),
                sector.katecX(),
                sector.katecY(),
                sector.radiusMeters(),
                result.success(),
                result.stationCount(),
                result.reports()
        );
    }

    @Scheduled(cron = "0 20 1 * * *", zone = "Asia/Seoul")
    public void scheduledAutoSectorSync() {
        syncDueSectors(100, SyncRequestSource.AUTO);
    }

    private String buildRequestKey(String syncType, SyncRequestSource source, String requestSignature) {
        return syncType + "|" + source.name() + "|" + requestSignature;
    }

    private String canonicalFuelTypes(List<FuelType> fuelTypes) {
        return String.join(",", fuelTypes.stream().map(Enum::name).toList());
    }

    private long nextBatchSequence() {
        synchronized (batchQueueMonitor) {
            return batchSequence++;
        }
    }

    private SyncResponse submitQueuedBatch(
            String syncType,
            SyncRequestSource source,
            String requestKey,
            String label,
            Supplier<List<SyncSector>> sectorSupplier,
            List<FuelType> fuelTypes,
            boolean collectReports
    ) {
        BatchSyncTask task = new BatchSyncTask(
                requestKey,
                syncType,
                source,
                label,
                sectorSupplier,
                fuelTypes,
                collectReports,
                nextBatchSequence()
        );

        BatchSyncTask existingTask = null;
        boolean shouldDrain = false;
        synchronized (batchQueueMonitor) {
            existingTask = batchTaskByKey.get(requestKey);
            if (existingTask == null) {
                batchTaskByKey.put(requestKey, task);
                batchQueue.add(task);
                if (!drainingBatchQueue) {
                    drainingBatchQueue = true;
                    shouldDrain = true;
                }
                batchQueueMonitor.notifyAll();
            }
        }

        if (existingTask != null) {
            return existingTask.awaitResponse();
        }
        if (shouldDrain) {
            drainBatchQueue();
        }
        return task.awaitResponse();
    }

    private void drainBatchQueue() {
        try {
            while (true) {
                long waitMillis;
                synchronized (batchQueueMonitor) {
                    if (batchTaskByKey.isEmpty()) {
                        drainingBatchQueue = false;
                        batchQueueMonitor.notifyAll();
                        return;
                    }
                    waitMillis = Math.max(0L, nextAllowedCallAtMillis - System.currentTimeMillis());
                }
                if (waitMillis > 0L) {
                    callPauseStrategy.pause(waitMillis);
                }

                BatchSyncTask task;
                synchronized (batchQueueMonitor) {
                    task = batchQueue.poll();
                    if (task == null) {
                        drainingBatchQueue = false;
                        batchQueueMonitor.notifyAll();
                        return;
                    }
                    activeTask = task;
                }

                try {
                    task.executeOneCall();
                } catch (Exception exception) {
                    task.failUnexpected(exception);
                }

                synchronized (batchQueueMonitor) {
                    if (task.isCompleted()) {
                        batchTaskByKey.remove(task.requestKey());
                        if (activeTask == task) {
                            activeTask = null;
                        }
                    } else {
                        batchQueue.add(task);
                    }
                    nextAllowedCallAtMillis = System.currentTimeMillis() + callIntervalMillis;
                    batchQueueMonitor.notifyAll();
                }
            }
        } finally {
            synchronized (batchQueueMonitor) {
                drainingBatchQueue = false;
                batchQueueMonitor.notifyAll();
            }
        }
    }

    private final class BatchSyncTask {
        private final String requestKey;
        private final String syncType;
        private final SyncRequestSource source;
        private final String label;
        private final Supplier<List<SyncSector>> sectorSupplier;
        private final List<FuelType> fuelTypes;
        private final boolean collectReports;
        private final long sequence;
        private final long requestedAtMillis = System.currentTimeMillis();
        private final CompletableFuture<SyncResponse> future = new CompletableFuture<>();
        private final List<OpinetFuelSyncReport> reports = new ArrayList<>();
        private final Map<Long, SectorProgress> progressBySectorId = new LinkedHashMap<>();
        private final ArrayDeque<PendingFuelCall> retryQueue = new ArrayDeque<>();
        private List<SyncSector> sectors = List.of();
        private boolean started;
        private boolean completed;
        private long startedAtMillis = -1L;
        private long finishedAtMillis = -1L;
        private long syncLogId;
        private int sectorIndex;
        private int fuelIndex;
        private int sectorCount;
        private int callCount;
        private int retryCount;
        private int retryFailureCount;
        private int stationCount;

        private BatchSyncTask(
                String requestKey,
                String syncType,
                SyncRequestSource source,
                String label,
                Supplier<List<SyncSector>> sectorSupplier,
                List<FuelType> fuelTypes,
                boolean collectReports,
                long sequence
        ) {
            this.requestKey = requestKey;
            this.syncType = syncType;
            this.source = source;
            this.label = label;
            this.sectorSupplier = sectorSupplier;
            this.fuelTypes = List.copyOf(fuelTypes);
            this.collectReports = collectReports;
            this.sequence = sequence;
        }

        private String requestKey() {
            return requestKey;
        }

        private int priority() {
            return source.priority();
        }

        private long sequence() {
            return sequence;
        }

        private SyncResponse awaitResponse() {
            return future.join();
        }

        private void startIfNeeded() {
            if (started) {
                return;
            }
            started = true;
            startedAtMillis = System.currentTimeMillis();
            syncLogId = syncLogRepository.start(syncType);
            try {
                sectors = List.copyOf(sectorSupplier.get());
            } catch (Exception exception) {
                failUnexpected(exception);
                return;
            }
            for (SyncSector sector : sectors) {
                progressBySectorId.put(sector.sectorId(), new SectorProgress(sector, fuelTypes.size()));
            }
            if (sectors.isEmpty()) {
                complete();
            }
        }

        private void executeOneCall() {
            startIfNeeded();
            if (completed) {
                return;
            }

            if (sectorIndex < sectors.size()) {
                SyncSector sector = sectors.get(sectorIndex);
                FuelType fuelType = fuelTypes.get(fuelIndex);
                OpinetFuelSyncReport report = syncSectorFuelWithReport(sector, fuelType);
                appendReport(report);
                callCount++;
                stationCount += report.stationCount();
                SectorProgress progress = progressBySectorId.get(sector.sectorId());
                if (report.success()) {
                    progress.successfulFuelCalls++;
                    if (progress.isComplete() && !progress.synced) {
                        syncSectorRepository.markSynced(sector.sectorId(), sector.syncTier());
                        progress.synced = true;
                    }
                } else {
                    retryQueue.addLast(new PendingFuelCall(sector, fuelType));
                }

                fuelIndex++;
                if (fuelIndex >= fuelTypes.size()) {
                    sectorCount++;
                    fuelIndex = 0;
                    sectorIndex++;
                }
            } else if (!retryQueue.isEmpty()) {
                PendingFuelCall retryCall = retryQueue.removeFirst();
                OpinetFuelSyncReport report = syncSectorFuelWithReport(retryCall.sector(), retryCall.fuelType());
                appendReport(report);
                callCount++;
                retryCount++;
                stationCount += report.stationCount();
                SectorProgress progress = progressBySectorId.get(retryCall.sector().sectorId());
                if (report.success()) {
                    progress.successfulFuelCalls++;
                    if (progress.isComplete() && !progress.synced) {
                        syncSectorRepository.markSynced(retryCall.sector().sectorId(), retryCall.sector().syncTier());
                        progress.synced = true;
                    }
                } else {
                    retryFailureCount++;
                }
            }

            if (sectorIndex >= sectors.size() && retryQueue.isEmpty()) {
                complete();
            }
        }

        private void appendReport(OpinetFuelSyncReport report) {
            if (collectReports) {
                reports.add(report);
            }
        }

        private void failUnexpected(Exception exception) {
            if (completed) {
                return;
            }
            completed = true;
            finishedAtMillis = System.currentTimeMillis();
            String message = exception.getMessage();
            syncLogRepository.finish(syncLogId, "FAILED", message);
            future.complete(new SyncResponse(syncType, "FAILED", message));
        }

        private void complete() {
            if (completed) {
                return;
            }
            completed = true;
            finishedAtMillis = System.currentTimeMillis();
            String summary = summary();
            String status = retryFailureCount == 0 ? "SUCCESS" : "FAILED";
            syncLogRepository.finish(syncLogId, status, summary);
            future.complete(new SyncResponse(syncType, status, summary));
        }

        private boolean isCompleted() {
            return completed;
        }

        private AdminSyncRuntimeStatus snapshot(String state, int queueSize, int waitingCount, long nowMillis) {
            long elapsedSeconds = Math.max(0L, (nowMillis - requestedAtMillis) / 1000L);
            String startedAt = startedAtMillis > 0L ? formatStatusTime(startedAtMillis) : null;
            String requestedAt = formatStatusTime(requestedAtMillis);
            String detail = started ? summary() : "대기 중";
            if (completed) {
                detail = summary();
            }
            return new AdminSyncRuntimeStatus(
                    state,
                    label,
                    syncType,
                    source.name(),
                    requestedAt,
                    startedAt,
                    elapsedSeconds,
                    formatElapsed(elapsedSeconds),
                    queueSize,
                    waitingCount,
                    detail
            );
        }

        private String summary() {
            return label + " sectors=" + sectorCount
                    + ", calls=" + callCount
                    + ", retries=" + retryCount
                    + ", retryFailures=" + retryFailureCount
                    + ", stationRows=" + stationCount;
        }
    }

    private static String formatStatusTime(long epochMillis) {
        return STATUS_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    private static String formatElapsed(long totalSeconds) {
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format("%d시간 %02d분 %02d초", hours, minutes, seconds);
        }
        if (minutes > 0L) {
            return String.format("%d분 %02d초", minutes, seconds);
        }
        return String.format("%d초", seconds);
    }

    private BatchSyncResult processBatch(String label, List<SyncSector> sectors, boolean collectReports) {
        return processBatch(label, sectors, DEFAULT_FUEL_TYPES, collectReports);
    }

    private BatchSyncResult processBatch(String label, List<SyncSector> sectors, List<FuelType> fuelTypes, boolean collectReports) {
        Map<Long, SectorProgress> progressBySectorId = new LinkedHashMap<>();
        List<PendingFuelCall> retryQueue = new ArrayList<>();
        List<OpinetFuelSyncReport> reports = collectReports ? new ArrayList<>() : List.of();
        int sectorCount = 0;
        int callCount = 0;
        int stationCount = 0;

        for (SyncSector sector : sectors) {
            SectorProgress progress = new SectorProgress(sector, fuelTypes.size());
            progressBySectorId.put(sector.sectorId(), progress);

            for (int fuelIndex = 0; fuelIndex < fuelTypes.size(); fuelIndex++) {
                FuelType fuelType = fuelTypes.get(fuelIndex);
                OpinetFuelSyncReport report = syncSectorFuelWithReport(sector, fuelType);
                if (collectReports) {
                    reports.add(report);
                }
                callCount++;
                stationCount += report.stationCount();
                if (report.success()) {
                    progress.successfulFuelCalls++;
                } else {
                    retryQueue.add(new PendingFuelCall(sector, fuelType));
                }
                if (fuelIndex < fuelTypes.size() - 1 || !retryQueue.isEmpty() || hasMoreSectors(sectors, sector, sectorCount)) {
                    pauseAfterCall();
                }
            }

            sectorCount++;
            if (progress.isComplete() && !progress.synced) {
                syncSectorRepository.markSynced(sector.sectorId(), sector.syncTier());
                progress.synced = true;
            }
        }

        int retryCount = 0;
        int retryFailureCount = 0;
        if (!retryQueue.isEmpty()) {
            for (int retryIndex = 0; retryIndex < retryQueue.size(); retryIndex++) {
                PendingFuelCall retryCall = retryQueue.get(retryIndex);
                OpinetFuelSyncReport report = syncSectorFuelWithReport(retryCall.sector(), retryCall.fuelType());
                if (collectReports) {
                    reports.add(report);
                }
                callCount++;
                retryCount++;
                stationCount += report.stationCount();
                SectorProgress progress = progressBySectorId.get(retryCall.sector().sectorId());
                if (report.success()) {
                    progress.successfulFuelCalls++;
                    if (progress.isComplete() && !progress.synced) {
                        syncSectorRepository.markSynced(retryCall.sector().sectorId(), retryCall.sector().syncTier());
                        progress.synced = true;
                    }
                } else {
                    retryFailureCount++;
                }
                if (retryIndex < retryQueue.size() - 1) {
                    pauseAfterCall();
                }
            }
        }

        boolean success = retryFailureCount == 0;
        return new BatchSyncResult(label, sectorCount, callCount, retryCount, retryFailureCount, stationCount, success, reports);
    }

    private boolean hasMoreSectors(List<SyncSector> sectors, SyncSector currentSector, int sectorCount) {
        return sectorCount < sectors.size() - 1 || currentSector != sectors.get(sectors.size() - 1);
    }

    private List<FuelType> normalizeFuelTypes(List<FuelType> fuelTypes) {
        if (fuelTypes == null || fuelTypes.isEmpty()) {
            return DEFAULT_FUEL_TYPES;
        }
        LinkedHashSet<FuelType> normalized = new LinkedHashSet<>();
        for (FuelType fuelType : DEFAULT_FUEL_TYPES) {
            if (fuelTypes.contains(fuelType)) {
                normalized.add(fuelType);
            }
        }
        for (FuelType fuelType : fuelTypes) {
            normalized.add(fuelType);
        }
        return List.copyOf(normalized);
    }

    private void pauseAfterCall() {
        callPauseStrategy.pause();
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

    private static final class SectorProgress {
        private final SyncSector sector;
        private final int requiredFuelCalls;
        private int successfulFuelCalls;
        private boolean synced;

        private SectorProgress(SyncSector sector, int requiredFuelCalls) {
            this.sector = sector;
            this.requiredFuelCalls = requiredFuelCalls;
        }

        private boolean isComplete() {
            return successfulFuelCalls >= requiredFuelCalls;
        }
    }

    private record PendingFuelCall(SyncSector sector, FuelType fuelType) {
    }

    private record BatchSyncResult(
            String label,
            int sectorCount,
            int callCount,
            int retryCount,
            int retryFailureCount,
            int stationCount,
            boolean success,
            List<OpinetFuelSyncReport> reports
    ) {
        private String summary() {
            return label + " sectors=" + sectorCount
                    + ", calls=" + callCount
                    + ", retries=" + retryCount
                    + ", retryFailures=" + retryFailureCount
                    + ", stationRows=" + stationCount;
        }
    }

    @FunctionalInterface
    private interface SyncWork {
        String run();
    }
}
