package com.keypass.server.key;

import com.keypass.common.crypto.Ed25519;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Holds the server's Ed25519 signing key for KeyCredentials (distinct from the RS256 JWTs used
 * for user login). If KEYPASS_SIGNING_KEY isn't set, a key pair is generated at startup so local
 * development works out of the box — every restart then invalidates previously issued
 * credentials, which is fine for dev but not for production. In production this key would live
 * in AWS KMS or an HSM and never touch application memory in plaintext.
 */
@Component
public class SigningKeyRegistry {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyRegistry.class);

    private final String activeKid;
    private final PrivateKey activePrivateKey;
    private final Map<String, PublicKey> publicKeysByKid;

    public SigningKeyRegistry(
            @Value("${keypass.signing-key:}") String base64PrivateKey,
            @Value("${keypass.signing-public-key:}") String base64PublicKey,
            @Value("${keypass.signing-key-id:kid-dev-1}") String kid)
            throws GeneralSecurityException {
        this.activeKid = kid;
        if (base64PrivateKey == null || base64PrivateKey.isBlank()) {
            log.warn("KEYPASS_SIGNING_KEY not set; generating an ephemeral Ed25519 key for this run only. "
                    + "Credentials issued now will not verify after a restart.");
            KeyPair pair = Ed25519.generate();
            this.activePrivateKey = pair.getPrivate();
            this.publicKeysByKid = Map.of(kid, pair.getPublic());
        } else {
            if (base64PublicKey == null || base64PublicKey.isBlank()) {
                throw new IllegalStateException(
                        "KEYPASS_SIGNING_KEY is set but KEYPASS_SIGNING_PUBLIC_KEY is missing. "
                                + "Java's Ed25519 KeyFactory can't derive a public key from a private one, so "
                                + "both must be supplied together (see scripts/generate-signing-key).");
            }
            this.activePrivateKey = Ed25519.privateKeyFromBase64(base64PrivateKey);
            this.publicKeysByKid = Map.of(kid, Ed25519.publicKeyFromBase64(base64PublicKey));
        }
    }

    public String currentKid() {
        return activeKid;
    }

    public PrivateKey currentPrivateKey() {
        return activePrivateKey;
    }

    public PublicKey publicKey(String kid) {
        return publicKeysByKid.get(kid);
    }

    public Map<String, PublicKey> allPublicKeys() {
        return publicKeysByKid;
    }
}
