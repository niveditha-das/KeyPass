package com.keypass.server.key;

import com.keypass.server.common.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Issuing and listing keys for a specific vehicle. Only the owner may do either. */
@RestController
@RequestMapping("/api/v1/vehicles/{vin}/keys")
public class VehicleKeyController {

    private final KeyService keyService;
    private final KeyAuthorization authz;
    private final DigitalKeyRepository keys;

    public VehicleKeyController(KeyService keyService, KeyAuthorization authz, DigitalKeyRepository keys) {
        this.keyService = keyService;
        this.authz = authz;
        this.keys = keys;
    }

    @PostMapping
    public ResponseEntity<KeyResponse> issue(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String vin, @jakarta.validation.Valid @RequestBody IssueKeyRequest request) {
        UUID actorId = CurrentUser.idOf(jwt);
        var vehicle = authz.requireOwnedVehicle(vin, actorId);
        DigitalKey key = keyService.issue(vehicle, request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(KeyResponse.from(key));
    }

    @GetMapping
    public List<KeyResponse> list(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String vin,
            @RequestParam(required = false) KeyStatus status) {
        UUID actorId = CurrentUser.idOf(jwt);
        var vehicle = authz.requireOwnedVehicle(vin, actorId);
        List<DigitalKey> result = status == null
                ? keys.findByVehicleId(vehicle.getId())
                : keys.findByVehicleIdAndStatus(vehicle.getId(), status);
        return result.stream().map(KeyResponse::from).toList();
    }
}
