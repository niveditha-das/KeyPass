package com.keypass.server.key;

import com.keypass.common.crypto.CredentialCodec;
import com.keypass.common.model.KeyCredential;
import com.keypass.server.common.NotFoundException;
import com.keypass.server.device.Device;
import com.keypass.server.device.DeviceRepository;
import com.keypass.server.vehicle.Vehicle;
import com.keypass.server.vehicle.VehicleRepository;
import java.security.GeneralSecurityException;
import java.time.Clock;
import org.springframework.stereotype.Service;

/** Turns a DigitalKey row into the signed, offline-verifiable token a phone carries around. */
@Service
public class CredentialIssuer {

    private final DeviceRepository devices;
    private final VehicleRepository vehicles;
    private final SigningKeyRegistry signingKeys;
    private final CredentialCodec codec;
    private final Clock clock;

    public CredentialIssuer(
            DeviceRepository devices, VehicleRepository vehicles, SigningKeyRegistry signingKeys,
            CredentialCodec codec, Clock clock) {
        this.devices = devices;
        this.vehicles = vehicles;
        this.signingKeys = signingKeys;
        this.codec = codec;
        this.clock = clock;
    }

    public String issue(DigitalKey key) throws GeneralSecurityException {
        Device device = devices.findById(key.getDeviceId()).orElseThrow(NotFoundException::new);
        Vehicle vehicle = vehicles.findById(key.getVehicleId()).orElseThrow(NotFoundException::new);

        KeyCredential credential = new KeyCredential(
                signingKeys.currentKid(),
                key.getId(),
                vehicle.getVin(),
                key.getHolderId(),
                device.getPublicKey(),
                key.getPermissions(),
                key.getNotBefore(),
                key.getNotAfter(),
                key.getPolicy(),
                key.getMaxSpeedKmh(),
                clock.instant());
        return codec.encode(credential, signingKeys.currentPrivateKey());
    }
}
