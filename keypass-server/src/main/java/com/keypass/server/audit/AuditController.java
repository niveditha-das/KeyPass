package com.keypass.server.audit;

import com.keypass.server.common.CurrentUser;
import com.keypass.server.common.NotFoundException;
import com.keypass.server.vehicle.Vehicle;
import com.keypass.server.vehicle.VehicleRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles/{vin}/audit")
public class AuditController {

    private final VehicleRepository vehicles;
    private final AuditEventRepository events;

    public AuditController(VehicleRepository vehicles, AuditEventRepository events) {
        this.vehicles = vehicles;
        this.events = events;
    }

    @GetMapping
    public Page<AuditEventResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String vin,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID userId = CurrentUser.idOf(jwt);
        Vehicle vehicle = vehicles.findByVin(vin)
                .filter(v -> v.getOwnerId().equals(userId))
                .orElseThrow(NotFoundException::new);
        Instant rangeFrom = from == null ? Instant.EPOCH : from;
        Instant rangeTo = to == null ? Instant.now() : to;
        return events.findByVehicleIdAndOccurredAtBetween(vehicle.getId(), rangeFrom, rangeTo, PageRequest.of(page, size))
                .map(AuditEventResponse::from);
    }
}
