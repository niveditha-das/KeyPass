package com.keypass.server.common;

public class InvalidKeyStateException extends RuntimeException {
    public InvalidKeyStateException(Object actual, Object expected) {
        super("Key is " + actual + ", expected " + expected);
    }

    public InvalidKeyStateException(String message) {
        super(message);
    }
}
