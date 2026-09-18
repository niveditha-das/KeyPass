import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * Generates an Ed25519 key pair for KEYPASS_SIGNING_KEY / KEYPASS_SIGNING_PUBLIC_KEY.
 * Run with: java scripts/GenerateSigningKey.java
 * (Java 21's single-file source launcher compiles and runs this directly, no build needed.)
 */
class GenerateSigningKey {
    public static void main(String[] args) throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        System.out.println("KEYPASS_SIGNING_KEY=" + privateKey);
        System.out.println("KEYPASS_SIGNING_PUBLIC_KEY=" + publicKey);
        System.out.println();
        System.out.println("Add both lines to your .env file. Never commit them.");
        System.out.println("In production, both halves would live in AWS KMS or an HSM instead.");
    }
}
