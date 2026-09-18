package com.keypass.loadtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keypass.common.crypto.Ed25519;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Seeds real data through the real HTTP API before the Gatling injection profile starts: one
 * vehicle, one owner/holder pair and one active UNLOCK key per fixture, so the load test
 * measures the full access-check path (signature verification, nonce insert, row lock, policy
 * evaluation, audit write) rather than an artificially warmed cache of a single key.
 */
final class LoadTestFixtures {

    private LoadTestFixtures() {}

    record Fixture(String vin, String vehicleApiKey, String credential, String devicePrivateKeyBase64) {}

    static List<Fixture> seed(String baseUrl, int count) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        ObjectMapper mapper = new ObjectMapper();
        Random random = new Random();
        List<Fixture> fixtures = new ArrayList<>(count);

        System.out.println("Seeding " + count + " vehicles/keys against " + baseUrl + " ...");
        for (int i = 0; i < count; i++) {
            try {
                fixtures.add(seedOne(http, mapper, random, baseUrl));
            } catch (Exception e) {
                throw new RuntimeException("Seeding failed at fixture " + i, e);
            }
            if ((i + 1) % 50 == 0) {
                System.out.println("  seeded " + (i + 1) + "/" + count);
            }
        }
        System.out.println("Seeding complete.");
        return fixtures;
    }

    private static Fixture seedOne(HttpClient http, ObjectMapper mapper, Random random, String baseUrl) throws Exception {
        String email = "loadtest-" + UUID.randomUUID() + "@example.com";
        String password = "loadtest-password-1!";
        postJson(http, mapper, baseUrl + "/api/v1/auth/register", null,
                Map.of("email", email, "password", password));
        JsonNode login = postJson(http, mapper, baseUrl + "/api/v1/auth/login", null,
                Map.of("email", email, "password", password));
        String token = login.get("accessToken").asText();

        KeyPair deviceKeys = Ed25519.generate();
        JsonNode device = postJson(http, mapper, baseUrl + "/api/v1/devices", token,
                Map.of("publicKey", Ed25519.publicKeyToBase64(deviceKeys.getPublic()), "label", "load-test-phone"));
        String deviceId = device.get("id").asText();

        String vin = randomVin(random);
        JsonNode vehicle = postJson(http, mapper, baseUrl + "/api/v1/vehicles", token,
                Map.of("vin", vin, "model", "Load Test Car", "timeZone", "Europe/Dublin"));
        String apiKey = vehicle.get("apiKey").asText();

        // The holder is the same account as the owner here, for simplicity — decode the user id
        // straight out of the JWT subject rather than making another round trip for it.
        String holderId = mapper.readTree(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]))
                .get("sub").asText();

        Instant now = Instant.now();
        JsonNode key = postJson(http, mapper, baseUrl + "/api/v1/vehicles/" + vin + "/keys", token,
                Map.of(
                        "holderId", holderId,
                        "deviceId", deviceId,
                        "permissions", List.of("UNLOCK"),
                        "notBefore", now.minusSeconds(60).toString(),
                        "notAfter", now.plusSeconds(3600 * 24).toString()));
        String keyId = key.get("id").asText();

        JsonNode credentialResponse = getJson(http, mapper, baseUrl + "/api/v1/keys/" + keyId + "/credential", token);
        String credential = credentialResponse.get("credential").asText();

        return new Fixture(vin, apiKey, credential, Ed25519.privateKeyToBase64(deviceKeys.getPrivate()));
    }

    private static JsonNode postJson(HttpClient http, ObjectMapper mapper, String url, String bearerToken, Object body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("POST " + url + " failed: " + response.statusCode() + " " + response.body());
        }
        return mapper.readTree(response.body());
    }

    private static JsonNode getJson(HttpClient http, ObjectMapper mapper, String url, String bearerToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + bearerToken)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("GET " + url + " failed: " + response.statusCode() + " " + response.body());
        }
        return mapper.readTree(response.body());
    }

    private static String randomVin(Random random) {
        String alphabet = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < 17; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
