package com.oilpricedbmanager.domain;

public record FuelPriceCsvRecord(
        String uniId,
        Integer premiumGasolineWon,
        Integer regularGasolineWon,
        Integer dieselWon
) {
}