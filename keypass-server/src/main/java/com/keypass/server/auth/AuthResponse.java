package com.keypass.server.auth;

public record AuthResponse(String accessToken, long expiresInSeconds) {}
