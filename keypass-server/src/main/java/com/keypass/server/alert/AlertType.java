package com.keypass.server.alert;

public final class AlertType {
    public static final String BRUTE_FORCE = "BRUTE_FORCE";
    public static final String UNUSUAL_TIME = "UNUSUAL_TIME";
    public static final String OFFLINE_USE_AFTER_REVOKE = "OFFLINE_USE_AFTER_REVOKE";

    private AlertType() {}
}
