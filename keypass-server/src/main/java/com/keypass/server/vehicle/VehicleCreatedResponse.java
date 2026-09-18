package com.keypass.server.vehicle;

import java.time.Instant;
import java.util.UUID;

/** Carries the raw API key. Only ever returned once, at creation time. */
public record VehicleCreatedResponse(
        UUID id, String vin, String model, String timeZone, String apiKey, Instant createdAt) {
    public static VehicleCreatedResponse of(Vehicle v, String apiKey) {
        return new VehicleCreatedResponse(v.getId(), v.getVin(), v.getModel(), v.getTimeZone(), apiKey, v.getCreatedAt());
    }
}
