package com.keypass.server.common;

/**
 * Thrown whenever a resource doesn't exist OR the caller isn't allowed to see it. Callers that
 * would otherwise get 403 for another user's resource get 404 instead, so they can't tell the
 * resource exists (ADR 0007).
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException() {
        super("Resource not found");
    }

    public NotFoundException(String message) {
        super(message);
    }
}
