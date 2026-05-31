package com.oilpricedbmanager.controller;

import com.oilpricedbmanager.domain.ForceSyncScope;
import com.oilpricedbmanager.dto.SyncResponse;
import com.oilpricedbmanager.dto.OpinetSectorSyncReport;
import com.oilpricedbmanager.service.OpinetSyncService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sync")
public class AdminSyncController {
    private final OpinetSyncService opinetSyncService;

    public AdminSyncController(OpinetSyncService opinetSyncService) {
        this.opinetSyncService = opinetSyncService;
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
        return opinetSyncService.syncDueSectors(limit);
    }

    @PostMapping("/sectors/force")
    public SyncResponse forceSync(@RequestParam ForceSyncScope scope) {
        return opinetSyncService.forceSync(scope);
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
