package com.keypass.server.key;

import com.keypass.common.model.Permission;
import com.keypass.common.model.PolicyRule;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record KeyResponse(
        UUID id,
        UUID vehicleId,
        UUID holderId,
        UUID deviceId,
        UUID issuedBy,
        UUID parentKeyId,
        int depth,
        KeyStatus status,
        Instant notBefore,
        Instant notAfter,
        Set<Permission> permissions,
        List<PolicyRule> policy,
        Integer maxSpeedKmh,
        Instant createdAt) {

    public static KeyResponse from(DigitalKey key) {
        return new KeyResponse(
                key.getId(), key.getVehicleId(), key.getHolderId(), key.getDeviceId(), key.getIssuedBy(),
                key.getParentKeyId(), key.getDepth(), key.getStatus(), key.getNotBefore(), key.getNotAfter(),
                key.getPermissions(), key.getPolicy(), key.getMaxSpeedKmh(), key.getCreatedAt());
    }
}
