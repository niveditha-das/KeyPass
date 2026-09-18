package com.keypass.server.audit;

import com.keypass.common.model.Command;
import com.keypass.common.model.GeoPoint;
import com.keypass.common.model.RuleResult;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditEventRepository events;
    private final Clock clock;

    public AuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public void accessGranted(
            UUID vehicleId, UUID keyId, UUID actorId, Command command, List<RuleResult> trace, GeoPoint location) {
        record(vehicleId, keyId, actorId, EventType.ACCESS_GRANTED, command, "ok", trace, location);
    }

    public void accessDenied(
            UUID vehicleId, UUID keyId, Command command, String reason, List<RuleResult> trace, GeoPoint location) {
        record(vehicleId, keyId, null, EventType.ACCESS_DENIED, command, reason, trace, location);
    }

    public void offlineAccessUploaded(
            UUID vehicleId, UUID keyId, Command command, boolean granted, String reason, List<RuleResult> trace) {
        record(vehicleId, keyId, null, EventType.OFFLINE_ACCESS, command,
                (granted ? "GRANTED: " : "DENIED: ") + reason, trace, null);
    }

    public void keyIssued(UUID vehicleId, UUID keyId, UUID actorId) {
        record(vehicleId, keyId, actorId, EventType.KEY_ISSUED, null, null, null, null);
    }

    public void keyShared(UUID vehicleId, UUID keyId, UUID actorId) {
        record(vehicleId, keyId, actorId, EventType.KEY_SHARED, null, null, null, null);
    }

    public void keySuspended(UUID vehicleId, UUID keyId, UUID actorId) {
        record(vehicleId, keyId, actorId, EventType.KEY_SUSPENDED, null, null, null, null);
    }

    public void keyResumed(UUID vehicleId, UUID keyId, UUID actorId) {
        record(vehicleId, keyId, actorId, EventType.KEY_RESUMED, null, null, null, null);
    }

    public void keyRevoked(UUID vehicleId, UUID keyId, UUID actorId) {
        record(vehicleId, keyId, actorId, EventType.KEY_REVOKED, null, null, null, null);
    }

    private void record(
            UUID vehicleId, UUID keyId, UUID actorId, String eventType, Command command,
            String reason, List<RuleResult> trace, GeoPoint location) {
        events.save(new AuditEvent(
                clock.instant(), vehicleId, keyId, actorId, eventType,
                command == null ? null : command.name(), reason, trace,
                location == null ? null : location.lat(),
                location == null ? null : location.lon()));
    }
}
