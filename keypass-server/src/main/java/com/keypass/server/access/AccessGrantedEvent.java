package com.keypass.server.access;

import java.time.Instant;
import java.util.UUID;

public record AccessGrantedEvent(UUID vehicleId, UUID keyId, UUID holderId, Instant occurredAt) {}
