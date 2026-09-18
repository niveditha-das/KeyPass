package com.keypass.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.keypass.common.crypto.BloomFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BloomFilterTest {

    @Test
    void neverProducesAFalseNegative() {
        BloomFilter filter = new BloomFilter(10_000, 0.01);
        List<UUID> added = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            UUID id = UUID.randomUUID();
            added.add(id);
            filter.add(id);
        }
        for (UUID id : added) {
            assertThat(filter.mightContain(id)).isTrue();
        }
    }

    @Test
    void falsePositiveRateIsCloseToTarget() {
        double target = 0.01;
        BloomFilter filter = new BloomFilter(100_000, target);
        for (int i = 0; i < 100_000; i++) {
            filter.add(UUID.randomUUID());
        }

        int trials = 100_000;
        int falsePositives = 0;
        for (int i = 0; i < trials; i++) {
            if (filter.mightContain(UUID.randomUUID())) {
                falsePositives++;
            }
        }
        double observedRate = (double) falsePositives / trials;
        assertThat(observedRate).isLessThan(target * 3);
    }

    @Test
    void roundTripsThroughBytes() {
        BloomFilter filter = new BloomFilter(1_000, 0.01);
        UUID id = UUID.randomUUID();
        filter.add(id);

        BloomFilter restored = BloomFilter.fromBytes(filter.toBytes(), filter.bitSize(), filter.hashFunctions());
        assertThat(restored.mightContain(id)).isTrue();
    }
}
