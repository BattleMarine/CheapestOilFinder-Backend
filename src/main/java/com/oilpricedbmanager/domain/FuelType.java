package com.oilpricedbmanager.domain;

public enum FuelType {
    REGULAR_GASOLINE("gas_low", "B027"),
    PREMIUM_GASOLINE("gas_hign", "B034"),
    DIESEL("disl", "D047"),
    LPG("lpg", "K015");

    private final String columnName;
    private final String opinetProductCode;

    FuelType(String columnName, String opinetProductCode) {
        this.columnName = columnName;
        this.opinetProductCode = opinetProductCode;
    }

    public String columnName() {
        return columnName;
    }

    public String opinetProductCode() {
        return opinetProductCode;
    }

    public static FuelType fromOpinetProductCode(String productCode) {
        for (FuelType fuelType : values()) {
            if (fuelType.opinetProductCode.equals(productCode)) {
                return fuelType;
            }
        }
        throw new IllegalArgumentException("Unsupported Opinet product code: " + productCode);
    }
}