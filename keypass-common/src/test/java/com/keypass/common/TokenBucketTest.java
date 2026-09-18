package com.keypass.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.keypass.common.crypto.TokenBucket;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    @Test
    void allowsUpToCapacityThenBlocks() {
        TokenBucket bucket = new TokenBucket(5, Duration.ofMinutes(1));
        for (int i = 0; i < 5; i++) {
            assertThat(bucket.tryAcquire()).isTrue();
        }
        assertThat(bucket.tryAcquire()).isFalse();
    }

    @Test
    void refillsOverTime() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(1, Duration.ofMillis(50));
        assertThat(bucket.tryAcquire()).isTrue();
        assertThat(bucket.tryAcquire()).isFalse();

        Thread.sleep(100);
        assertThat(bucket.tryAcquire()).isTrue();
    }
}
