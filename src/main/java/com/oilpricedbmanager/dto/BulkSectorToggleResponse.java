package com.oilpricedbmanager.dto;

public record BulkSectorToggleResponse(
        boolean enabled,
        int updatedCount
) {
}