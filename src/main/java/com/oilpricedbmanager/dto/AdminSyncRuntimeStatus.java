package com.oilpricedbmanager.dto;

public record AdminSyncRuntimeStatus(
        String state,
        String label,
        String syncType,
        String source,
        String requestedAt,
        String startedAt,
        Long elapsedSeconds,
        String elapsedText,
        int queueSize,
        int waitingCount,
        String detail
) {
    public static AdminSyncRuntimeStatus idle(int queueSize, int waitingCount) {
        return new AdminSyncRuntimeStatus(
                "IDLE",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                queueSize,
                waitingCount,
                "대기 중인 작업이 없습니다."
        );
    }
}
