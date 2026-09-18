package com.keypass.server.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.keypass.common.model.Permission;
import com.keypass.server.common.NotFoundException;
import com.keypass.server.device.Device;
import com.keypass.server.device.DeviceRepository;
import com.keypass.server.vehicle.Vehicle;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Covers issue(), suspend() and resume() — share() has its own dedicated test class. */
@ExtendWith(MockitoExtension.class)
class KeyServiceTest {

    @Mock
    private DigitalKeyRepository keys;

    @Mock
    private DeviceRepository devices;

    @Mock
    private KeyAuthorization authz;

    private KeyService service;

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-10T12:00:00Z"), ZoneOffset.UTC);
    private final Instant now = clock.instant();

    @BeforeEach
    void setUp() {
        service = new KeyService(keys, devices, authz, clock);
    }

    @Test
    void issueCreatesAnActiveKeyWhenDeviceBelongsToHolder() {
        UUID ownerId = UUID.randomUUID();
        UUID holderId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle(UUID.randomUUID(), "1HGCM82633A123456", ownerId, "Model", "Europe/Dublin", "hash", now);
        Device device = new Device(deviceId, holderId, "pub-key", "phone", now);
        when(devices.findById(deviceId)).thenReturn(Optional.of(device));
        when(keys.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IssueKeyRequest request = new IssueKeyRequest(
                holderId, deviceId, Set.of(Permission.UNLOCK), now, now.plusSeconds(3600), List.of(), null);

        DigitalKey key = service.issue(vehicle, request, ownerId);

        assertThat(key.getStatus()).isEqualTo(KeyStatus.ACTIVE);
        assertThat(key.getHolderId()).isEqualTo(holderId);
        assertThat(key.getVehicleId()).isEqualTo(vehicle.getId());
        assertThat(key.getIssuedBy()).isEqualTo(ownerId);
        assertThat(key.getDepth()).isZero();
        assertThat(key.getParentKeyId()).isNull();
    }

    @Test
    void issueRejectsADeviceThatBelongsToSomeoneElse() {
        UUID ownerId = UUID.randomUUID();
        UUID holderId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle(UUID.randomUUID(), "1HGCM82633A654321", ownerId, "Model", "Europe/Dublin", "hash", now);
        Device someoneElsesDevice = new Device(deviceId, UUID.randomUUID(), "pub-key", "phone", now);
        when(devices.findById(deviceId)).thenReturn(Optional.of(someoneElsesDevice));

        IssueKeyRequest request = new IssueKeyRequest(
                holderId, deviceId, Set.of(Permission.UNLOCK), now, now.plusSeconds(3600), List.of(), null);

        assertThatThrownBy(() -> service.issue(vehicle, request, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("holder");
    }

    @Test
    void issueOnAnUnknownDeviceThrowsNotFound() {
        UUID deviceId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle(UUID.randomUUID(), "1HGCM82633A777888", UUID.randomUUID(), "Model", "Europe/Dublin", "hash", now);
        when(devices.findById(deviceId)).thenReturn(Optional.empty());

        IssueKeyRequest request = new IssueKeyRequest(
                UUID.randomUUID(), deviceId, Set.of(Permission.UNLOCK), now, now.plusSeconds(3600), List.of(), null);

        assertThatThrownBy(() -> service.issue(vehicle, request, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    private DigitalKey activeKey(UUID holderId) {
        return new DigitalKey(
                UUID.randomUUID(), UUID.randomUUID(), holderId, UUID.randomUUID(), UUID.randomUUID(),
                null, 0, now.minusSeconds(60), now.plusSeconds(3600),
                EnumSet.of(Permission.UNLOCK), List.of(), null, now);
    }

    @Test
    void suspendPausesAnActiveKey() {
        UUID ownerId = UUID.randomUUID();
        DigitalKey key = activeKey(UUID.randomUUID());
        when(keys.findByIdForUpdate(key.getId())).thenReturn(Optional.of(key));
        when(keys.save(key)).thenReturn(key);

        DigitalKey suspended = service.suspend(key.getId(), ownerId);

        assertThat(suspended.getStatus()).isEqualTo(KeyStatus.SUSPENDED);
    }

    @Test
    void suspendOnAnUnknownKeyThrowsNotFound() {
        UUID keyId = UUID.randomUUID();
        when(keys.findByIdForUpdate(keyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspend(keyId, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void resumeReactivatesASuspendedKey() {
        UUID ownerId = UUID.randomUUID();
        DigitalKey key = activeKey(UUID.randomUUID());
        key.suspend();
        when(keys.findByIdForUpdate(key.getId())).thenReturn(Optional.of(key));
        when(keys.save(key)).thenReturn(key);

        DigitalKey resumed = service.resume(key.getId(), ownerId);

        assertThat(resumed.getStatus()).isEqualTo(KeyStatus.ACTIVE);
    }
}
