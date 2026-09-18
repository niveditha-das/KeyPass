package com.keypass.server.access;

import com.keypass.common.crypto.AccessSigningMessage;
import com.keypass.common.crypto.CredentialCodec;
import com.keypass.common.crypto.Ed25519;
import com.keypass.common.model.AccessRequest;
import com.keypass.common.model.KeyCredential;
import com.keypass.server.key.SigningKeyRegistry;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class CredentialVerifier {

    private final CredentialCodec codec;
    private final SigningKeyRegistry signingKeys;

    public CredentialVerifier(CredentialCodec codec, SigningKeyRegistry signingKeys) {
        this.codec = codec;
        this.signingKeys = signingKeys;
    }

    /** Verifies the server's signature on the credential and decodes it. */
    public KeyCredential verify(String token) {
        return codec.decodeAndVerify(token, signingKeys::publicKey);
    }

    /** Verifies the request was signed just now by the device that owns this credential —
     * proof of possession, not just possession of the credential text. */
    public boolean deviceSignatureValid(KeyCredential cred, String vin, AccessRequest req) {
        try {
            PublicKey devicePublicKey = Ed25519.publicKeyFromBase64(cred.devicePublicKey());
            byte[] message = AccessSigningMessage.bytes(vin, req.command().name(), req.challenge(), req.timestamp());
            byte[] signature = Base64.getDecoder().decode(req.deviceSignature());
            return Ed25519.verify(devicePublicKey, message, signature);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}
