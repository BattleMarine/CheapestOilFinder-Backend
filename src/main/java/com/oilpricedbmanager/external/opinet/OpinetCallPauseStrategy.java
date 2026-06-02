package com.oilpricedbmanager.external.opinet;

public interface OpinetCallPauseStrategy {
    void pause();

    void pause(long millis);
}
