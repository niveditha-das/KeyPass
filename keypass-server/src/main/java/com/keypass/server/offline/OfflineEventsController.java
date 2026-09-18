package com.keypass.server.offline;

import com.keypass.server.alert.AlertService;
import com.keypass.server.alert.AlertType;
import com.keypass.server.audit.AuditService;
import com.keypass.server.key.DigitalKey;
import com.keypass.server.key.DigitalKeyRepository;
import com.keypass.server.vehicle.Vehicle;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cars upload the decisions they made offline once they reconnect. Each one is re-checked
 * against the server's records: if a key was used after its revocation had already happened,
 * that's evidence the offline bundle was stale when the car made the call, and it's worth an
 * alert even though the car behaved correctly given what it knew at the time.
 */
@RestController
@RequestMapping("/api/v1/vehicles/{vin}/offline-events")
public class OfflineEventsController {

    private final DigitalKeyRepository keys;
    private final AuditService audit;
    private final AlertService alertService;

    public OfflineEventsController(DigitalKeyRepository keys, AuditService audit, AlertService alertService) {
        this.keys = keys;
        this.audit = audit;
        this.alertService = alertService;
    }

    @PostMapping
    @Transactional
    public void upload(
            @AuthenticationPrincipal Vehicle vehicle, @PathVariable String vin, @RequestBody List<OfflineEventRequest> events) {
        for (OfflineEventRequest event : events) {
            audit.offlineAccessUploaded(vehicle.getId(), event.keyId(), event.command(), event.granted(), event.reason(), event.trace());

            if (event.granted() && event.keyId() != null) {
                DigitalKey key = keys.findById(event.keyId()).orElse(null);
                if (key != null && key.getRevokedAt() != null && key.getRevokedAt().isBefore(event.occurredAt())) {
                    alertService.raise(vehicle.getId(), event.keyId(), AlertType.OFFLINE_USE_AFTER_REVOKE,
                            "Key was used offline at " + event.occurredAt() + " but had already been revoked at "
                                    + key.getRevokedAt());
                }
            }
        }
    }
}
