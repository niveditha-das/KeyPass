package com.keypass.server.device;

import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(UUID id, String publicKey, String label, Instant registeredAt) {
    public static DeviceResponse from(Device d) {
        return new DeviceResponse(d.getId(), d.getPublicKey(), d.getLabel(), d.getRegisteredAt());
    }
}
