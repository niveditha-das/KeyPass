package com.keypass.server.audit;

public final class EventType {
    public static final String ACCESS_GRANTED = "ACCESS_GRANTED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String KEY_ISSUED = "KEY_ISSUED";
    public static final String KEY_SHARED = "KEY_SHARED";
    public static final String KEY_SUSPENDED = "KEY_SUSPENDED";
    public static final String KEY_RESUMED = "KEY_RESUMED";
    public static final String KEY_REVOKED = "KEY_REVOKED";
    public static final String OFFLINE_ACCESS = "OFFLINE_ACCESS";

    private EventType() {}
}
