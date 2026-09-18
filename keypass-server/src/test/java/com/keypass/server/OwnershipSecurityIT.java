package com.keypass.server;

import com.keypass.common.crypto.Ed25519;
import com.keypass.common.model.Permission;
import com.keypass.server.auth.AppUser;
import com.keypass.server.auth.JwtService;
import com.keypass.server.device.Device;
import com.keypass.server.key.DigitalKey;
import com.keypass.server.vehicle.Vehicle;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * User B must never be able to see or change User A's resources by simply guessing/changing an
 * ID (IDOR). Every one of these calls must come back 404, never 403 — a 403 would confirm the
 * resource exists at all (ADR 0007).
 */
class OwnershipSecurityIT extends IntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private Clock clock;

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void userBCannotSeeOrChangeUserAsVehicleOrKeys() throws Exception {
        AppUser userA = data.user("a-" + UUID.randomUUID() + "@example.com");
        AppUser userB = data.user("b-" + UUID.randomUUID() + "@example.com");
        Vehicle vehicleA = data.vehicle(userA, "1HGCM82633A" + String.format("%06d", System.nanoTime() % 1_000_000));
        Device deviceA = data.device(userA, Ed25519.generate());
        Instant now = clock.instant();
        DigitalKey keyA = data.key(vehicleA, userA, deviceA, userA, Set.of(Permission.UNLOCK),
                now.minusSeconds(60), now.plusSeconds(3600));

        String tokenB = jwtService.issueToken(userB).accessToken();
        RestTestClient rest = client();

        rest.get().uri("/api/v1/vehicles/{vin}", vehicleA.getVin())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);

        rest.get().uri("/api/v1/keys/{id}", keyA.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);

        rest.post().uri("/api/v1/keys/{id}/suspend", keyA.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);

        rest.post().uri("/api/v1/keys/{id}/revoke", keyA.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);

        rest.get().uri("/api/v1/vehicles/{vin}/keys", vehicleA.getVin())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);

        rest.get().uri("/api/v1/vehicles/{vin}/audit", vehicleA.getVin())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        client().get().uri("/api/v1/keys/mine")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
