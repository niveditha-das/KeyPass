package com.keypass.server.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived RS256 JWTs that prove who a user is to the API (not what a car key allows). */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final Duration ttl;

    public JwtService(
            JwtEncoder encoder,
            Clock clock,
            @Value("${keypass.jwt.issuer}") String issuer,
            @Value("${keypass.jwt.access-token-ttl-minutes}") long ttlMinutes) {
        this.encoder = encoder;
        this.clock = clock;
        this.issuer = issuer;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public AuthResponse issueToken(AppUser user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", java.util.List.of(user.getRole().name()))
                .build();
        String token = encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
        return new AuthResponse(token, ttl.toSeconds());
    }
}
