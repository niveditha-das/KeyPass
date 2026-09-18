package com.keypass.common.model;

public record RuleResult(String rule, boolean passed, String reason) {
    public static RuleResult pass(String rule) {
        return new RuleResult(rule, true, "ok");
    }

    public static RuleResult fail(String rule, String reason) {
        return new RuleResult(rule, false, reason);
    }
}
