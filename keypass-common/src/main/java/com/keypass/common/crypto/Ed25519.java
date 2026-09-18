package com.keypass.common.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class Ed25519 {

    private Ed25519() {}

    public static KeyPair generate() throws GeneralSecurityException {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    public static byte[] sign(PrivateKey key, byte[] data) throws GeneralSecurityException {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(key);
        s.update(data);
        return s.sign();
    }

    public static boolean verify(PublicKey key, byte[] data, byte[] sig) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(data);
            return s.verify(sig);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    public static PublicKey publicKeyFromBase64(String b64) throws GeneralSecurityException {
        byte[] der = Base64.getDecoder().decode(b64);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
    }

    public static PrivateKey privateKeyFromBase64(String b64) throws GeneralSecurityException {
        byte[] der = Base64.getDecoder().decode(b64);
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    public static String publicKeyToBase64(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static String privateKeyToBase64(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}
