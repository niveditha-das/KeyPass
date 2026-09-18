package com.keypass.carsim;

import com.fasterxml.jackson.databind.JsonNode;
import com.keypass.common.crypto.Ed25519;
import java.security.KeyPair;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Drives the KeyPass API through the attack and edge-case scenarios described in the build
 * guide. Run against a live server (defaults to http://localhost:8080):
 *
 *   mvn -pl keypass-car-sim -am exec:java -Dexec.args="http://localhost:8080"
 */
public class Main {

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : "http://localhost:8080";
        ApiClient api = new ApiClient(baseUrl);

        List<ScenarioResult> results = new ArrayList<>();
        results.add(run("Normal unlock is granted", () -> normalUnlock(api)));
        results.add(run("Expired key is denied", () -> expiredKey(api)));
        results.add(run("Revoked key is denied", () -> revokedKey(api)));
        results.add(run("Replayed request is denied", () -> replayAttack(api)));
        results.add(run("Stolen credential is useless without the device key", () -> stolenCredential(api)));
        results.add(run("Tampered credential is rejected", () -> tamperedCredential(api)));
        results.add(run("Geofence breach is denied", () -> geofenceBreach(api)));
        results.add(run("Curfew blocks engine start", () -> curfew(api)));
        results.add(run("Brute force is rate limited", () -> bruteForce(api)));

        printSummary(results);
    }

    private static ScenarioResult run(String name, ScenarioBody body) {
        try {
            return body.run();
        } catch (Exception e) {
            return ScenarioResult.fail(name, "Threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private interface ScenarioBody {
        ScenarioResult run() throws Exception;
    }

    private static ScenarioResult normalUnlock(ApiClient api) throws Exception {
        Fixtures f = new Fixtures(api);
        f.issueStandardActiveKey(List.of("UNLOCK"));
        var response = f.car.attemptAccess(api, f.credential, "UNLOCK", SimCar.newChallenge(), Instant.now(), f.holder.deviceKeys.getPrivate());
        return expect("Normal unlock is granted", response, "GRANTED", null);
    }

    private static ScenarioResult expiredKey(ApiClient api) throws Exception {
        // The API refuses to issue an already-expired key (notAfter must be in the future), so
        // this issues one that's valid for only two seconds and waits for it to lapse naturally
        // — closer to what really happens than trying to forge an expired key directly.
        Fixtures f = new Fixtures(api);
        Instant now = Instant.now();
        f.keyId = f.issueKey(List.of("UNLOCK"), now.minusSeconds(60), now.plusSeconds(4), List.of(), null);
        f.credential = f.holder.fetchCredential(api, f.keyId).get("credential").asText();
        Thread.sleep(5000);
        var response = f.car.attemptAccess(api, f.credential, "UNLOCK", SimCar.newChallenge(), Instant.now(), f.holder.deviceKeys.getPrivate());
        return expect("Expired key is denied", response, "DENIED", "VALIDITY");
    }

    private static ScenarioResult revokedKey(ApiClient api) throws Exception {
        Fixtures f = new Fixtures(api);
        f.issueStandardActiveKey(List.of("UNLOCK"));
        var revoke = api.post("/api/v1/keys/" + f.keyId + "/revoke", null, f.owner.accessToken);
        if (!revoke.isSuccess()) {
            return ScenarioResult.fail("Revoked key is denied", "Revoke call failed: " + revoke.status());
        }
        var response = f.car.attemptAccess(api, f.credential, "UNLOCK", SimCar.newChallenge(), Instant.now(), f.holder.deviceKeys.getPrivate());
        return expect("Revoked key is denied", response, "DENIED", "STATUS");
    }

    private static ScenarioResult replayAttack(ApiClient api) throws Exception {
        Fixtures f = new Fixtures(api);
        f.issueStandardActiveKey(List.of("UNLOCK"));
        String challenge = SimCar.newChallenge();
        Instant now = Instant.now();
        var first = f.car.attemptAccess(api, f.credential, "UNLOCK", challenge, now, f.holder.deviceKeys.getPrivate());
        var replay = f.car.attemptAccess(api, f.credential, "UNLOCK", challenge, now, f.holder.deviceKeys.getPrivate());
        if (!"GRANTED".equals(SimCar.decision(first.body()))) {
            return ScenarioResult.fail("Replayed request is denied", "First request wasn't granted: " + first.body());
        }
        return expect("Replayed request is denied", replay, "DENIED", "REPLAY_DETECTED");
    }

    private static ScenarioResult stolenCredential(ApiClient api) throws Exception {
        Fixtures f = new Fixtures(api);
        f.issueStandardActiveKey(List.of("UNLOCK"));
        KeyPair attackerKeys = Ed25519.generate();
        var response = f.car.attemptAccess(api, f.credential, "UNLOCK", SimCar.newChallenge(), Instant.now(), attackerKeys.getPrivate());
        return expect("Stolen credential is useless without the device key", response, "DENIED", "BAD_DEVICE_SIGNATURE");
    }

    private static ScenarioResult tamperedCredential(ApiClient api) throws Exception {
        Fixtures f = new Fixtures(api);
        f.issueStandardActiveKey(List.of("UNLOCK"));
        String[] parts = f.credential.split("\\.");
        String tampered = parts[0].substring(0, parts[0].length() - 4) + "AAAA" + "." + parts[1];
        var response = f.car.attemptAccess(api, tampered, "UNLOCK", SimCar.newChallenge(), Instant.now(), f.holder.deviceKeys.getPrivate());
        return expect("Tampered credential is rejected", response, "DENIED", "INVALID_CREDENTIAL");
    }

    private static ScenarioResult geofenceBreach(ApiClient api) throws Exception {
        Fixtures f = new Fixtures(api);
        Instant now = Instant.now();
        var geofence = Map.of(
                "type", "GEOFENCE",
                "polygon", List.of(
                        Map.of("lat", 0, "lon", 0), Map.of("lat", 0, "lon", 1),
                        Map.of("lat", 1, "lon", 1), Map.of("lat", 1, "lon", 0)));
        f.keyId = f.issueKey(List.of("UNLOCK"), now.minusSeconds(60), now.plusSeconds(3600), List.of(geofence), null);
        f.credential = f.holder.fetchCredential(api, f.keyId).get("credential").asText();
        var response = f.car.attemptAccess(api, f.credential, "UNLOCK", SimCar.newChallenge(), now, f.holder.deviceKeys.getPrivate());
        return expect("Geofence breach is denied", response, "DENIED", "GEOFENCE");
    }

    private static ScenarioResult curfew(ApiClient api) throws Exception {
        Fixtures f = new Fixtures(api);
        Instant now = Instant.now();
        // An all-day curfew makes the scenario deterministic regardless of when it runs.
        var curfewRule = Map.of("type", "CURFEW", "start", "00:00:00", "end", "23:59:59");
        f.keyId = f.issueKey(List.of("START_ENGINE"), now.minusSeconds(60), now.plusSeconds(3600), List.of(curfewRule), null);
        f.credential = f.holder.fetchCredential(api, f.keyId).get("credential").asText();
        var response = f.car.attemptAccess(api, f.credential, "START_ENGINE", SimCar.newChallenge(), now, f.holder.deviceKeys.getPrivate());
        return expect("Curfew blocks engine start", response, "DENIED", "CURFEW");
    }

    private static ScenarioResult bruteForce(ApiClient api) throws Exception {
        Fixtures f = new Fixtures(api);
        f.issueStandardActiveKey(List.of("UNLOCK"));
        KeyPair attackerKeys = Ed25519.generate(); // every attempt fails signature check, but still consumes the rate limit
        JsonNode last = null;
        for (int i = 0; i < 15; i++) {
            last = f.car.attemptAccess(api, f.credential, "UNLOCK", SimCar.newChallenge(), Instant.now(), attackerKeys.getPrivate()).body();
        }
        return expect("Brute force is rate limited", new ApiClient.ApiResponse(200, last), "DENIED", "RATE_LIMITED");
    }

    private static ScenarioResult expect(String name, ApiClient.ApiResponse response, String expectedDecision, String expectedReason) {
        String decision = SimCar.decision(response.body());
        String reason = SimCar.reason(response.body());
        boolean decisionOk = expectedDecision.equals(decision);
        boolean reasonOk = expectedReason == null || expectedReason.equals(reason);
        if (decisionOk && reasonOk) {
            return ScenarioResult.pass(name, "decision=" + decision + (reason.isEmpty() ? "" : ", reason=" + reason));
        }
        return ScenarioResult.fail(name, "expected decision=" + expectedDecision
                + (expectedReason == null ? "" : " reason=" + expectedReason)
                + " but got decision=" + decision + " reason=" + reason);
    }

    private static void printSummary(List<ScenarioResult> results) {
        System.out.println();
        System.out.println("KeyPass car simulator — scenario results");
        System.out.println("=========================================");
        long passed = results.stream().filter(ScenarioResult::passed).count();
        for (ScenarioResult r : results) {
            System.out.printf("[%s] %s%n", r.passed() ? "PASS" : "FAIL", r.name());
            System.out.println("       " + r.detail());
        }
        System.out.println();
        System.out.printf("%d/%d scenarios passed%n", passed, results.size());
        if (passed != results.size()) {
            System.exit(1);
        }
    }
}
