package com.keypass.server.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.keypass.common.crypto.Ed25519;
import com.keypass.common.model.Command;
import com.keypass.common.model.Permission;
import com.keypass.server.IntegrationTest;
import com.keypass.server.TestDataFactory;
import com.keypass.server.auth.AppUser;
import com.keypass.server.device.Device;
import com.keypass.server.key.DigitalKey;
import com.keypass.server.vehicle.Vehicle;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReplayProtectionIT extends IntegrationTest {

    @Autowired
    private AccessCheckService accessCheckService;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private Clock clock;

    @Test
    void sameChallengeCannotBeUsedTwice() throws Exception {
        AppUser owner = data.user("owner-" + UUID.randomUUID() + "@example.com");
        AppUser holder = data.user("holder-" + UUID.randomUUID() + "@example.com");
        Vehicle vehicle = data.vehicle(owner, "1HGCM82633A" + String.format("%06d", System.nanoTime() % 1_000_000));
        KeyPair deviceKeys = Ed25519.generate();
        Device device = data.device(holder, deviceKeys);
        Instant now = clock.instant();
        DigitalKey key = data.key(vehicle, holder, device, owner, Set.of(Permission.UNLOCK),
                now.minusSeconds(60), now.plusSeconds(3600));
        String credential = data.credentialFor(key);

        var req = data.signedRequest(vehicle.getVin(), credential, Command.UNLOCK, "replay-me", now, deviceKeys.getPrivate());

        AccessDecision first = accessCheckService.check(vehicle, req);
        AccessDecision second = accessCheckService.check(vehicle, req);

        assertThat(first.decision()).isEqualTo("GRANTED");
        assertThat(second.decision()).isEqualTo("DENIED");
        assertThat(second.reason()).isEqualTo("REPLAY_DETECTED");
    }
}
