package com.keypass.server.alert;

import com.keypass.server.access.AccessGrantedEvent;
import com.keypass.server.audit.EventType;
import com.keypass.server.vehicle.Vehicle;
import com.keypass.server.vehicle.VehicleRepository;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Flags a grant that happens at an hour the holder has almost never used this key before, once
 * there's enough history (20+ past grants) to say what "usual" looks like.
 */
@Component
public class UnusualTimeRule {

    private static final int MIN_HISTORY = 20;
    private static final double UNUSUAL_SHARE = 0.02;

    private final JdbcClient jdbc;
    private final VehicleRepository vehicles;
    private final AlertService alerts;

    public UnusualTimeRule(JdbcClient jdbc, VehicleRepository vehicles, AlertService alerts) {
        this.jdbc = jdbc;
        this.vehicles = vehicles;
        this.alerts = alerts;
    }

    public void check(AccessGrantedEvent event) {
        Vehicle vehicle = vehicles.findById(event.vehicleId()).orElse(null);
        if (vehicle == null) {
            return;
        }

        record HourCount(int hr, long n) {}
        List<HourCount> hourly = jdbc.sql("""
                        SELECT extract(hour FROM occurred_at AT TIME ZONE :tz)::int AS hr, count(*) AS n
                        FROM audit_event
                        WHERE key_id = :keyId
                          AND event_type = :grantedType
                          AND occurred_at > now() - interval '30 days'
                        GROUP BY hr
                        """)
                .param("tz", vehicle.getTimeZone())
                .param("keyId", event.keyId())
                .param("grantedType", EventType.ACCESS_GRANTED)
                .query((rs, i) -> new HourCount(rs.getInt("hr"), rs.getLong("n")))
                .list();

        long total = hourly.stream().mapToLong(HourCount::n).sum();
        if (total < MIN_HISTORY) {
            return;
        }

        int currentHour = event.occurredAt().atZone(vehicle.getZone()).getHour();
        long countThisHour = hourly.stream().filter(h -> h.hr() == currentHour).mapToLong(HourCount::n).sum();
        double share = (double) countThisHour / total;

        if (share < UNUSUAL_SHARE) {
            alerts.raise(event.vehicleId(), event.keyId(), AlertType.UNUSUAL_TIME,
                    "Access granted at hour " + currentHour + ", which accounts for only "
                            + Math.round(share * 1000) / 10.0 + "% of this key's history");
        }
    }
}
