package com.keypass.server.access;

import java.time.Instant;
import java.util.UUID;

public record AccessDeniedEvent(UUID vehicleId, UUID keyId, String reason, Instant occurredAt) {}
