package com.keypass.server.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keypass.server.IntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

class AuditAppendOnlyIT extends IntegrationTest {

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void updatingAnAuditRowIsRejectedByTheDatabase() {
        AuditEvent event = events.save(new AuditEvent(
                Instant.now(), UUID.randomUUID(), UUID.randomUUID(), null,
                EventType.ACCESS_GRANTED, "UNLOCK", "ok", null, null, null));

        assertThatThrownBy(() -> jdbc.sql("UPDATE audit_event SET reason = 'tampered' WHERE id = :id")
                .param("id", event.getId())
                .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void deletingAnAuditRowIsRejectedByTheDatabase() {
        AuditEvent event = events.save(new AuditEvent(
                Instant.now(), UUID.randomUUID(), UUID.randomUUID(), null,
                EventType.ACCESS_DENIED, "LOCK", "TEST", null, null, null));

        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit_event WHERE id = :id")
                .param("id", event.getId())
                .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }
}
