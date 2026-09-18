package com.keypass.server.access;

import com.keypass.common.crypto.InvalidCredentialException;
import com.keypass.common.model.AccessContext;
import com.keypass.common.model.AccessRequest;
import com.keypass.common.model.KeyCredential;
import com.keypass.common.model.RuleResult;
import com.keypass.server.audit.AuditService;
import com.keypass.server.key.DigitalKey;
import com.keypass.server.key.DigitalKeyRepository;
import com.keypass.server.policy.Evaluation;
import com.keypass.server.policy.PolicyEngine;
import com.keypass.server.vehicle.Vehicle;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The heart of the system: decides whether a car should unlock, and writes an audit trail
 * either way. Checks run in a fixed order — signature, rate limit, proof of possession,
 * freshness, replay, current state, policy — each one gated behind the previous ones so an
 * attacker never learns more than the single reason they were stopped at.
 */
@Service
public class AccessCheckService {

    private static final Duration MAX_SKEW = Duration.ofSeconds(30);

    private final CredentialVerifier credentialVerifier;
    private final NonceStore nonceStore;
    private final DigitalKeyRepository keys;
    private final PolicyEngine policyEngine;
    private final AuditService audit;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AccessCheckService(
            CredentialVerifier credentialVerifier,
            NonceStore nonceStore,
            DigitalKeyRepository keys,
            PolicyEngine policyEngine,
            AuditService audit,
            RateLimiter rateLimiter,
            ApplicationEventPublisher events,
            Clock clock) {
        this.credentialVerifier = credentialVerifier;
        this.nonceStore = nonceStore;
        this.keys = keys;
        this.policyEngine = policyEngine;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public AccessDecision check(Vehicle vehicle, AccessRequest req) {
        requireComplete(req);
        Instant now = clock.instant();

        KeyCredential cred;
        try {
            cred = credentialVerifier.verify(req.credential());
        } catch (InvalidCredentialException e) {
            return deny(vehicle.getId(), null, req, "INVALID_CREDENTIAL", List.of());
        }
        if (!cred.vin().equals(vehicle.getVin())) {
            return deny(vehicle.getId(), cred.keyId(), req, "WRONG_VEHICLE", List.of());
        }

        if (!rateLimiter.tryAcquire(cred.keyId())) {
            return deny(vehicle.getId(), cred.keyId(), req, "RATE_LIMITED", List.of());
        }

        if (!credentialVerifier.deviceSignatureValid(cred, vehicle.getVin(), req)) {
            return deny(vehicle.getId(), cred.keyId(), req, "BAD_DEVICE_SIGNATURE", List.of());
        }

        if (Duration.between(req.timestamp(), now).abs().compareTo(MAX_SKEW) > 0) {
            return deny(vehicle.getId(), cred.keyId(), req, "STALE_REQUEST", List.of());
        }
        if (!nonceStore.consume(vehicle.getId(), req.challenge(), now.plus(MAX_SKEW.multipliedBy(2)))) {
            return deny(vehicle.getId(), cred.keyId(), req, "REPLAY_DETECTED", List.of());
        }

        DigitalKey key = keys.findByIdForShare(cred.keyId()).orElse(null);
        if (key == null) {
            return deny(vehicle.getId(), cred.keyId(), req, "UNKNOWN_KEY", List.of());
        }

        AccessContext ctx = new AccessContext(now, req.command(), req.location(), vehicle.getZone());
        Evaluation eval = policyEngine.evaluate(key, ctx);

        if (!eval.granted()) {
            return deny(vehicle.getId(), key.getId(), req, firstFailure(eval), eval.trace());
        }

        audit.accessGranted(vehicle.getId(), key.getId(), key.getHolderId(), req.command(), eval.trace(), req.location());
        events.publishEvent(new AccessGrantedEvent(vehicle.getId(), key.getId(), key.getHolderId(), now));
        return AccessDecision.granted(eval.trace(), key.getMaxSpeedKmh());
    }

    private AccessDecision deny(
            java.util.UUID vehicleId, java.util.UUID keyId, AccessRequest req, String reason, List<RuleResult> trace) {
        audit.accessDenied(vehicleId, keyId, req.command(), reason, trace, req.location());
        events.publishEvent(new AccessDeniedEvent(vehicleId, keyId, reason, clock.instant()));
        return AccessDecision.denied(reason, trace);
    }

    private static void requireComplete(AccessRequest req) {
        if (req.credential() == null || req.command() == null || req.challenge() == null
                || req.timestamp() == null || req.deviceSignature() == null) {
            throw new IllegalArgumentException("credential, command, challenge, timestamp and deviceSignature are required");
        }
    }

    private static String firstFailure(Evaluation eval) {
        return eval.trace().stream().filter(r -> !r.passed()).findFirst().map(RuleResult::rule).orElse("UNKNOWN");
    }
}
