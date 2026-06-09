package com.oilpricedbmanager.controller;

import com.oilpricedbmanager.domain.ForceSyncScope;
import com.oilpricedbmanager.domain.FuelType;
import com.oilpricedbmanager.domain.SyncRequestSource;
import com.oilpricedbmanager.dto.AdminSyncLogItem;
import com.oilpricedbmanager.dto.AdminSyncLogClearResponse;
import com.oilpricedbmanager.dto.AdminSyncRuntimeStatus;
import com.oilpricedbmanager.dto.SyncResponse;
import com.oilpricedbmanager.repository.AdminSyncLogRepository;
import com.oilpricedbmanager.service.AdminSyncLogMaintenanceService;
import com.oilpricedbmanager.service.OpinetSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminSyncController.class)
class AdminSyncControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OpinetSyncService opinetSyncService;

    @MockitoBean
    AdminSyncLogRepository adminSyncLogRepository;

    @MockitoBean
    AdminSyncLogMaintenanceService adminSyncLogMaintenanceService;

    @Test
    void sync_status_endpoint_returns_runtime_status() throws Exception {
        when(opinetSyncService.getRuntimeStatus()).thenReturn(new AdminSyncRuntimeStatus(
                "RUNNING",
                "HOT만 재호출",
                "FORCE_HOT_ONLY",
                "MANUAL",
                "2026-06-04 14:31:36",
                "2026-06-04 14:31:36",
                120L,
                "2분 00초",
                2,
                1,
                "HOT만 재호출 sectors=145, calls=10"
        ));

        mockMvc.perform(get("/api/admin/sync/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.label").value("HOT만 재호출"))
                .andExpect(jsonPath("$.elapsedText").value("2분 00초"))
                .andExpect(jsonPath("$.waitingCount").value(1));

        verify(opinetSyncService).getRuntimeStatus();
    }

    @Test
    void force_sync_endpoint_accepts_scope_and_fuel_types() throws Exception {
        when(opinetSyncService.forceSync(any(), any(), any()))
                .thenReturn(new SyncResponse("FORCE_HOT_ONLY", "SUCCESS", "HOT만 재호출 sectors=1, calls=3, stationRows=10"));

        mockMvc.perform(post("/api/admin/sync/sectors/force")
                        .param("scope", "HOT_ONLY")
                        .param("fuelTypes", "REGULAR_GASOLINE", "DIESEL")
                        .param("source", "FRONTEND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncType").value("FORCE_HOT_ONLY"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(opinetSyncService).forceSync(
                eq(ForceSyncScope.HOT_ONLY),
                eq(List.of(FuelType.REGULAR_GASOLINE, FuelType.DIESEL)),
                eq(SyncRequestSource.FRONTEND)
        );
    }

    @Test
    void recent_sync_logs_endpoint_returns_logs() throws Exception {
        when(adminSyncLogRepository.findRecent(3)).thenReturn(List.of(
                new AdminSyncLogItem(
                        "BATCH",
                        35L,
                        null,
                        null,
                        "FORCE_HOT_ONLY",
                        null,
                        null,
                        "RUNNING",
                        null,
                        "진행 중",
                        "2026-06-02 10:31:20",
                        null
                )
        ));

        mockMvc.perform(get("/api/admin/sync/logs/recent")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].logType").value("BATCH"))
                .andExpect(jsonPath("$[0].syncType").value("FORCE_HOT_ONLY"))
                .andExpect(jsonPath("$[0].status").value("RUNNING"));

        verify(adminSyncLogRepository).findRecent(3);
    }

    @Test
    void recent_sync_logs_endpoint_returns_all_logs_when_limit_is_missing() throws Exception {
        when(adminSyncLogRepository.findRecent(null)).thenReturn(List.of(
                new AdminSyncLogItem(
                        "BATCH",
                        35L,
                        null,
                        null,
                        "FORCE_HOT_ONLY",
                        null,
                        null,
                        "RUNNING",
                        null,
                        "진행 중",
                        "2026-06-02 10:31:20",
                        null
                )
        ));

        mockMvc.perform(get("/api/admin/sync/logs/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].logType").value("BATCH"))
                .andExpect(jsonPath("$[0].syncType").value("FORCE_HOT_ONLY"));

        verify(adminSyncLogRepository).findRecent(null);
    }

    @Test
    void clear_sync_logs_endpoint_archives_and_clears_logs() throws Exception {
        when(adminSyncLogMaintenanceService.archiveAndClear()).thenReturn(new AdminSyncLogClearResponse(
                8,
                2,
                6,
                "logs/log_history.csv",
                "logs/log_history_20260605_153000.csv",
                "2026-06-05 15:30:00"
        ));

        mockMvc.perform(post("/api/admin/sync/logs/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(8))
                .andExpect(jsonPath("$.latestBackupFile").value("logs/log_history.csv"));

        verify(adminSyncLogMaintenanceService).archiveAndClear();
    }
}
