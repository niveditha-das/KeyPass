package com.keypass.server.device;

import com.keypass.server.common.CurrentUser;
import com.keypass.server.common.NotFoundException;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceRepository devices;
    private final Clock clock;

    public DeviceController(DeviceRepository devices, Clock clock) {
        this.devices = devices;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<DeviceResponse> register(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterDeviceRequest request) {
        UUID userId = CurrentUser.idOf(jwt);
        Device device = new Device(UUID.randomUUID(), userId, request.publicKey(), request.label(), clock.instant());
        devices.save(device);
        return ResponseEntity.status(HttpStatus.CREATED).body(DeviceResponse.from(device));
    }

    @GetMapping
    public List<DeviceResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = CurrentUser.idOf(jwt);
        return devices.findByUserId(userId).stream().map(DeviceResponse::from).toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = CurrentUser.idOf(jwt);
        Device device = devices.findByIdAndUserId(id, userId).orElseThrow(NotFoundException::new);
        devices.delete(device);
        return ResponseEntity.noContent().build();
    }
}
