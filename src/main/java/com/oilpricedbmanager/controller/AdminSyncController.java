package com.oilpricedbmanager.controller;

import com.oilpricedbmanager.domain.ForceSyncScope;
import com.oilpricedbmanager.domain.FuelType;
import com.oilpricedbmanager.domain.SyncRequestSource;
import com.oilpricedbmanager.dto.AdminSyncLogItem;
import com.oilpricedbmanager.dto.AdminSyncRuntimeStatus;
import com.oilpricedbmanager.repository.AdminSyncLogRepository;
import com.oilpricedbmanager.dto.SyncResponse;
import com.oilpricedbmanager.dto.OpinetSectorSyncReport;
import com.oilpricedbmanager.service.OpinetSyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/sync")
public class AdminSyncController {
    private final OpinetSyncService opinetSyncService;
    private final AdminSyncLogRepository adminSyncLogRepository;

    public AdminSyncController(OpinetSyncService opinetSyncService, AdminSyncLogRepository adminSyncLogRepository) {
        this.opinetSyncService = opinetSyncService;
        this.adminSyncLogRepository = adminSyncLogRepository;
    }

    @PostMapping("/stations")
    public SyncResponse syncStations() {
        return opinetSyncService.syncStations();
    }

    @PostMapping("/fuels")
    public SyncResponse syncFuels() {
        return opinetSyncService.syncFuels();
    }

    @PostMapping("/sectors/due")
    public SyncResponse syncDueSectors(@RequestParam(defaultValue = "10") int limit) {
        return opinetSyncService.syncDueSectors(limit, SyncRequestSource.MANUAL);
    }

    @PostMapping("/sectors/force")
    public SyncResponse forceSync(
            @RequestParam ForceSyncScope scope,
            @RequestParam(required = false) List<FuelType> fuelTypes,
            @RequestParam(defaultValue = "MANUAL") SyncRequestSource source
    ) {
        return opinetSyncService.forceSync(scope, fuelTypes, source);
    }

    @GetMapping("/logs/recent")
    public List<AdminSyncLogItem> recentSyncLogs(@RequestParam(defaultValue = "12") int limit) {
        return adminSyncLogRepository.findRecent(limit);
    }

    @GetMapping("/status")
    public AdminSyncRuntimeStatus syncStatus() {
        return opinetSyncService.getRuntimeStatus();
    }

    @PostMapping("/sectors/{sectorId}")
    public SyncResponse syncSector(@PathVariable long sectorId) {
        return opinetSyncService.syncSector(sectorId);
    }

    @PostMapping("/sectors/{sectorId}/report")
    public OpinetSectorSyncReport syncSectorWithReport(@PathVariable long sectorId) {
        return opinetSyncService.syncSectorWithReport(sectorId);
    }
}
