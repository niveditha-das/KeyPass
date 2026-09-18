package com.keypass.server.key;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DigitalKeyRepository extends JpaRepository<DigitalKey, UUID> {

    /** FOR SHARE: lets concurrent access checks proceed together, but blocks a revocation
     * (which needs FOR UPDATE) until every in-flight check commits. See ADR 0004. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select k from DigitalKey k where k.id = :id")
    Optional<DigitalKey> findByIdForShare(@Param("id") UUID id);

    /** FOR UPDATE: waits for any in-flight FOR SHARE holders, then blocks new ones. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from DigitalKey k where k.id = :id")
    Optional<DigitalKey> findByIdForUpdate(@Param("id") UUID id);

    Optional<DigitalKey> findByIdAndHolderId(UUID id, UUID holderId);

    List<DigitalKey> findByVehicleIdAndStatus(UUID vehicleId, KeyStatus status);

    List<DigitalKey> findByVehicleId(UUID vehicleId);

    List<DigitalKey> findByHolderId(UUID holderId);

    List<DigitalKey> findByParentKeyId(UUID parentKeyId);

    @Query("select k.id from DigitalKey k where k.vehicleId = :vehicleId and k.status = 'REVOKED'")
    List<UUID> findRevokedKeyIds(@Param("vehicleId") UUID vehicleId);

    @Query("select max(k.revocationEpoch) from DigitalKey k where k.vehicleId = :vehicleId")
    Optional<Long> findMaxRevocationEpoch(@Param("vehicleId") UUID vehicleId);

    @Query("select k.id from DigitalKey k where k.vehicleId = :vehicleId and k.revocationEpoch > :sinceEpoch")
    List<UUID> findRevokedSince(@Param("vehicleId") UUID vehicleId, @Param("sinceEpoch") long sinceEpoch);
}
