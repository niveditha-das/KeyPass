package com.keypass.server.key;

import com.keypass.common.model.Permission;
import com.keypass.server.common.NotFoundException;
import com.keypass.server.device.Device;
import com.keypass.server.device.DeviceRepository;
import com.keypass.server.vehicle.Vehicle;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issuing and sharing (delegating) keys. Every delegation rule here is enforced server-side —
 * a holder can only ever narrow what they pass on, never widen it (see KeyShareServiceTest).
 */
@Service
public class KeyService {

    private static final int MAX_DEPTH = 3;

    private final DigitalKeyRepository keys;
    private final DeviceRepository devices;
    private final KeyAuthorization authz;
    private final Clock clock;

    public KeyService(DigitalKeyRepository keys, DeviceRepository devices, KeyAuthorization authz, Clock clock) {
        this.keys = keys;
        this.devices = devices;
        this.authz = authz;
        this.clock = clock;
    }

    @Transactional
    public DigitalKey issue(Vehicle vehicle, IssueKeyRequest request, UUID actorId) {
        Device device = devices.findById(request.deviceId()).orElseThrow(NotFoundException::new);
        if (!device.getUserId().equals(request.holderId())) {
            throw new IllegalArgumentException("Device does not belong to the holder");
        }

        DigitalKey key = new DigitalKey(
                UUID.randomUUID(),
                vehicle.getId(),
                request.holderId(),
                request.deviceId(),
                actorId,
                null,
                0,
                request.notBefore(),
                request.notAfter(),
                request.permissions(),
                request.policyOrEmpty(),
                request.maxSpeedKmh(),
                clock.instant());
        return keys.save(key);
    }

    @Transactional
    public DigitalKey share(UUID parentKeyId, ShareKeyRequest request, UUID actorId) {
        DigitalKey parent = keys.findByIdForShare(parentKeyId).orElseThrow(NotFoundException::new);
        authz.requireViewable(parent, actorId);

        if (!authz.isHolder(parent, actorId)) {
            throw new IllegalArgumentException("Only the holder of a key can share it");
        }
        if (parent.getStatus() != KeyStatus.ACTIVE) {
            throw new IllegalArgumentException("Parent key is not active");
        }
        if (!parent.getPermissions().contains(Permission.SHARE)) {
            throw new IllegalArgumentException("Parent key does not have SHARE permission");
        }
        if (!parent.getPermissions().containsAll(request.permissions())) {
            throw new IllegalArgumentException("Child permissions must be a subset of the parent's");
        }
        if (request.notBefore().isBefore(parent.getNotBefore()) || request.notAfter().isAfter(parent.getNotAfter())) {
            throw new IllegalArgumentException("Child validity window must sit inside the parent's");
        }
        if (parent.getDepth() + 1 > MAX_DEPTH) {
            throw new IllegalArgumentException("Maximum delegation depth reached");
        }
        Device device = devices.findById(request.deviceId()).orElseThrow(NotFoundException::new);
        if (!device.getUserId().equals(request.holderId())) {
            throw new IllegalArgumentException("Device does not belong to the recipient");
        }
        Integer childMaxSpeed = tighterOf(parent.getMaxSpeedKmh(), request.maxSpeedKmh());

        List<com.keypass.common.model.PolicyRule> childPolicy =
                new java.util.ArrayList<>(parent.getPolicy());
        childPolicy.addAll(request.additionalPolicyOrEmpty());

        DigitalKey child = new DigitalKey(
                UUID.randomUUID(),
                parent.getVehicleId(),
                request.holderId(),
                request.deviceId(),
                actorId,
                parent.getId(),
                parent.getDepth() + 1,
                request.notBefore(),
                request.notAfter(),
                EnumSet.copyOf(request.permissions()),
                childPolicy,
                childMaxSpeed,
                clock.instant());
        return keys.save(child);
    }

    @Transactional
    public DigitalKey suspend(UUID keyId, UUID actorId) {
        DigitalKey key = keys.findByIdForUpdate(keyId).orElseThrow(NotFoundException::new);
        authz.requireOwner(key, actorId);
        key.suspend();
        return keys.save(key);
    }

    @Transactional
    public DigitalKey resume(UUID keyId, UUID actorId) {
        DigitalKey key = keys.findByIdForUpdate(keyId).orElseThrow(NotFoundException::new);
        authz.requireOwner(key, actorId);
        key.resume();
        return keys.save(key);
    }

    private static Integer tighterOf(Integer parentLimit, Integer requested) {
        if (parentLimit == null) {
            return requested;
        }
        if (requested == null) {
            return parentLimit;
        }
        return Math.min(parentLimit, requested);
    }
}
