package com.keypass.server.audit;

import com.keypass.common.model.RuleResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditEventResponse(
        Long id, Instant occurredAt, UUID vehicleId, UUID keyId, UUID actorId,
        String eventType, String command, String reason, List<RuleResult> trace) {

    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(
                e.getId(), e.getOccurredAt(), e.getVehicleId(), e.getKeyId(), e.getActorId(),
                e.getEventType(), e.getCommand(), e.getReason(), e.getTrace());
    }
}
