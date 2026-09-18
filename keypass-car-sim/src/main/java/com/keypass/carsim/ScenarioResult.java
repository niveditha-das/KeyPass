package com.keypass.carsim;

public record ScenarioResult(String name, boolean passed, String detail) {
    public static ScenarioResult pass(String name, String detail) {
        return new ScenarioResult(name, true, detail);
    }

    public static ScenarioResult fail(String name, String detail) {
        return new ScenarioResult(name, false, detail);
    }
}
