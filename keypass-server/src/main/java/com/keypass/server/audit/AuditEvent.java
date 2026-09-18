package com.keypass.server.audit;

import com.keypass.common.model.RuleResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only: the database itself rejects UPDATE/DELETE on this table (see V1__init.sql). */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "key_id")
    private UUID keyId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    private String command;

    @Column(length = 300)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<RuleResult> trace;

    private Double lat;
    private Double lon;

    protected AuditEvent() {}

    public AuditEvent(
            Instant occurredAt, UUID vehicleId, UUID keyId, UUID actorId, String eventType,
            String command, String reason, List<RuleResult> trace, Double lat, Double lon) {
        this.occurredAt = occurredAt;
        this.vehicleId = vehicleId;
        this.keyId = keyId;
        this.actorId = actorId;
        this.eventType = eventType;
        this.command = command;
        this.reason = reason;
        this.trace = trace;
        this.lat = lat;
        this.lon = lon;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getKeyId() {
        return keyId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getCommand() {
        return command;
    }

    public String getReason() {
        return reason;
    }

    public List<RuleResult> getTrace() {
        return trace;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLon() {
        return lon;
    }
}
