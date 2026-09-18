package com.keypass.server.vehicle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    Optional<Vehicle> findByVin(String vin);

    List<Vehicle> findByOwnerId(UUID ownerId);

    Optional<Vehicle> findByIdAndOwnerId(UUID id, UUID ownerId);
}
