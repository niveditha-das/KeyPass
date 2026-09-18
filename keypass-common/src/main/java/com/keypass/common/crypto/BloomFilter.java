package com.keypass.common.crypto;

import java.util.BitSet;
import java.util.UUID;

/**
 * A compact, fail-safe "might be revoked" set for offline cars. False positives are acceptable
 * (a rare wrongly-denied key); false negatives are not, and this structure never produces them.
 */
public final class BloomFilter {

    private final BitSet bits;
    private final int m;
    private final int k;

    public BloomFilter(int expectedItems, double falsePositiveRate) {
        this.m = (int) Math.ceil(-expectedItems * Math.log(falsePositiveRate) / (Math.log(2) * Math.log(2)));
        this.k = Math.max(1, (int) Math.round((double) m / expectedItems * Math.log(2)));
        this.bits = new BitSet(m);
    }

    private BloomFilter(BitSet bits, int m, int k) {
        this.bits = bits;
        this.m = m;
        this.k = k;
    }

    public void add(UUID id) {
        for (int i = 0; i < k; i++) {
            bits.set(index(id, i));
        }
    }

    public boolean mightContain(UUID id) {
        for (int i = 0; i < k; i++) {
            if (!bits.get(index(id, i))) {
                return false;
            }
        }
        return true;
    }

    private int index(UUID id, int i) {
        long h1 = id.getMostSignificantBits();
        long h2 = id.getLeastSignificantBits();
        return (int) Math.floorMod(h1 + (long) i * h2, (long) m);
    }

    public byte[] toBytes() {
        return bits.toByteArray();
    }

    public int bitSize() {
        return m;
    }

    public int hashFunctions() {
        return k;
    }

    public static BloomFilter fromBytes(byte[] data, int m, int k) {
        return new BloomFilter(BitSet.valueOf(data), m, k);
    }
}
