package com.keypass.server.report;

public record DenialReasonRow(String reason, long count, double percentage) {}
