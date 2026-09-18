package com.keypass.server.access;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.keypass.common.crypto.TokenBucket;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Per-key brute-force throttle. Only effective on a single server instance — with multiple
 * instances behind a load balancer, this would need to move to Redis.
 */
@Component
public class RateLimiter {

    private final Cache<UUID, TokenBucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    public boolean tryAcquire(UUID keyId) {
        return buckets.get(keyId, id -> new TokenBucket(10, Duration.ofMinutes(1))).tryAcquire();
    }
}
