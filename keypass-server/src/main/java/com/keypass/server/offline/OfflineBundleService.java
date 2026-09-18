package com.keypass.server.offline;

import com.keypass.common.crypto.BloomFilter;
import com.keypass.common.crypto.Ed25519;
import com.keypass.server.key.DigitalKeyRepository;
import com.keypass.server.key.SigningKeyRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Builds the bundle a car downloads before going out of signal range: the server's public
 * keys, and a Bloom filter of every currently revoked key for this vehicle. The bundle expires
 * after 24 hours (ADR 0005) — a car that hasn't synced within that window must fail closed
 * rather than trust a stale revocation list.
 */
@Service
public class OfflineBundleService {

    private static final Duration BUNDLE_TTL = Duration.ofHours(24);
    private static final double FALSE_POSITIVE_RATE = 0.01;

    private final DigitalKeyRepository keys;
    private final SigningKeyRegistry signingKeys;
    private final Clock clock;

    public OfflineBundleService(DigitalKeyRepository keys, SigningKeyRegistry signingKeys, Clock clock) {
        this.keys = keys;
        this.signingKeys = signingKeys;
        this.clock = clock;
    }

    public OfflineBundleResponse buildBundle(UUID vehicleId) {
        List<UUID> revokedIds = keys.findRevokedKeyIds(vehicleId);
        int expectedItems = Math.max(revokedIds.size(), 16);
        BloomFilter filter = new BloomFilter(expectedItems, FALSE_POSITIVE_RATE);
        revokedIds.forEach(filter::add);

        long epoch = keys.findMaxRevocationEpoch(vehicleId).orElse(0L);
        var now = clock.instant();

        List<OfflineBundleResponse.ServerKeyDto> serverKeys = signingKeys.allPublicKeys().entrySet().stream()
                .map(e -> new OfflineBundleResponse.ServerKeyDto(e.getKey(), Ed25519.publicKeyToBase64(e.getValue())))
                .toList();

        return new OfflineBundleResponse(
                serverKeys,
                java.util.Base64.getEncoder().encodeToString(filter.toBytes()),
                filter.bitSize(),
                filter.hashFunctions(),
                epoch,
                now,
                now.plus(BUNDLE_TTL));
    }
}
