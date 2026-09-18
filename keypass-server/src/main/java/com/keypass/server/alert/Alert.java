package com.keypass.server.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert")
public class Alert {

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "key_id")
    private UUID keyId;

    @Column(name = "alert_type", nullable = false)
    private String alertType;

    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private boolean acknowledged;

    protected Alert() {}

    public Alert(UUID id, UUID vehicleId, UUID keyId, String alertType, String details, Instant createdAt) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.keyId = keyId;
        this.alertType = alertType;
        this.details = details;
        this.createdAt = createdAt;
        this.acknowledged = false;
    }

    public void acknowledge() {
        this.acknowledged = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getKeyId() {
        return keyId;
    }

    public String getAlertType() {
        return alertType;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }
}
