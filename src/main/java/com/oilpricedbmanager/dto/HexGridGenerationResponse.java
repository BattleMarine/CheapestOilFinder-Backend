package com.oilpricedbmanager.dto;

public record HexGridGenerationResponse(
        int sideLengthMeters,
        int generatedCount,
        int upsertedCount,
        String source
) {
}
