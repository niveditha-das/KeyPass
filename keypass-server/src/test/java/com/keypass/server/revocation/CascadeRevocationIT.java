package com.keypass.server.revocation;

import static org.assertj.core.api.Assertions.assertThat;

import com.keypass.common.crypto.Ed25519;
import com.keypass.common.model.Permission;
import com.keypass.server.IntegrationTest;
import com.keypass.server.TestDataFactory;
import com.keypass.server.auth.AppUser;
import com.keypass.server.device.Device;
import com.keypass.server.key.DigitalKey;
import com.keypass.server.key.DigitalKeyRepository;
import com.keypass.server.key.KeyStatus;
import com.keypass.server.vehicle.Vehicle;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CascadeRevocationIT extends IntegrationTest {

    @Autowired
    private KeyRevocationService revocationService;

    @Autowired
    private DigitalKeyRepository keys;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private Clock clock;

    @Test
    void revokingTheRootRevokesThreeLevelsOfChildren() throws Exception {
        AppUser owner = data.user("owner-" + UUID.randomUUID() + "@example.com");
        AppUser a = data.user("holder-a-" + UUID.randomUUID() + "@example.com");
        AppUser b = data.user("holder-b-" + UUID.randomUUID() + "@example.com");
        AppUser c = data.user("holder-c-" + UUID.randomUUID() + "@example.com");
        Vehicle vehicle = data.vehicle(owner, "1HGCM82633A" + String.format("%06d", System.nanoTime() % 1_000_000));

        Device deviceA = data.device(a, Ed25519.generate());
        Device deviceB = data.device(b, Ed25519.generate());
        Device deviceC = data.device(c, Ed25519.generate());

        Instant now = clock.instant();
        DigitalKey root = data.key(vehicle, a, deviceA, owner, Set.of(Permission.UNLOCK, Permission.SHARE),
                now.minusSeconds(60), now.plusSeconds(7200));
        DigitalKey child = data.key(vehicle, b, deviceB, owner, root.getId(), 1,
                Set.of(Permission.UNLOCK, Permission.SHARE), now, now.plusSeconds(3600), List.of(), null);
        DigitalKey grandchild = data.key(vehicle, c, deviceC, owner, child.getId(), 2,
                Set.of(Permission.UNLOCK), now, now.plusSeconds(1800), List.of(), null);

        List<UUID> revoked = revocationService.revoke(root.getId(), owner.getId());

        assertThat(revoked).containsExactlyInAnyOrder(root.getId(), child.getId(), grandchild.getId());
        assertThat(keys.findById(root.getId()).orElseThrow().getStatus()).isEqualTo(KeyStatus.REVOKED);
        assertThat(keys.findById(child.getId()).orElseThrow().getStatus()).isEqualTo(KeyStatus.REVOKED);
        assertThat(keys.findById(grandchild.getId()).orElseThrow().getStatus()).isEqualTo(KeyStatus.REVOKED);
    }
}
