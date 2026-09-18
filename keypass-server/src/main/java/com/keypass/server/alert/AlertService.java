package com.keypass.server.alert;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AlertService {

    private final AlertRepository alerts;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public AlertService(AlertRepository alerts, Clock clock, MeterRegistry meterRegistry) {
        this.alerts = alerts;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    public Alert raise(UUID vehicleId, UUID keyId, String alertType, String details) {
        Alert alert = new Alert(UUID.randomUUID(), vehicleId, keyId, alertType, details, clock.instant());
        meterRegistry.counter("keypass_alerts_total", "type", alertType).increment();
        return alerts.save(alert);
    }
}
