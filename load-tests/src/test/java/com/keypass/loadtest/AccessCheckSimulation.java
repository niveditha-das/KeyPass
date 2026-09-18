package com.keypass.loadtest;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import com.keypass.common.crypto.AccessSigningMessage;
import com.keypass.common.crypto.Ed25519;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Load-tests the single hottest endpoint in the system: POST /vehicles/{vin}/access-checks —
 * the one a real car calls on every unlock attempt, and the one doing the most work per request
 * (signature verification x2, a nonce insert, a row lock, full policy evaluation, an audit
 * write). Run against a server you've started yourself:
 *
 *   cd load-tests
 *   mvn gatling:test -Dkeypass.baseUrl=http://localhost:8080 -Dkeypass.keyCount=200 \
 *       -Dkeypass.usersPerSec=100 -Dkeypass.durationSeconds=30
 */
public class AccessCheckSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("keypass.baseUrl", "http://localhost:8080");
    // Each key allows 10 requests/minute (RateLimiter); keep the average per-key request rate
    // (usersPerSec / keyCount) comfortably under that so RATE_LIMITED denials don't show up as
    // load-test "failures" that are actually just the rate limiter correctly doing its job.
    private static final int KEY_COUNT = Integer.getInteger("keypass.keyCount", 400);
    private static final double USERS_PER_SEC = Double.parseDouble(System.getProperty("keypass.usersPerSec", "50"));
    private static final int DURATION_SECONDS = Integer.getInteger("keypass.durationSeconds", 30);

    private static final List<LoadTestFixtures.Fixture> FIXTURES = LoadTestFixtures.seed(BASE_URL, KEY_COUNT);

    private final List<Map<String, Object>> feederData = buildFeederData();

    private static List<Map<String, Object>> buildFeederData() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LoadTestFixtures.Fixture f : FIXTURES) {
            rows.add(Map.of(
                    "vin", f.vin(),
                    "vehicleApiKey", f.vehicleApiKey(),
                    "credential", f.credential(),
                    "devicePrivateKeyBase64", f.devicePrivateKeyBase64()));
        }
        return rows;
    }

    private final HttpProtocolBuilder httpProtocol = http.baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    private final ChainBuilder signRequest = exec(session -> {
        try {
            String vin = session.getString("vin");
            String devicePrivateKeyBase64 = session.getString("devicePrivateKeyBase64");
            PrivateKey deviceKey = Ed25519.privateKeyFromBase64(devicePrivateKeyBase64);
            String challenge = UUID.randomUUID().toString();
            Instant timestamp = Instant.now();

            byte[] message = AccessSigningMessage.bytes(vin, "UNLOCK", challenge, timestamp);
            byte[] signature = Ed25519.sign(deviceKey, message);

            return session
                    .set("challenge", challenge)
                    .set("timestamp", timestamp.toString())
                    .set("deviceSignature", Base64.getEncoder().encodeToString(signature));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    private final ScenarioBuilder scenario = scenario("Access check")
            .feed(listFeeder(feederData).random())
            .exec(signRequest)
            .exec(http("POST /vehicles/{vin}/access-checks")
                    .post("/api/v1/vehicles/#{vin}/access-checks")
                    .header("X-Vehicle-Key", "#{vehicleApiKey}")
                    .body(StringBody(session -> """
                            {
                              "credential": "%s",
                              "command": "UNLOCK",
                              "challenge": "%s",
                              "timestamp": "%s",
                              "deviceSignature": "%s"
                            }
                            """.formatted(
                            session.getString("credential"),
                            session.getString("challenge"),
                            session.getString("timestamp"),
                            session.getString("deviceSignature"))))
                    .check(status().is(200))
                    .check(jsonPath("$.decision").is("GRANTED")));

    {
        setUp(
                scenario.injectOpen(
                        rampUsersPerSec(1).to(USERS_PER_SEC).during(Duration.ofSeconds(10)),
                        constantUsersPerSec(USERS_PER_SEC).during(Duration.ofSeconds(DURATION_SECONDS))))
                .protocols(httpProtocol)
                .assertions(
                        global().successfulRequests().percent().is(100.0),
                        global().responseTime().percentile(95).lt(500));
    }
}
