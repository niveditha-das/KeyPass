package com.keypass.server.offline;

import java.time.Instant;
import java.util.List;

public record OfflineBundleResponse(
        List<ServerKeyDto> serverKeys,
        String bloomFilterBase64,
        int bloomBitSize,
        int bloomHashFunctions,
        long revocationEpoch,
        Instant issuedAt,
        Instant validUntil) {

    public record ServerKeyDto(String kid, String publicKey) {}
}
