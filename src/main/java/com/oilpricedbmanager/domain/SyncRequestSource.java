package com.oilpricedbmanager.domain;

public enum SyncRequestSource {
    FRONTEND(1),
    MANUAL(2),
    AUTO(3);

    private final int priority;

    SyncRequestSource(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
