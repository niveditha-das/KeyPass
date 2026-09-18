package com.keypass.server.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device")
public class Device {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    private String label;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    protected Device() {}

    public Device(UUID id, UUID userId, String publicKey, String label, Instant registeredAt) {
        this.id = id;
        this.userId = userId;
        this.publicKey = publicKey;
        this.label = label;
        this.registeredAt = registeredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getLabel() {
        return label;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }
}
