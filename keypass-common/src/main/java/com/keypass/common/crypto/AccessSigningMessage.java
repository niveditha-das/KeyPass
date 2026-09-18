package com.keypass.common.crypto;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * The exact bytes a phone signs with its device private key to prove it holds a specific key
 * for a specific car, command, challenge and moment. Both the phone and the server must build
 * this identically, so it lives in the shared module rather than being duplicated.
 */
public final class AccessSigningMessage {

    private AccessSigningMessage() {}

    public static byte[] bytes(String vin, String command, String challenge, Instant timestamp) {
        String message = vin + "|" + command + "|" + challenge + "|" + timestamp;
        return message.getBytes(StandardCharsets.UTF_8);
    }
}
