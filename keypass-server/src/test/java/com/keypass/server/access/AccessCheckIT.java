package com.keypass.server.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.keypass.common.crypto.Ed25519;
import com.keypass.common.model.Command;
import com.keypass.common.model.CurfewRule;
import com.keypass.common.model.GeoPoint;
import com.keypass.common.model.GeofenceRule;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccessCheckIT extends IntegrationTest {

    @Autowired
    private AccessCheckService accessCheckService;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private Clock clock;

    private AppUser owner;
    private AppUser holder;
    private Vehicle vehicle;
    private Device device;
    private KeyPair deviceKeys;

    @BeforeEach
    void setUp() throws Exception {
        owner = data.user("owner-" + UUID.randomUUID() + "@example.com");
        holder = data.user("holder-" + UUID.randomUUID() + "@example.com");
        vehicle = data.vehicle(owner, uniqueVin());
        deviceKeys = Ed25519.generate();
        device = data.device(holder, deviceKeys);
    }

    private String uniqueVin() {
        return "1HGCM82633A" + String.format("%06d", System.nanoTime() % 1_000_000);
    }

    private DigitalKey activeKey(Set<Permission> permissions) {
        Instant now = clock.instant();
        return data.key(vehicle, holder, device, owner, permissions, now.minusSeconds(60), now.plusSeconds(3600));
    }

    @Test
    void normalUnlockIsGranted() {
        DigitalKey key = activeKey(Set.of(Permission.UNLOCK));
        String credential = data.credentialFor(key);
        var req = data.signedRequest(vehicle.getVin(), credential, Command.UNLOCK, "chal-1", clock.instant(), deviceKeys.getPrivate());

        AccessDecision decision = accessCheckService.check(vehicle, req);

        assertThat(decision.decision()).isEqualTo("GRANTED");
    }

    @Test
    void expiredKeyIsDenied() {
        Instant now = clock.instant();
        DigitalKey key = data.key(vehicle, holder, device, owner, Set.of(Permission.UNLOCK),
                now.minusSeconds(7200), now.minusSeconds(3600));
        String credential = data.credentialFor(key);
        var req = data.signedRequest(vehicle.getVin(), credential, Command.UNLOCK, "chal-2", now, deviceKeys.getPrivate());

        AccessDecision decision = accessCheckService.check(vehicle, req);

        assertThat(decision.decision()).isEqualTo("DENIED");
        assertThat(decision.reason()).isEqualTo("VALIDITY");
    }

    @Test
    void missingPermissionIsDenied() {
        DigitalKey key = activeKey(Set.of(Permission.LOCK));
        String credential = data.credentialFor(key);
        var req = data.signedRequest(vehicle.getVin(), credential, Command.UNLOCK, "chal-3", clock.instant(), deviceKeys.getPrivate());

        AccessDecision decision = accessCheckService.check(vehicle, req);

        assertThat(decision.decision()).isEqualTo("DENIED");
        assertThat(decision.reason()).isEqualTo("PERMISSION");
    }

    @Test
    void stolenCredentialFailsWithoutTheRightDeviceKey() throws Exception {
        DigitalKey key = activeKey(Set.of(Permission.UNLOCK));
        String credential = data.credentialFor(key);
        KeyPair attackerKeys = Ed25519.generate();

        var req = data.signedRequest(vehicle.getVin(), credential, Command.UNLOCK, "chal-4", clock.instant(), attackerKeys.getPrivate());

        AccessDecision decision = accessCheckService.check(vehicle, req);

        assertThat(decision.decision()).isEqualTo("DENIED");
        assertThat(decision.reason()).isEqualTo("BAD_DEVICE_SIGNATURE");
    }

    @Test
    void tamperedCredentialIsRejected() {
        DigitalKey key = activeKey(Set.of(Permission.UNLOCK));
        String credential = data.credentialFor(key);
        String[] parts = credential.split("\\.");
        String tamperedPayload = parts[0].substring(0, parts[0].length() - 4) + "AAAA";
        String tampered = tamperedPayload + "." + parts[1];

        var req = data.signedRequest(vehicle.getVin(), tampered, Command.UNLOCK, "chal-5", clock.instant(), deviceKeys.getPrivate());

        AccessDecision decision = accessCheckService.check(vehicle, req);

        assertThat(decision.decision()).isEqualTo("DENIED");
        assertThat(decision.reason()).isEqualTo("INVALID_CREDENTIAL");
    }

    @Test
    void geofenceBreachIsDenied() {
        Instant now = clock.instant();
        GeofenceRule fence = new GeofenceRule(List.of(
                new GeoPoint(0, 0), new GeoPoint(0, 1), new GeoPoint(1, 1), new GeoPoint(1, 0)));
        DigitalKey key = data.key(vehicle, holder, device, owner, null, 0, Set.of(Permission.UNLOCK),
                now.minusSeconds(60), now.plusSeconds(3600), List.of(fence), null);
        String credential = data.credentialFor(key);
        var req = data.signedRequest(vehicle.getVin(), credential, Command.UNLOCK, "chal-6", now,
                deviceKeys.getPrivate(), new GeoPoint(50, 50));

        AccessDecision decision = accessCheckService.check(vehicle, req);

        assertThat(decision.decision()).isEqualTo("DENIED");
        assertThat(decision.reason()).isEqualTo("GEOFENCE");
    }

    @Test
    void curfewBlocksEngineStart() {
        // The request must be signed "now" (within the ±30s freshness window the service checks
        // against the real clock), so curfew is exercised directly through the policy engine
        // instead of via a frozen Clock bean — see PolicyEngineTest and CurfewRuleTest in
        // keypass-common for deterministic coverage of the curfew boundary logic itself.
        Vehicle dublinCar = data.vehicle(owner, uniqueVin(), "Europe/Dublin");
        Instant now = clock.instant();
        CurfewRule allDayCurfew = new CurfewRule(LocalTime.of(0, 0), LocalTime.of(23, 59, 59));
        DigitalKey key = data.key(dublinCar, holder, device, owner, null, 0, Set.of(Permission.START_ENGINE),
                now.minusSeconds(3600), now.plusSeconds(3600), List.of(allDayCurfew), null);
        String credential = data.credentialFor(key);
        var req = data.signedRequest(dublinCar.getVin(), credential, Command.START_ENGINE, "chal-7", now, deviceKeys.getPrivate());

        AccessDecision decision = accessCheckService.check(dublinCar, req);

        assertThat(decision.decision()).isEqualTo("DENIED");
        assertThat(decision.reason()).isEqualTo("CURFEW");
    }

    @Test
    void wrongVehicleIsDenied() {
        Vehicle otherVehicle = data.vehicle(owner, uniqueVin());
        DigitalKey key = activeKey(Set.of(Permission.UNLOCK));
        String credential = data.credentialFor(key);
        var req = data.signedRequest(otherVehicle.getVin(), credential, Command.UNLOCK, "chal-8", clock.instant(), deviceKeys.getPrivate());

        AccessDecision decision = accessCheckService.check(otherVehicle, req);

        assertThat(decision.decision()).isEqualTo("DENIED");
        assertThat(decision.reason()).isEqualTo("WRONG_VEHICLE");
    }
}
