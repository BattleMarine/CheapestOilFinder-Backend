package com.oilpricedbmanager.dto;

public record AdminSyncLogItem(
        String logType,
        long refId,
        Long sectorId,
        String sectorCode,
        String syncType,
        String fuelType,
        String opinetProdcd,
        String status,
        Integer stationCount,
        String detail,
        String startedAt,
        String finishedAt
) {
}
