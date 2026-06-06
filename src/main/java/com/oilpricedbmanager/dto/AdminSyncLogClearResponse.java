package com.oilpricedbmanager.dto;

public record AdminSyncLogClearResponse(
        int totalCount,
        int batchCount,
        int sectorCount,
        String latestBackupFile,
        String timestampedBackupFile,
        String clearedAt
) {
}
