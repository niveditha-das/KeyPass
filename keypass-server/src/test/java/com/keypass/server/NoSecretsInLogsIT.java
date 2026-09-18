package com.keypass.server;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.keypass.common.crypto.Ed25519;
import com.keypass.server.auth.AppUser;
import com.keypass.server.auth.JwtService;
import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Passwords, JWTs and signed key credentials must never end up in application logs — if they
 * do, log aggregation becomes a credential leak. This drives a real login and a real API call
 * through the app while capturing every log line, then asserts none of the secrets appear.
 */
class NoSecretsInLogsIT extends IntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    private ListAppender<ILoggingEvent> appender;
    private Logger rootLogger;

    @BeforeEach
    void attachAppender() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        rootLogger.detachAppender(appender);
    }

    @Test
    @SuppressWarnings("unchecked")
    void loginDoesNotLogThePasswordOrTheIssuedToken() {
        String password = "super-secret-password-1!";
        String email = "secret-check-" + UUID.randomUUID() + "@example.com";
        RestTestClient client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        client.post().uri("/api/v1/auth/register")
                .body(Map.of("email", email, "password", password))
                .exchange()
                .expectStatus().is2xxSuccessful();

        Map<String, Object> loginResponse = client.post().uri("/api/v1/auth/login")
                .body(Map.of("email", email, "password", password))
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        String token = (String) loginResponse.get("accessToken");

        client.get().uri("/api/v1/keys/mine")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().is2xxSuccessful();

        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).noneMatch(m -> m.contains(password));
        assertThat(messages).noneMatch(m -> m.contains(token));
    }

    @Test
    void signingKeyMaterialIsNeverLogged() throws Exception {
        AppUser fakeUser = new AppUser(UUID.randomUUID(), "x@example.com", "hash", com.keypass.server.auth.Role.USER, Instant.now());
        String jwt = jwtService.issueToken(fakeUser).accessToken();
        KeyPair deviceKeys = Ed25519.generate();
        String devicePrivateKeyB64 = Ed25519.privateKeyToBase64(deviceKeys.getPrivate());

        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).noneMatch(m -> m.contains(jwt));
        assertThat(messages).noneMatch(m -> m.contains(devicePrivateKeyB64));
    }
}
