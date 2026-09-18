package com.keypass.server.alert;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AlertService {

    private final AlertRepository alerts;
    private final Clock clock;

    public AlertService(AlertRepository alerts, Clock clock) {
        this.alerts = alerts;
        this.clock = clock;
    }

    public Alert raise(UUID vehicleId, UUID keyId, String alertType, String details) {
        Alert alert = new Alert(UUID.randomUUID(), vehicleId, keyId, alertType, details, clock.instant());
        return alerts.save(alert);
    }
}
