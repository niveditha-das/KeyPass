package com.keypass.common.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The signed, offline-verifiable proof of what a device is allowed to do to a specific car.
 * Signed by the server's Ed25519 signing key and encoded by CredentialCodec; never mutated
 * after issuance (ADR 0003) — a change means revoking this key and issuing a new one.
 */
public record KeyCredential(
        String kid,
        UUID keyId,
        String vin,
        UUID holderId,
        String devicePublicKey,
        Set<Permission> permissions,
        Instant notBefore,
        Instant notAfter,
        List<PolicyRule> policy,
        Integer maxSpeedKmh,
        Instant issuedAt) {}
