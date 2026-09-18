package com.keypass.server.key;

import com.keypass.common.crypto.Ed25519;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/.well-known")
public class WellKnownController {

    public record ServerKey(String kid, String publicKey) {}

    private final SigningKeyRegistry signingKeys;

    public WellKnownController(SigningKeyRegistry signingKeys) {
        this.signingKeys = signingKeys;
    }

    @GetMapping("/keypass-keys")
    public List<ServerKey> keys() {
        return signingKeys.allPublicKeys().entrySet().stream()
                .map(e -> new ServerKey(e.getKey(), Ed25519.publicKeyToBase64(e.getValue())))
                .toList();
    }
}
