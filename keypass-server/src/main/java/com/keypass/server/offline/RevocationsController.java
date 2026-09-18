package com.keypass.server.offline;

import com.keypass.server.key.DigitalKeyRepository;
import com.keypass.server.vehicle.Vehicle;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lets a car pull just the revocations it's missed since its last sync, instead of a full bundle. */
@RestController
@RequestMapping("/api/v1/vehicles/{vin}/revocations")
public class RevocationsController {

    private final DigitalKeyRepository keys;

    public RevocationsController(DigitalKeyRepository keys) {
        this.keys = keys;
    }

    @GetMapping
    public List<UUID> since(
            @AuthenticationPrincipal Vehicle vehicle, @PathVariable String vin,
            @RequestParam(defaultValue = "0") long sinceEpoch) {
        return keys.findRevokedSince(vehicle.getId(), sinceEpoch);
    }
}
