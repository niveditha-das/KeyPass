package com.keypass.server;

import com.keypass.common.crypto.AccessSigningMessage;
import com.keypass.common.crypto.Ed25519;
import com.keypass.common.model.AccessRequest;
import com.keypass.common.model.Command;
import com.keypass.common.model.GeoPoint;
import com.keypass.common.model.Permission;
import com.keypass.common.model.PolicyRule;
import com.keypass.server.auth.AppUser;
import com.keypass.server.auth.AppUserRepository;
import com.keypass.server.auth.Role;
import com.keypass.server.device.Device;
import com.keypass.server.device.DeviceRepository;
import com.keypass.server.key.CredentialIssuer;
import com.keypass.server.key.DigitalKey;
import com.keypass.server.key.DigitalKeyRepository;
import com.keypass.server.vehicle.Vehicle;
import com.keypass.server.vehicle.VehicleRepository;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Shared setup helpers so integration tests don't each hand-roll owners, vehicles and keys. */
@Component
public class TestDataFactory {

    @Autowired
    private AppUserRepository users;

    @Autowired
    private VehicleRepository vehicles;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private DigitalKeyRepository keys;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CredentialIssuer credentialIssuer;

    @Autowired
    private Clock clock;

    public AppUser user(String email) {
        AppUser user = new AppUser(UUID.randomUUID(), email, passwordEncoder.encode("password123"), Role.USER, clock.instant());
        return users.save(user);
    }

    public String rawVehicleApiKey = "test-vehicle-api-key";

    public Vehicle vehicle(AppUser owner, String vin) {
        return vehicle(owner, vin, "Europe/Dublin");
    }

    public Vehicle vehicle(AppUser owner, String vin, String timeZone) {
        Vehicle v = new Vehicle(
                UUID.randomUUID(), vin, owner.getId(), "Test Model", timeZone,
                passwordEncoder.encode(rawVehicleApiKey), clock.instant());
        return vehicles.save(v);
    }

    public Device device(AppUser owner, KeyPair deviceKeyPair) {
        Device d = new Device(
                UUID.randomUUID(), owner.getId(), Ed25519.publicKeyToBase64(deviceKeyPair.getPublic()),
                "Test Device", clock.instant());
        return devices.save(d);
    }

    public DigitalKey key(
            Vehicle vehicle, AppUser holder, Device device, AppUser issuer, Set<Permission> permissions,
            Instant notBefore, Instant notAfter) {
        return key(vehicle, holder, device, issuer, null, 0, permissions, notBefore, notAfter, List.of(), null);
    }

    public DigitalKey key(
            Vehicle vehicle, AppUser holder, Device device, AppUser issuer, UUID parentKeyId, int depth,
            Set<Permission> permissions, Instant notBefore, Instant notAfter, List<PolicyRule> policy, Integer maxSpeed) {
        DigitalKey key = new DigitalKey(
                UUID.randomUUID(), vehicle.getId(), holder.getId(), device.getId(), issuer.getId(),
                parentKeyId, depth, notBefore, notAfter, permissions, policy, maxSpeed, clock.instant());
        return keys.save(key);
    }

    public String credentialFor(DigitalKey key) {
        try {
            return credentialIssuer.issue(key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public AccessRequest signedRequest(
            String vin, String credential, Command command, String challenge, Instant timestamp,
            PrivateKey devicePrivateKey) {
        return signedRequest(vin, credential, command, challenge, timestamp, devicePrivateKey, null);
    }

    public AccessRequest signedRequest(
            String vin, String credential, Command command, String challenge, Instant timestamp,
            PrivateKey devicePrivateKey, GeoPoint location) {
        try {
            byte[] message = AccessSigningMessage.bytes(vin, command.name(), challenge, timestamp);
            byte[] sig = Ed25519.sign(devicePrivateKey, message);
            String sigB64 = Base64.getEncoder().encodeToString(sig);
            return new AccessRequest(credential, command, challenge, timestamp, location, sigB64);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
