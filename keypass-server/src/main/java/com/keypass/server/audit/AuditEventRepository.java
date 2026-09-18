package com.keypass.server.audit;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findByVehicleIdAndOccurredAtBetween(
            UUID vehicleId, Instant from, Instant to, Pageable pageable);

    long countByKeyIdAndEventTypeAndOccurredAtAfter(UUID keyId, String eventType, Instant since);

    @Query("select min(a.id) from AuditEvent a where a.keyId = :keyId and a.eventType = :eventType")
    Optional<Long> findFirstIdByKeyIdAndEventType(@Param("keyId") UUID keyId, @Param("eventType") String eventType);

    long countByKeyIdAndEventTypeAndIdGreaterThan(UUID keyId, String eventType, Long id);
}
