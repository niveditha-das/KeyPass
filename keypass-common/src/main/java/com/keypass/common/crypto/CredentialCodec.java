package com.keypass.common.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keypass.common.model.KeyCredential;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.function.Function;

/**
 * Encodes a KeyCredential as base64url(payloadJson) + "." + base64url(signature), and verifies
 * it back. The signature is checked over the exact bytes received, never a re-serialisation of
 * the decoded object, because JSON field order isn't guaranteed to round-trip identically.
 */
public class CredentialCodec {

    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final ObjectMapper mapper;

    public CredentialCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String encode(KeyCredential cred, PrivateKey signingKey) throws GeneralSecurityException {
        try {
            byte[] payload = mapper.writeValueAsBytes(cred);
            byte[] sig = Ed25519.sign(signingKey, payload);
            return ENC.encodeToString(payload) + "." + ENC.encodeToString(sig);
        } catch (Exception e) {
            throw new InvalidCredentialException("Unable to encode credential: " + e.getMessage());
        }
    }

    public KeyCredential decodeAndVerify(String token, Function<String, PublicKey> keyLookup) {
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new InvalidCredentialException("Malformed credential");
        }
        byte[] payload;
        byte[] sig;
        try {
            payload = DEC.decode(parts[0]);
            sig = DEC.decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new InvalidCredentialException("Malformed credential encoding");
        }

        String kid;
        try {
            kid = mapper.readTree(payload).path("kid").asText();
        } catch (Exception e) {
            throw new InvalidCredentialException("Malformed credential payload");
        }

        PublicKey key = keyLookup.apply(kid);
        if (key == null || !Ed25519.verify(key, payload, sig)) {
            throw new InvalidCredentialException("Bad signature");
        }

        try {
            return mapper.readValue(payload, KeyCredential.class);
        } catch (Exception e) {
            throw new InvalidCredentialException("Malformed credential payload");
        }
    }
}
