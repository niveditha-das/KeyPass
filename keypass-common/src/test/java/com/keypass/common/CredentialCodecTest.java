package com.keypass.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keypass.common.crypto.CredentialCodec;
import com.keypass.common.crypto.Ed25519;
import com.keypass.common.crypto.InvalidCredentialException;
import com.keypass.common.model.KeyCredential;
import com.keypass.common.model.Permission;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CredentialCodecTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final CredentialCodec codec = new CredentialCodec(mapper);

    private KeyCredential sampleCredential(String kid) {
        return new KeyCredential(
                kid, UUID.randomUUID(), "1HGCM82633A123456", UUID.randomUUID(), "device-pub-key",
                Set.of(Permission.UNLOCK, Permission.LOCK), Instant.now(), Instant.now().plusSeconds(3600),
                List.of(), null, Instant.now());
    }

    @Test
    void encodesAndDecodesRoundTrip() throws Exception {
        KeyPair serverKeys = Ed25519.generate();
        String token = codec.encode(sampleCredential("kid-1"), serverKeys.getPrivate());

        KeyCredential decoded = codec.decodeAndVerify(token, kid -> serverKeys.getPublic());

        assertThat(decoded.permissions()).containsExactlyInAnyOrder(Permission.UNLOCK, Permission.LOCK);
    }

    @Test
    void tamperedPayloadIsRejected() throws Exception {
        KeyPair serverKeys = Ed25519.generate();
        String token = codec.encode(sampleCredential("kid-1"), serverKeys.getPrivate());
        String[] parts = token.split("\\.");
        String tamperedPayload = parts[0].substring(0, parts[0].length() - 4) + "AAAA";
        String tampered = tamperedPayload + "." + parts[1];

        assertThatThrownBy(() -> codec.decodeAndVerify(tampered, kid -> serverKeys.getPublic()))
                .isInstanceOf(InvalidCredentialException.class);
    }

    @Test
    void wrongSigningKeyIsRejected() throws Exception {
        KeyPair serverKeys = Ed25519.generate();
        KeyPair attackerKeys = Ed25519.generate();
        String token = codec.encode(sampleCredential("kid-1"), attackerKeys.getPrivate());

        assertThatThrownBy(() -> codec.decodeAndVerify(token, kid -> serverKeys.getPublic()))
                .isInstanceOf(InvalidCredentialException.class);
    }

    @Test
    void unknownKidIsRejected() throws Exception {
        KeyPair serverKeys = Ed25519.generate();
        String token = codec.encode(sampleCredential("kid-unknown"), serverKeys.getPrivate());

        java.util.function.Function<String, PublicKey> noKeys = kid -> null;
        assertThatThrownBy(() -> codec.decodeAndVerify(token, noKeys))
                .isInstanceOf(InvalidCredentialException.class);
    }

    @Test
    void malformedTokenIsRejected() {
        assertThatThrownBy(() -> codec.decodeAndVerify("not-a-valid-token", kid -> null))
                .isInstanceOf(InvalidCredentialException.class);
    }
}
