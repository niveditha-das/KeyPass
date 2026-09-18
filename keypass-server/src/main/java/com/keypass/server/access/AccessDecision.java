package com.keypass.server.access;

import com.keypass.common.model.RuleResult;
import java.util.List;

public record AccessDecision(String decision, String reason, Integer maxSpeedKmh, List<RuleResult> trace) {

    public static AccessDecision granted(List<RuleResult> trace, Integer maxSpeedKmh) {
        return new AccessDecision("GRANTED", null, maxSpeedKmh, trace);
    }

    public static AccessDecision denied(String reason, List<RuleResult> trace) {
        return new AccessDecision("DENIED", reason, null, trace);
    }
}
