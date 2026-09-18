package com.keypass.server.alert;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findByVehicleIdIn(List<UUID> vehicleIds);
}
