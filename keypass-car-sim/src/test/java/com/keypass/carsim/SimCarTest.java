package com.keypass.carsim;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keypass.common.crypto.AccessSigningMessage;
import com.keypass.common.crypto.Ed25519;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SimCarTest {

    @Test
    void vinIsSeventeenCharsAndNeverContainsIOrQ() {
        for (int i = 0; i < 200; i++) {
            assertThat(new SimCar().vin).matches("[ABCDEFGHJKLMNPRSTUVWXYZ0-9]{17}");
        }
    }

    @Test
    void challengesAreUnique() {
        assertThat(SimCar.newChallenge()).isNotEqualTo(SimCar.newChallenge());
    }

    @Test
    void decisionAndReasonDefaultWhenAbsent() throws Exception {
        var node = new ObjectMapper().readTree("{}");

        assertThat(SimCar.decision(node)).isEqualTo("?");
        assertThat(SimCar.reason(node)).isEmpty();
    }

    @Test
    void attemptAccessSendsARequestTheServerCouldVerify() throws Exception {
        var received = new AtomicReference<String>();
        var vehicleKey = new AtomicReference<String>();
        var path = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            vehicleKey.set(exchange.getRequestHeaders().getFirst("X-Vehicle-Key"));
            path.set(exchange.getRequestURI().getPath());
            byte[] out = "{\"decision\":\"GRANTED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        try {
            var api = new ApiClient("http://127.0.0.1:" + server.getAddress().getPort());
            var car = new SimCar();
            car.apiKey = "car-key";
            var deviceKeys = Ed25519.generate();
            Instant now = Instant.parse("2026-01-01T12:00:00Z");

            var response = car.attemptAccess(api, "cred", "UNLOCK", "chal-1", now, deviceKeys.getPrivate());

            assertThat(SimCar.decision(response.body())).isEqualTo("GRANTED");
            assertThat(path.get()).isEqualTo("/api/v1/vehicles/" + car.vin + "/access-checks");
            assertThat(vehicleKey.get()).isEqualTo("car-key");

            var body = new ObjectMapper().readTree(received.get());
            assertThat(body.get("credential").asText()).isEqualTo("cred");
            assertThat(body.get("command").asText()).isEqualTo("UNLOCK");
            byte[] signature = Base64.getDecoder().decode(body.get("deviceSignature").asText());
            byte[] message = AccessSigningMessage.bytes(car.vin, "UNLOCK", "chal-1", now);
            assertThat(Ed25519.verify(deviceKeys.getPublic(), message, signature)).isTrue();
            // Binding the VIN into the signed message: the same signature must not verify for another car.
            byte[] otherCar = AccessSigningMessage.bytes(new SimCar().vin, "UNLOCK", "chal-1", now);
            assertThat(Ed25519.verify(deviceKeys.getPublic(), otherCar, signature)).isFalse();
        } finally {
            server.stop(0);
        }
    }
}
