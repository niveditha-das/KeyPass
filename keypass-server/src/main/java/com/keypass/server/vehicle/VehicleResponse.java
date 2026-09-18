package com.keypass.server.vehicle;

import java.time.Instant;
import java.util.UUID;

public record VehicleResponse(UUID id, String vin, String model, String timeZone, Instant createdAt) {
    public static VehicleResponse from(Vehicle v) {
        return new VehicleResponse(v.getId(), v.getVin(), v.getModel(), v.getTimeZone(), v.getCreatedAt());
    }
}
