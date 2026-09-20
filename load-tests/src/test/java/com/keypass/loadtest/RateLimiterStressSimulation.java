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
import java.util.concurrent.atomic.AtomicLong;

/**
 * The counterpart to {@link AccessCheckSimulation}: that test deliberately keeps every key under
 * its rate limit to measure steady-state latency; this one does the opposite. A handful of keys
 * are hammered at a throughput far above the 10 requests/minute each is allowed, which is what a
 * brute-force attacker replaying one stolen credential looks like. It proves two things under
 * sustained load: the limiter holds (grants per key never exceed the bucket's burst plus what
 * it refills over the run), and the server stays healthy while rejecting (no 5xx, bounded p95).
 * A rate-limited request is an HTTP 200 carrying decision DENIED / reason RATE_LIMITED.
 *
 *   cd load-tests
 *   mvn gatling:test -Dgatling.simulationClass=com.keypass.loadtest.RateLimiterStressSimulation \
 *       -Dkeypass.baseUrl=http://localhost:8080
 */
public class RateLimiterStressSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("keypass.baseUrl", "http://localhost:8080");
    private static final int KEY_COUNT = Integer.getInteger("keypass.keyCount", 5);
    private static final double USERS_PER_SEC = Double.parseDouble(System.getProperty("keypass.usersPerSec", "50"));
    private static final int DURATION_SECONDS = Integer.getInteger("keypass.durationSeconds", 30);

    // Mirror RateLimiter: 10 tokens, refilled at 10 per minute (one every 6 seconds).
    private static final int BUCKET_CAPACITY = 10;
    private static final int REFILL_SECONDS_PER_TOKEN = 6;

    private static final AtomicLong GRANTED = new AtomicLong();
    private static final AtomicLong RATE_LIMITED = new AtomicLong();
    private static final AtomicLong OTHER_DENIALS = new AtomicLong();

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
            PrivateKey deviceKey = Ed25519.privateKeyFromBase64(session.getString("devicePrivateKeyBase64"));
            String challenge = UUID.randomUUID().toString();
            Instant timestamp = Instant.now();
            byte[] message = AccessSigningMessage.bytes(vin, "UNLOCK", challenge, timestamp);
            return session
                    .set("challenge", challenge)
                    .set("timestamp", timestamp.toString())
                    .set(
                            "deviceSignature",
                            Base64.getEncoder().encodeToString(Ed25519.sign(deviceKey, message)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    private final ChainBuilder tally = exec(session -> {
        String decision = session.getString("decision");
        if ("GRANTED".equals(decision)) {
            GRANTED.incrementAndGet();
        } else if ("RATE_LIMITED".equals(session.getString("reason"))) {
            RATE_LIMITED.incrementAndGet();
        } else {
            OTHER_DENIALS.incrementAndGet();
        }
        return session;
    });

    private final ScenarioBuilder scenario = scenario("Brute force one key")
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
                    // Denials are 200s too; a non-200 here means the server itself misbehaved.
                    .check(status().is(200))
                    .check(jsonPath("$.decision").saveAs("decision"))
                    .check(jsonPath("$.reason").optional().saveAs("reason")))
            .exec(tally);

    private static final int RAMP_SECONDS = 10;

    {
        setUp(scenario.injectOpen(
                        rampUsersPerSec(1).to(USERS_PER_SEC).during(Duration.ofSeconds(RAMP_SECONDS)),
                        constantUsersPerSec(USERS_PER_SEC).during(Duration.ofSeconds(DURATION_SECONDS))))
                .protocols(httpProtocol)
                .assertions(
                        global().successfulRequests().percent().is(100.0),
                        global().responseTime().percentile(95).lt(500));
    }

    @Override
    public void after() {
        long granted = GRANTED.get();
        long limited = RATE_LIMITED.get();
        long other = OTHER_DENIALS.get();
        long total = granted + limited + other;
        // Upper bound on legitimate grants: the initial burst per key plus every token refilled
        // during the run (+1 per key for boundary timing).
        long runSeconds = RAMP_SECONDS + DURATION_SECONDS;
        long maxGrants = KEY_COUNT * (BUCKET_CAPACITY + runSeconds / REFILL_SECONDS_PER_TOKEN + 1);

        System.out.printf(
                "%nRate limiter stress: %d requests over %d keys -> %d granted, %d RATE_LIMITED, %d other denials "
                        + "(max grants the limiter should allow: %d)%n",
                total, KEY_COUNT, granted, limited, other, maxGrants);

        if (granted > maxGrants) {
            throw new IllegalStateException("Rate limiter leaked: " + granted + " grants exceeds bound " + maxGrants);
        }
        if (limited == 0) {
            throw new IllegalStateException("Nothing was rate limited; the test did not exercise the limiter");
        }
    }
}
