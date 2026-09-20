package com.keypass.server.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keypass.common.crypto.Ed25519;
import org.junit.jupiter.api.Test;

class SigningKeyRegistryTest {

    @Test
    void generatesAnEphemeralKeyWhenNoneIsConfiguredAndNotRequired() throws Exception {
        var registry = new SigningKeyRegistry("", "", "kid-1", false);

        assertThat(registry.currentPrivateKey()).isNotNull();
        assertThat(registry.publicKey("kid-1")).isNotNull();
    }

    @Test
    void refusesToStartWithoutAKeyWhenRequired() {
        assertThatThrownBy(() -> new SigningKeyRegistry("", "", "kid-1", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KEYPASS_SIGNING_KEY is required");
    }

    @Test
    void usesTheConfiguredKeyPairWhenRequired() throws Exception {
        var pair = Ed25519.generate();
        var registry = new SigningKeyRegistry(
                Ed25519.privateKeyToBase64(pair.getPrivate()),
                Ed25519.publicKeyToBase64(pair.getPublic()),
                "kid-prod",
                true);

        assertThat(registry.currentKid()).isEqualTo("kid-prod");
        assertThat(registry.publicKey("kid-prod")).isEqualTo(pair.getPublic());
    }

    @Test
    void aPrivateKeyWithoutItsPublicHalfIsRejected() throws Exception {
        var pair = Ed25519.generate();

        assertThatThrownBy(() -> new SigningKeyRegistry(
                        Ed25519.privateKeyToBase64(pair.getPrivate()), "", "kid-1", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KEYPASS_SIGNING_PUBLIC_KEY");
    }
}
