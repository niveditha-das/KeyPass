package com.keypass.common.crypto;

import java.time.Duration;

/** A simple, self-contained token bucket. Only works within a single JVM instance. */
public final class TokenBucket {

    private final long capacity;
    private final double refillPerNano;
    private double tokens;
    private long lastRefill;

    public TokenBucket(long capacity, Duration refillPeriod) {
        this.capacity = capacity;
        this.refillPerNano = (double) capacity / refillPeriod.toNanos();
        this.tokens = capacity;
        this.lastRefill = System.nanoTime();
    }

    public synchronized boolean tryAcquire() {
        long now = System.nanoTime();
        tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerNano);
        lastRefill = now;
        if (tokens < 1) {
            return false;
        }
        tokens -= 1;
        return true;
    }
}
