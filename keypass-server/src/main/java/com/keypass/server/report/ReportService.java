package com.keypass.server.report;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final JdbcClient jdbc;

    public ReportService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<UsageReportRow> usageReport(UUID vehicleId, Instant monthStart, Instant monthEnd) {
        return jdbc.sql("""
                        SELECT u.email AS holder_email,
                               count(*) FILTER (WHERE a.event_type = 'ACCESS_GRANTED') AS grants,
                               count(*) FILTER (WHERE a.event_type = 'ACCESS_DENIED')  AS denials,
                               max(a.occurred_at)                                      AS last_used
                        FROM audit_event a
                        JOIN digital_key k ON k.id = a.key_id
                        JOIN app_user u    ON u.id = k.holder_id
                        WHERE a.vehicle_id = :vehicleId
                          AND a.occurred_at >= :monthStart AND a.occurred_at < :monthEnd
                        GROUP BY u.email
                        ORDER BY grants DESC
                        """)
                .param("vehicleId", vehicleId)
                .param("monthStart", Timestamp.from(monthStart))
                .param("monthEnd", Timestamp.from(monthEnd))
                .query((rs, i) -> new UsageReportRow(
                        rs.getString("holder_email"), rs.getLong("grants"), rs.getLong("denials"),
                        rs.getTimestamp("last_used") == null ? null : rs.getTimestamp("last_used").toInstant()))
                .list();
    }

    public List<DenialReasonRow> denialReasons(UUID vehicleId) {
        return jdbc.sql("""
                        SELECT reason, count(*) AS n,
                               round(100.0 * count(*) / sum(count(*)) OVER (), 1) AS pct
                        FROM audit_event
                        WHERE vehicle_id = :vehicleId AND event_type = 'ACCESS_DENIED'
                        GROUP BY reason
                        ORDER BY n DESC
                        """)
                .param("vehicleId", vehicleId)
                .query((rs, i) -> new DenialReasonRow(rs.getString("reason"), rs.getLong("n"), rs.getDouble("pct")))
                .list();
    }
}
