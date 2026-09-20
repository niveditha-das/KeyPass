package com.keypass.carsim;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiClientTest {

    private HttpServer server;
    private ApiClient client;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastVehicleKey = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastVehicleKey.set(exchange.getRequestHeaders().getFirst("X-Vehicle-Key"));
            byte[] out = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.createContext("/empty", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/denied", exchange -> {
            byte[] out = "{\"decision\":\"DENIED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        client = new ApiClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postSendsJsonBodyAndBearerToken() {
        var response = client.post("/echo", ApiClient.map("a", 1, "b", "two"), "tok");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.body().get("ok").asBoolean()).isTrue();
        assertThat(lastBody.get()).isEqualTo("{\"a\":1,\"b\":\"two\"}");
        assertThat(lastAuth.get()).isEqualTo("Bearer tok");
        assertThat(lastVehicleKey.get()).isNull();
    }

    @Test
    void postWithVehicleKeySendsTheHeader() {
        client.post("/echo", ApiClient.map("x", 1), null, "car-secret");

        assertThat(lastVehicleKey.get()).isEqualTo("car-secret");
        assertThat(lastAuth.get()).isNull();
    }

    @Test
    void emptyResponseBodyBecomesAnEmptyObject() {
        var response = client.get("/empty", null);

        assertThat(response.status()).isEqualTo(204);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.body().isEmpty()).isTrue();
    }

    @Test
    void nonSuccessStatusIsReportedNotThrown() {
        var response = client.post("/denied", ApiClient.map(), null);

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.isSuccess()).isFalse();
        assertThat(SimCar.decision(response.body())).isEqualTo("DENIED");
    }

    @Test
    void mapPreservesInsertionOrder() {
        assertThat(ApiClient.map("z", 1, "a", 2).keySet()).containsExactly("z", "a");
    }
}
