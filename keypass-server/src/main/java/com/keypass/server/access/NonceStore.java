package com.keypass.server.access;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

/**
 * Replay protection: a challenge can be consumed for a given vehicle exactly once. Uses
 * INSERT ... ON CONFLICT DO NOTHING rather than catching a duplicate-key exception, because in
 * PostgreSQL a failed statement aborts the whole transaction — which would prevent writing the
 * "denied" audit row afterwards.
 */
@Repository
public class NonceStore {

    private final JdbcClient jdbc;

    public NonceStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns true if the nonce was new, false if it has been seen before for this vehicle. */
    public boolean consume(UUID vehicleId, String nonce, Instant expiresAt) {
        int inserted = jdbc.sql("""
                        INSERT INTO access_nonce (vehicle_id, nonce, expires_at)
                        VALUES (:v, :n, :e)
                        ON CONFLICT DO NOTHING
                        """)
                .param("v", vehicleId)
                .param("n", nonce)
                .param("e", Timestamp.from(expiresAt))
                .update();
        return inserted == 1;
    }

    @Scheduled(fixedDelayString = "PT5M")
    public void purgeExpired() {
        jdbc.sql("DELETE FROM access_nonce WHERE expires_at < now()").update();
    }
}
