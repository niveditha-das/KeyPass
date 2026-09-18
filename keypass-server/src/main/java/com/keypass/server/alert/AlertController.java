package com.keypass.server.alert;

import com.keypass.server.common.CurrentUser;
import com.keypass.server.common.NotFoundException;
import com.keypass.server.vehicle.Vehicle;
import com.keypass.server.vehicle.VehicleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertRepository alerts;
    private final VehicleRepository vehicles;

    public AlertController(AlertRepository alerts, VehicleRepository vehicles) {
        this.alerts = alerts;
        this.vehicles = vehicles;
    }

    @GetMapping
    public List<AlertResponse> list(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = CurrentUser.idOf(jwt);
        List<UUID> ownedVehicleIds = vehicles.findByOwnerId(userId).stream().map(Vehicle::getId).toList();
        return alerts.findByVehicleIdIn(ownedVehicleIds).stream().map(AlertResponse::from).toList();
    }

    @PostMapping("/{id}/ack")
    public AlertResponse acknowledge(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = CurrentUser.idOf(jwt);
        Alert alert = alerts.findById(id).orElseThrow(NotFoundException::new);
        Vehicle vehicle = vehicles.findById(alert.getVehicleId()).orElseThrow(NotFoundException::new);
        if (!vehicle.getOwnerId().equals(userId)) {
            throw new NotFoundException();
        }
        alert.acknowledge();
        alerts.save(alert);
        return AlertResponse.from(alert);
    }
}
