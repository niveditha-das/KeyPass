package com.keypass.server.vehicle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 17)
    private String vin;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String model;

    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Vehicle() {}

    public Vehicle(
            UUID id, String vin, UUID ownerId, String model, String timeZone, String apiKeyHash, Instant createdAt) {
        this.id = id;
        this.vin = vin;
        this.ownerId = ownerId;
        this.model = model;
        this.timeZone = timeZone;
        this.apiKeyHash = apiKeyHash;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getVin() {
        return vin;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getModel() {
        return model;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public ZoneId getZone() {
        return ZoneId.of(timeZone);
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
