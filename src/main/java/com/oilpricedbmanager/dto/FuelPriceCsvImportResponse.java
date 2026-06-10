package com.oilpricedbmanager.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FuelPriceCsvImportResponse(
        String fileName,
        long fileSizeBytes,
        LocalDateTime uploadedAt,
        int totalRows,
        int parsedRows,
        int updatedRows,
        int insertedRows,
        int skippedMissingStationRows,
        int invalidRows,
        int zeroOrBlankPriceCells,
        List<String> missingStationIds,
        List<String> warnings
) {
}