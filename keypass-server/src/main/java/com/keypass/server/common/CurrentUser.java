package com.keypass.server.common;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Pulls the authenticated user's id out of the login JWT's subject claim. */
public final class CurrentUser {

    private CurrentUser() {}

    public static UUID idOf(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
