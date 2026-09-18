package com.keypass.carsim;

import com.fasterxml.jackson.databind.JsonNode;
import com.keypass.common.crypto.Ed25519;
import java.security.KeyPair;
import java.util.UUID;

/** A registered user plus one phone (device key pair) — what a real KeyPass app user looks like. */
public class SimPhone {

    public final String email;
    public final String password = "password123!";
    public final KeyPair deviceKeys;

    public String userId;
    public String accessToken;
    public String deviceId;

    public SimPhone(String label) throws Exception {
        this.email = label + "-" + UUID.randomUUID() + "@example.com";
        this.deviceKeys = Ed25519.generate();
    }

    public void registerAndLogin(ApiClient api) {
        var register = api.post("/api/v1/auth/register", ApiClient.map("email", email, "password", password), null);
        if (!register.isSuccess()) {
            throw new IllegalStateException("Register failed: " + register.status() + " " + register.body());
        }
        userId = register.body().get("id").asText();

        var login = api.post("/api/v1/auth/login", ApiClient.map("email", email, "password", password), null);
        if (!login.isSuccess()) {
            throw new IllegalStateException("Login failed: " + login.status() + " " + login.body());
        }
        accessToken = login.body().get("accessToken").asText();

        String publicKeyB64 = Ed25519.publicKeyToBase64(deviceKeys.getPublic());
        var device = api.post("/api/v1/devices", ApiClient.map("publicKey", publicKeyB64, "label", "sim-phone"), accessToken);
        if (!device.isSuccess()) {
            throw new IllegalStateException("Device registration failed: " + device.status() + " " + device.body());
        }
        deviceId = device.body().get("id").asText();
    }

    public JsonNode fetchCredential(ApiClient api, String keyId) {
        var response = api.get("/api/v1/keys/" + keyId + "/credential", accessToken);
        if (!response.isSuccess()) {
            throw new IllegalStateException("Fetching credential failed: " + response.status() + " " + response.body());
        }
        return response.body();
    }
}
