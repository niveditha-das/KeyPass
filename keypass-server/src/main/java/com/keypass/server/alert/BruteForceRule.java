package com.keypass.server.alert;

import com.keypass.server.audit.AuditEventRepository;
import com.keypass.server.audit.EventType;
import com.keypass.server.access.AccessDeniedEvent;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Five or more denials for the same key within 10 minutes looks like brute forcing. */
@Component
public class BruteForceRule {

    private static final int THRESHOLD = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final AuditEventRepository events;
    private final AlertService alerts;
    private final Clock clock;

    public BruteForceRule(AuditEventRepository events, AlertService alerts, Clock clock) {
        this.events = events;
        this.alerts = alerts;
        this.clock = clock;
    }

    public void check(AccessDeniedEvent event) {
        if (event.keyId() == null) {
            return;
        }
        long denials = events.countByKeyIdAndEventTypeAndOccurredAtAfter(
                event.keyId(), EventType.ACCESS_DENIED, clock.instant().minus(WINDOW));
        if (denials >= THRESHOLD) {
            alerts.raise(event.vehicleId(), event.keyId(), AlertType.BRUTE_FORCE,
                    denials + " denied attempts for this key in the last " + WINDOW.toMinutes() + " minutes");
        }
    }
}
