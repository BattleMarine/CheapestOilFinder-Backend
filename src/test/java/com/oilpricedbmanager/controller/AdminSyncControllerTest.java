package com.oilpricedbmanager.controller;

import com.oilpricedbmanager.domain.ForceSyncScope;
import com.oilpricedbmanager.dto.SyncResponse;
import com.oilpricedbmanager.service.OpinetSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminSyncController.class)
class AdminSyncControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockBean
    OpinetSyncService opinetSyncService;

    @Test
    void force_sync_endpoint_accepts_scope() throws Exception {
        when(opinetSyncService.forceSync(any()))
                .thenReturn(new SyncResponse("FORCE_HOT_ONLY", "SUCCESS", "HOT만 재호출 sectors=1, calls=3, stationRows=10"));

        mockMvc.perform(post("/api/admin/sync/sectors/force")
                        .param("scope", "HOT_ONLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncType").value("FORCE_HOT_ONLY"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(opinetSyncService).forceSync(ForceSyncScope.HOT_ONLY);
    }
}
