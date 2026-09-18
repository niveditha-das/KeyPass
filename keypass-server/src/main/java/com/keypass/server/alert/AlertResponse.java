package com.keypass.server.alert;

import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        UUID id, UUID vehicleId, UUID keyId, String alertType, String details, Instant createdAt, boolean acknowledged) {

    public static AlertResponse from(Alert a) {
        return new AlertResponse(
                a.getId(), a.getVehicleId(), a.getKeyId(), a.getAlertType(), a.getDetails(),
                a.getCreatedAt(), a.isAcknowledged());
    }
}
