package com.keypass.server.offline;

import com.keypass.common.model.Command;
import com.keypass.common.model.RuleResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A single access decision the car made on its own, offline, uploaded once it reconnects. */
public record OfflineEventRequest(
        UUID keyId,
        Command command,
        Instant occurredAt,
        boolean granted,
        String reason,
        List<RuleResult> trace) {}
