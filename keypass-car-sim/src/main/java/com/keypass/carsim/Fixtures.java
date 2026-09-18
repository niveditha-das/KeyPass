package com.keypass.carsim;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/** Everything most scenarios need: an owner, a holder with a phone, a car, and one active key. */
public class Fixtures {

    public final ApiClient api;
    public final SimPhone owner;
    public final SimPhone holder;
    public final SimCar car;
    public String keyId;
    public String credential;

    public Fixtures(ApiClient api) throws Exception {
        this.api = api;
        this.owner = new SimPhone("owner");
        this.owner.registerAndLogin(api);
        this.holder = new SimPhone("holder");
        this.holder.registerAndLogin(api);
        this.car = new SimCar();
        this.car.register(api, owner.accessToken);
    }

    public String issueKey(List<String> permissions, Instant notBefore, Instant notAfter, List<Object> policy, Integer maxSpeedKmh) {
        var body = ApiClient.map(
                "holderId", holder.userId,
                "deviceId", holder.deviceId,
                "permissions", permissions,
                "notBefore", notBefore.toString(),
                "notAfter", notAfter.toString(),
                "policy", policy,
                "maxSpeedKmh", maxSpeedKmh);
        var response = api.post("/api/v1/vehicles/" + car.vin + "/keys", body, owner.accessToken);
        if (!response.isSuccess()) {
            throw new IllegalStateException("Key issuance failed: " + response.status() + " " + response.body());
        }
        return response.body().get("id").asText();
    }

    public void issueStandardActiveKey(List<String> permissions) {
        Instant now = Instant.now();
        this.keyId = issueKey(permissions, now.minusSeconds(60), now.plusSeconds(3600), List.of(), null);
        JsonNode credNode = holder.fetchCredential(api, keyId);
        this.credential = credNode.get("credential").asText();
    }
}
