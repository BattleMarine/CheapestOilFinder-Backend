package com.oilpricedbmanager.domain;

import java.util.List;

public enum ForceSyncScope {
    HOT_ONLY("HOT만 재호출", "FORCE_HOT_ONLY", List.of(SyncTier.HOT)),
    HOT_WARM("HOT+WARM 재호출", "FORCE_HOT_WARM", List.of(SyncTier.HOT, SyncTier.WARM)),
    WARM_ONLY("WARM만 재호출", "FORCE_WARM_ONLY", List.of(SyncTier.WARM)),
    COLD_ONLY("COLD만 재호출", "FORCE_COLD_ONLY", List.of(SyncTier.COLD));

    private final String displayName;
    private final String syncType;
    private final List<SyncTier> tiers;

    ForceSyncScope(String displayName, String syncType, List<SyncTier> tiers) {
        this.displayName = displayName;
        this.syncType = syncType;
        this.tiers = List.copyOf(tiers);
    }

    public String displayName() {
        return displayName;
    }

    public String syncType() {
        return syncType;
    }

    public List<SyncTier> tiers() {
        return tiers;
    }
}
