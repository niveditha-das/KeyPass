package com.keypass.server.report;

import java.time.Instant;

public record UsageReportRow(String holderEmail, long grants, long denials, Instant lastUsed) {}
