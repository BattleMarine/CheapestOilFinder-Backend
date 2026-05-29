package com.oilpricedbmanager.controller;

import com.oilpricedbmanager.domain.SyncSector;
import com.oilpricedbmanager.domain.SyncTier;
import com.oilpricedbmanager.dto.BulkSectorToggleResponse;
import com.oilpricedbmanager.dto.HexGridGenerationResponse;
import com.oilpricedbmanager.dto.SectorPointResponse;
import com.oilpricedbmanager.dto.SyncSectorResponse;
import com.oilpricedbmanager.repository.SyncSectorRepository;
import com.oilpricedbmanager.service.CustomHexGridService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/sectors")
public class AdminSectorController {
    private final SyncSectorRepository syncSectorRepository;
    private final CustomHexGridService customHexGridService;

    public AdminSectorController(SyncSectorRepository syncSectorRepository, CustomHexGridService customHexGridService) {
        this.syncSectorRepository = syncSectorRepository;
        this.customHexGridService = customHexGridService;
    }

    @GetMapping
    public List<SyncSectorResponse> sectors() {
        return syncSectorRepository.findAll().stream()
                .map(AdminSectorController::toResponse)
                .toList();
    }

    @PostMapping("/generate/custom-hex")
    public HexGridGenerationResponse generateCustomHex(@RequestParam(defaultValue = "5000") int sideLengthMeters) {
        return customHexGridService.generateKoreaHexGrid(sideLengthMeters);
    }

    @PostMapping("/enabled")
    public BulkSectorToggleResponse updateAllEnabled(@RequestParam boolean enabled) {
        int updatedCount = syncSectorRepository.setAllEnabled(enabled);
        return new BulkSectorToggleResponse(enabled, updatedCount);
    }

    @PostMapping("/{sectorId}/status")
    public SyncSectorResponse updateStatus(
            @PathVariable long sectorId,
            @RequestParam SyncTier syncTier
    ) {
        SyncSector updated = syncSectorRepository.updateStatus(sectorId, syncTier)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sector not found: " + sectorId));
        return toResponse(updated);
    }

    private static SyncSectorResponse toResponse(SyncSector sector) {
        return new SyncSectorResponse(
                sector.sectorId(),
                sector.sectorCode(),
                sector.centerLat(),
                sector.centerLon(),
                sector.katecX(),
                sector.katecY(),
                sector.radiusMeters(),
                sector.sideLengthMeters(),
                sector.sectorSource(),
                parseBoundary(sector.sectorShapeWkt()),
                sector.syncTier(),
                sector.enabled(),
                sector.priorityScore(),
                sector.lastSyncedAt(),
                sector.nextSyncAt(),
                sector.memo()
        );
    }

    private static List<SectorPointResponse> parseBoundary(String polygonWkt) {
        if (polygonWkt == null || polygonWkt.isBlank()) {
            return List.of();
        }
        String trimmed = polygonWkt.trim();
        int start = trimmed.indexOf("((");
        int end = trimmed.indexOf("))");
        if (start < 0 || end < 0 || end <= start + 2) {
            return List.of();
        }
        String[] pairs = trimmed.substring(start + 2, end).split(",");
        List<SectorPointResponse> points = new ArrayList<>();
        for (int i = 0; i < pairs.length - 1; i++) {
            String[] lonLat = pairs[i].trim().split("\\s+");
            if (lonLat.length < 2) {
                continue;
            }
            points.add(new SectorPointResponse(Double.parseDouble(lonLat[1]), Double.parseDouble(lonLat[0])));
        }
        return points;
    }
}