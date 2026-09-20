package com.keypass.server.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.keypass.common.model.Permission;
import com.keypass.server.device.Device;
import com.keypass.server.device.DeviceRepository;
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

/** One test per delegation rule: a holder can only ever narrow what they
 * pass on, never widen it. */
@ExtendWith(MockitoExtension.class)
class KeyShareServiceTest {

    @Mock
    private DigitalKeyRepository keys;

    @Mock
    private DeviceRepository devices;

    @Mock
    private KeyAuthorization authz;

    private KeyService service;

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-10T12:00:00Z"), ZoneOffset.UTC);
    private final Instant now = clock.instant();

    private UUID holderId;
    private UUID recipientId;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        service = new KeyService(keys, devices, authz, clock);
        holderId = UUID.randomUUID();
        recipientId = UUID.randomUUID();
        deviceId = UUID.randomUUID();
    }

    private DigitalKey activeParent(Set<Permission> permissions) {
        DigitalKey parent = new DigitalKey(
                UUID.randomUUID(), UUID.randomUUID(), holderId, UUID.randomUUID(), UUID.randomUUID(),
                null, 0, now.minusSeconds(60), now.plusSeconds(7200), permissions, List.of(), null, now);
        when(keys.findByIdForShare(parent.getId())).thenReturn(Optional.of(parent));
        when(authz.isHolder(parent, holderId)).thenReturn(true);
        return parent;
    }

    private ShareKeyRequest requestWithin(DigitalKey parent, Set<Permission> permissions) {
        return new ShareKeyRequest(recipientId, deviceId, permissions, now, now.plusSeconds(1800), null, null);
    }

    private void recipientOwnsDevice() {
        Device device = new Device(deviceId, recipientId, "pub-key", "phone", now);
        when(devices.findById(deviceId)).thenReturn(Optional.of(device));
    }

    @Test
    void narrowerShareSucceeds() {
        DigitalKey parent = activeParent(EnumSet.of(Permission.UNLOCK, Permission.SHARE));
        recipientOwnsDevice();
        when(keys.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DigitalKey child = service.share(parent.getId(), requestWithin(parent, Set.of(Permission.UNLOCK)), holderId);

        assertThat(child.getPermissions()).containsExactly(Permission.UNLOCK);
        assertThat(child.getDepth()).isEqualTo(1);
        assertThat(child.getParentKeyId()).isEqualTo(parent.getId());
    }

    @Test
    void onlyTheHolderCanShare() {
        DigitalKey parent = new DigitalKey(
                UUID.randomUUID(), UUID.randomUUID(), holderId, UUID.randomUUID(), UUID.randomUUID(),
                null, 0, now.minusSeconds(60), now.plusSeconds(7200),
                EnumSet.of(Permission.UNLOCK, Permission.SHARE), List.of(), null, now);
        when(keys.findByIdForShare(parent.getId())).thenReturn(Optional.of(parent));
        UUID notTheHolder = UUID.randomUUID();
        when(authz.isHolder(parent, notTheHolder)).thenReturn(false);

        assertThatThrownBy(() -> service.share(parent.getId(), requestWithin(parent, Set.of(Permission.UNLOCK)), notTheHolder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("holder");
    }

    @Test
    void parentMustBeActive() {
        DigitalKey parent = new DigitalKey(
                UUID.randomUUID(), UUID.randomUUID(), holderId, UUID.randomUUID(), UUID.randomUUID(),
                null, 0, now.minusSeconds(60), now.plusSeconds(7200),
                EnumSet.of(Permission.UNLOCK, Permission.SHARE), List.of(), null, now);
        parent.suspend();
        when(keys.findByIdForShare(parent.getId())).thenReturn(Optional.of(parent));
        when(authz.isHolder(parent, holderId)).thenReturn(true);

        assertThatThrownBy(() -> service.share(parent.getId(), requestWithin(parent, Set.of(Permission.UNLOCK)), holderId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active");
    }

    @Test
    void parentMustHaveSharePermission() {
        DigitalKey parent = activeParent(EnumSet.of(Permission.UNLOCK));

        assertThatThrownBy(() -> service.share(parent.getId(), requestWithin(parent, Set.of(Permission.UNLOCK)), holderId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SHARE");
    }

    @Test
    void childCannotHaveMorePermissionsThanParent() {
        DigitalKey parent = activeParent(EnumSet.of(Permission.UNLOCK, Permission.SHARE));

        assertThatThrownBy(() -> service.share(
                parent.getId(), requestWithin(parent, Set.of(Permission.UNLOCK, Permission.START_ENGINE)), holderId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subset");
    }

    @Test
    void childWindowMustSitInsideParentWindow() {
        DigitalKey parent = activeParent(EnumSet.of(Permission.UNLOCK, Permission.SHARE));
        ShareKeyRequest tooLate = new ShareKeyRequest(
                recipientId, deviceId, Set.of(Permission.UNLOCK), now, parent.getNotAfter().plusSeconds(1), null, null);

        assertThatThrownBy(() -> service.share(parent.getId(), tooLate, holderId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window");
    }

    @Test
    void maximumDelegationDepthIsEnforced() {
        DigitalKey parent = new DigitalKey(
                UUID.randomUUID(), UUID.randomUUID(), holderId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 3, now.minusSeconds(60), now.plusSeconds(7200),
                EnumSet.of(Permission.UNLOCK, Permission.SHARE), List.of(), null, now);
        when(keys.findByIdForShare(parent.getId())).thenReturn(Optional.of(parent));
        when(authz.isHolder(parent, holderId)).thenReturn(true);

        assertThatThrownBy(() -> service.share(parent.getId(), requestWithin(parent, Set.of(Permission.UNLOCK)), holderId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("depth");
    }

    @Test
    void recipientsDeviceMustBelongToRecipient() {
        DigitalKey parent = activeParent(EnumSet.of(Permission.UNLOCK, Permission.SHARE));
        Device someoneElsesDevice = new Device(deviceId, UUID.randomUUID(), "pub-key", "phone", now);
        when(devices.findById(deviceId)).thenReturn(Optional.of(someoneElsesDevice));

        assertThatThrownBy(() -> service.share(parent.getId(), requestWithin(parent, Set.of(Permission.UNLOCK)), holderId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipient");
    }
}
