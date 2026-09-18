package com.keypass.server.vehicle;

import com.keypass.server.common.ConflictException;
import com.keypass.server.common.CurrentUser;
import com.keypass.server.common.NotFoundException;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VehicleRepository vehicles;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public VehicleController(VehicleRepository vehicles, PasswordEncoder passwordEncoder, Clock clock) {
        this.vehicles = vehicles;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<VehicleCreatedResponse> register(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateVehicleRequest request) {
        if (vehicles.findByVin(request.vin()).isPresent()) {
            throw new ConflictException("VIN already registered");
        }
        UUID ownerId = CurrentUser.idOf(jwt);
        String apiKey = generateApiKey();
        String timeZone = request.timeZone() == null || request.timeZone().isBlank()
                ? "Europe/Dublin" : request.timeZone();

        Vehicle vehicle = new Vehicle(
                UUID.randomUUID(), request.vin(), ownerId, request.model(), timeZone,
                passwordEncoder.encode(apiKey), clock.instant());
        vehicles.save(vehicle);
        return ResponseEntity.status(HttpStatus.CREATED).body(VehicleCreatedResponse.of(vehicle, apiKey));
    }

    @GetMapping
    public List<VehicleResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = CurrentUser.idOf(jwt);
        return vehicles.findByOwnerId(ownerId).stream().map(VehicleResponse::from).toList();
    }

    @GetMapping("/{vin}")
    public VehicleResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String vin) {
        UUID ownerId = CurrentUser.idOf(jwt);
        Vehicle vehicle = vehicles.findByVin(vin)
                .filter(v -> v.getOwnerId().equals(ownerId))
                .orElseThrow(NotFoundException::new);
        return VehicleResponse.from(vehicle);
    }

    private static String generateApiKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
