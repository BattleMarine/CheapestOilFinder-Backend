package com.oilpricedbmanager.external.opinet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ThreadSleepOpinetCallPauseStrategy implements OpinetCallPauseStrategy {
    private final long callPauseMillis;

    public ThreadSleepOpinetCallPauseStrategy(@Value("${opinet.call-interval-millis:30000}") long callPauseMillis) {
        this.callPauseMillis = callPauseMillis;
    }

    @Override
    public void pause() {
        pause(callPauseMillis);
    }

    @Override
    public void pause(long millis) {
        long sleepMillis = Math.max(0L, millis);
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting between Opinet API calls", exception);
        }
    }
}
