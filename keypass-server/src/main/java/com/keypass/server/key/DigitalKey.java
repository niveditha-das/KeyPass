package com.keypass.server.key;

import com.keypass.common.model.Permission;
import com.keypass.common.model.PolicyRule;
import com.keypass.server.common.InvalidKeyStateException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Keys are immutable once issued (ADR 0003): the only mutations after issuance are status
 * transitions (suspend/resume/expire/revoke), never permissions, validity or policy. Editing a
 * key means revoking it and issuing a new one, so an offline car only ever needs a revocation
 * list, never a policy update.
 */
@Entity
@Table(name = "digital_key")
public class DigitalKey {

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "holder_id", nullable = false)
    private UUID holderId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "issued_by", nullable = false)
    private UUID issuedBy;

    @Column(name = "parent_key_id")
    private UUID parentKeyId;

    @Column(nullable = false)
    private int depth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KeyStatus status;

    @Column(name = "not_before", nullable = false)
    private Instant notBefore;

    @Column(name = "not_after", nullable = false)
    private Instant notAfter;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<PolicyRule> policy = new ArrayList<>();

    @Column(name = "max_speed_kmh")
    private Integer maxSpeedKmh;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_epoch")
    private Long revocationEpoch;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "key_permission", joinColumns = @JoinColumn(name = "key_id"))
    @Column(name = "permission")
    @Enumerated(EnumType.STRING)
    private Set<Permission> permissions = EnumSet.noneOf(Permission.class);

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DigitalKey() {}

    public DigitalKey(
            UUID id,
            UUID vehicleId,
            UUID holderId,
            UUID deviceId,
            UUID issuedBy,
            UUID parentKeyId,
            int depth,
            Instant notBefore,
            Instant notAfter,
            Set<Permission> permissions,
            List<PolicyRule> policy,
            Integer maxSpeedKmh,
            Instant createdAt) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.holderId = holderId;
        this.deviceId = deviceId;
        this.issuedBy = issuedBy;
        this.parentKeyId = parentKeyId;
        this.depth = depth;
        this.status = KeyStatus.ACTIVE;
        this.notBefore = notBefore;
        this.notAfter = notAfter;
        this.permissions = EnumSet.copyOf(permissions);
        this.policy = new ArrayList<>(policy);
        this.maxSpeedKmh = maxSpeedKmh;
        this.createdAt = createdAt;
    }

    public void suspend() {
        requireStatus(KeyStatus.ACTIVE);
        status = KeyStatus.SUSPENDED;
    }

    public void resume() {
        requireStatus(KeyStatus.SUSPENDED);
        status = KeyStatus.ACTIVE;
    }

    private void requireStatus(KeyStatus expected) {
        if (status != expected) {
            throw new InvalidKeyStateException(status, expected);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getHolderId() {
        return holderId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public UUID getIssuedBy() {
        return issuedBy;
    }

    public UUID getParentKeyId() {
        return parentKeyId;
    }

    public int getDepth() {
        return depth;
    }

    public KeyStatus getStatus() {
        return status;
    }

    public Instant getNotBefore() {
        return notBefore;
    }

    public Instant getNotAfter() {
        return notAfter;
    }

    public List<PolicyRule> getPolicy() {
        return policy;
    }

    public Integer getMaxSpeedKmh() {
        return maxSpeedKmh;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Long getRevocationEpoch() {
        return revocationEpoch;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
