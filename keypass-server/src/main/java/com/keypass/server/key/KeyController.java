package com.keypass.server.key;

import com.keypass.server.common.CurrentUser;
import com.keypass.server.common.NotFoundException;
import com.keypass.server.revocation.KeyRevocationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/keys")
public class KeyController {

    private final DigitalKeyRepository keys;
    private final KeyAuthorization authz;
    private final KeyService keyService;
    private final KeyRevocationService revocationService;
    private final CredentialIssuer credentialIssuer;

    public KeyController(
            DigitalKeyRepository keys, KeyAuthorization authz, KeyService keyService,
            KeyRevocationService revocationService, CredentialIssuer credentialIssuer) {
        this.keys = keys;
        this.authz = authz;
        this.keyService = keyService;
        this.revocationService = revocationService;
        this.credentialIssuer = credentialIssuer;
    }

    @GetMapping("/mine")
    public List<KeyResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = CurrentUser.idOf(jwt);
        return keys.findByHolderId(userId).stream().map(KeyResponse::from).toList();
    }

    @GetMapping("/{id}")
    public KeyResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = CurrentUser.idOf(jwt);
        DigitalKey key = keys.findById(id).orElseThrow(NotFoundException::new);
        return KeyResponse.from(authz.requireViewable(key, userId));
    }

    @GetMapping("/{id}/credential")
    public Map<String, String> credential(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) throws Exception {
        UUID userId = CurrentUser.idOf(jwt);
        DigitalKey key = keys.findByIdAndHolderId(id, userId).orElseThrow(NotFoundException::new);
        return Map.of("credential", credentialIssuer.issue(key));
    }

    @PostMapping("/{id}/shares")
    public ResponseEntity<KeyResponse> share(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ShareKeyRequest request) {
        UUID actorId = CurrentUser.idOf(jwt);
        DigitalKey child = keyService.share(id, request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(KeyResponse.from(child));
    }

    @PostMapping("/{id}/suspend")
    public KeyResponse suspend(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID actorId = CurrentUser.idOf(jwt);
        return KeyResponse.from(keyService.suspend(id, actorId));
    }

    @PostMapping("/{id}/resume")
    public KeyResponse resume(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID actorId = CurrentUser.idOf(jwt);
        return KeyResponse.from(keyService.resume(id, actorId));
    }

    @PostMapping("/{id}/revoke")
    public Map<String, Object> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID actorId = CurrentUser.idOf(jwt);
        List<UUID> revoked = revocationService.revoke(id, actorId);
        return Map.of("revokedKeyIds", revoked);
    }
}
