package com.keypass.server.revocation;

import static org.assertj.core.api.Assertions.assertThat;

import com.keypass.common.crypto.Ed25519;
import com.keypass.common.model.AccessRequest;
import com.keypass.common.model.Command;
import com.keypass.common.model.Permission;
import com.keypass.server.IntegrationTest;
import com.keypass.server.TestDataFactory;
import com.keypass.server.access.AccessCheckService;
import com.keypass.server.audit.AuditEventRepository;
import com.keypass.server.audit.EventType;
import com.keypass.server.auth.AppUser;
import com.keypass.server.device.Device;
import com.keypass.server.key.DigitalKey;
import com.keypass.server.vehicle.Vehicle;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves ADR 0004's claim in practice: once a revocation transaction commits, no access check
 * that started afterwards can have been granted. This relies on the FOR SHARE / FOR UPDATE
 * locking in DigitalKeyRepository — remove it and this test starts failing intermittently.
 */
class RevocationConcurrencyIT extends IntegrationTest {

    @Autowired
    private AccessCheckService accessCheckService;

    @Autowired
    private KeyRevocationService revocationService;

    @Autowired
    private AuditEventRepository auditEvents;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private Clock clock;

    @Test
    void noAccessIsGrantedAfterRevocationCommits() throws Exception {
        AppUser owner = data.user("owner-" + UUID.randomUUID() + "@example.com");
        AppUser holder = data.user("holder-" + UUID.randomUUID() + "@example.com");
        Vehicle vehicle = data.vehicle(owner, "1HGCM82633A" + String.format("%06d", System.nanoTime() % 1_000_000));
        KeyPair deviceKeys = Ed25519.generate();
        Device device = data.device(holder, deviceKeys);
        Instant now = clock.instant();
        DigitalKey key = data.key(vehicle, holder, device, owner, Set.of(Permission.UNLOCK),
                now.minusSeconds(60), now.plusSeconds(3600));
        String credential = data.credentialFor(key);

        int requests = 150;
        AtomicInteger nonceCounter = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new java.util.ArrayList<>();

        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    AccessRequest req = data.signedRequest(
                            vehicle.getVin(), credential, Command.UNLOCK,
                            "concurrency-" + nonceCounter.incrementAndGet(), clock.instant(), deviceKeys.getPrivate());
                    return accessCheckService.check(vehicle, req);
                }));
            }
            futures.add(pool.submit(() -> {
                start.await();
                return revocationService.revoke(key.getId(), owner.getId());
            }));
            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        }

        long revokeAuditId = auditEvents.findFirstIdByKeyIdAndEventType(key.getId(), EventType.KEY_REVOKED)
                .orElseThrow();
        long grantsAfterRevoke = auditEvents.countByKeyIdAndEventTypeAndIdGreaterThan(
                key.getId(), EventType.ACCESS_GRANTED, revokeAuditId);

        assertThat(grantsAfterRevoke).isZero();
    }
}
