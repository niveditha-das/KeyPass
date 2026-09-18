package com.keypass.carsim;

import com.fasterxml.jackson.databind.JsonNode;
import com.keypass.common.crypto.AccessSigningMessage;
import com.keypass.common.crypto.Ed25519;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;

/** A registered car: a VIN and the API key it authenticates with. */
public class SimCar {

    private static final Random RANDOM = new Random();

    public final String vin;
    public String apiKey;

    public SimCar() {
        this.vin = randomVin();
    }

    public void register(ApiClient api, String ownerToken) {
        var response = api.post("/api/v1/vehicles",
                ApiClient.map("vin", vin, "model", "KeyPass Simulator", "timeZone", "Europe/Dublin"), ownerToken);
        if (!response.isSuccess()) {
            throw new IllegalStateException("Vehicle registration failed: " + response.status() + " " + response.body());
        }
        apiKey = response.body().get("apiKey").asText();
    }

    /** Builds a signed access request the way a real phone would, then sends it as this car. */
    public ApiClient.ApiResponse attemptAccess(
            ApiClient api, String credential, String command, String challenge, Instant timestamp, PrivateKey deviceKey) {
        try {
            byte[] message = AccessSigningMessage.bytes(vin, command, challenge, timestamp);
            byte[] signature = Ed25519.sign(deviceKey, message);
            String signatureB64 = Base64.getEncoder().encodeToString(signature);

            var body = ApiClient.map(
                    "credential", credential,
                    "command", command,
                    "challenge", challenge,
                    "timestamp", timestamp.toString(),
                    "deviceSignature", signatureB64);
            return api.post("/api/v1/vehicles/" + vin + "/access-checks", body, null, apiKey);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String newChallenge() {
        return UUID.randomUUID().toString();
    }

    private static String randomVin() {
        String alphabet = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789"; // no I, O, Q
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < 17; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    public static String decision(JsonNode response) {
        return response.path("decision").asText("?");
    }

    public static String reason(JsonNode response) {
        return response.path("reason").asText("");
    }
}
