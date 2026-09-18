package com.keypass.server.revocation;

import com.keypass.server.audit.AuditService;
import com.keypass.server.common.NotFoundException;
import com.keypass.server.key.DigitalKey;
import com.keypass.server.key.DigitalKeyRepository;
import com.keypass.server.key.KeyAuthorization;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revoking a key revokes every key ever shared from it, computed with a recursive CTE in one
 * statement. The root key is locked FOR UPDATE first; any access check already holding the
 * root's FOR SHARE lock finishes and writes its audit row before this can proceed, and any
 * check that starts afterwards reads REVOKED — see ADR 0004 and RevocationConcurrencyIT.
 */
@Service
public class KeyRevocationService {

    private final JdbcClient jdbc;
    private final DigitalKeyRepository keys;
    private final KeyAuthorization authz;
    private final AuditService audit;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public KeyRevocationService(
            JdbcClient jdbc, DigitalKeyRepository keys, KeyAuthorization authz, AuditService audit, Clock clock,
            MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.keys = keys;
        this.authz = authz;
        this.audit = audit;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public List<UUID> revoke(UUID keyId, UUID actorId) {
        DigitalKey root = keys.findByIdForUpdate(keyId).orElseThrow(NotFoundException::new);
        authz.requireOwnerOrIssuer(root, actorId);

        List<UUID> revoked = jdbc.sql("""
                        WITH RECURSIVE tree AS (
                            SELECT id FROM digital_key WHERE id = :root
                            UNION ALL
                            SELECT k.id FROM digital_key k JOIN tree t ON k.parent_key_id = t.id
                        )
                        UPDATE digital_key
                        SET status = 'REVOKED',
                            revoked_at = :now,
                            revocation_epoch = nextval('revocation_epoch_seq'),
                            version = version + 1
                        WHERE id IN (SELECT id FROM tree) AND status <> 'REVOKED'
                        RETURNING id
                        """)
                .param("root", keyId)
                .param("now", Timestamp.from(clock.instant()))
                .query(UUID.class)
                .list();

        revoked.forEach(id -> audit.keyRevoked(root.getVehicleId(), id, actorId));
        meterRegistry.counter("keypass_revocations_total").increment(revoked.size());
        return revoked;
    }
}
