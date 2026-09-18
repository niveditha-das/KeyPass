package com.keypass.server.key;

import com.keypass.server.common.NotFoundException;
import com.keypass.server.vehicle.Vehicle;
import com.keypass.server.vehicle.VehicleRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Centralises "is this user allowed to see or touch this key" so every controller and service
 * enforces it the same way. Callers get a 404, not a 403, for a resource that isn't theirs
 * (ADR 0007) — that way they can't tell whether the resource exists at all.
 */
@Component
public class KeyAuthorization {

    private final VehicleRepository vehicles;

    public KeyAuthorization(VehicleRepository vehicles) {
        this.vehicles = vehicles;
    }

    public boolean isOwner(DigitalKey key, UUID userId) {
        return vehicles.findById(key.getVehicleId())
                .map(v -> v.getOwnerId().equals(userId))
                .orElse(false);
    }

    public boolean isHolder(DigitalKey key, UUID userId) {
        return key.getHolderId().equals(userId);
    }

    public boolean isIssuer(DigitalKey key, UUID userId) {
        return key.getIssuedBy().equals(userId);
    }

    public DigitalKey requireViewable(DigitalKey key, UUID userId) {
        if (isOwner(key, userId) || isHolder(key, userId)) {
            return key;
        }
        throw new NotFoundException();
    }

    public DigitalKey requireOwner(DigitalKey key, UUID userId) {
        if (!isOwner(key, userId)) {
            throw new NotFoundException();
        }
        return key;
    }

    public DigitalKey requireOwnerOrIssuer(DigitalKey key, UUID userId) {
        if (!isOwner(key, userId) && !isIssuer(key, userId)) {
            throw new NotFoundException();
        }
        return key;
    }

    public Vehicle requireOwnedVehicle(String vin, UUID userId) {
        Vehicle vehicle = vehicles.findByVin(vin).orElseThrow(NotFoundException::new);
        if (!vehicle.getOwnerId().equals(userId)) {
            throw new NotFoundException();
        }
        return vehicle;
    }
}
