package com.oilpricedbmanager.dto;

import java.time.LocalDateTime;

public record FuelPriceSummary(
        Integer regularGasolineWon,
        Integer premiumGasolineWon,
        Integer dieselWon,
        Integer lpgWon,
        LocalDateTime updatedAt
) {
}
