package com.keypass.server.key;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keypass.common.model.Permission;
import com.keypass.server.common.ApiExceptionHandler;
import com.keypass.server.common.NotFoundException;
import com.keypass.server.config.JwtKeysConfig;
import com.keypass.server.config.SecurityConfig;
import com.keypass.server.revocation.KeyRevocationService;
import com.keypass.server.vehicle.VehicleRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A web-layer slice test: real Spring MVC dispatch and the real security chain (JWT auth,
 * role/ownership checks), with the persistence and service layers mocked out. Covers
 * validation errors, the ProblemDetail error shape, and 401/404 behavior without needing a
 * database.
 */
@WebMvcTest(controllers = KeyController.class)
@Import({SecurityConfig.class, JwtKeysConfig.class, ApiExceptionHandler.class})
class KeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DigitalKeyRepository keys;

    @MockitoBean
    private KeyAuthorization authz;

    @MockitoBean
    private KeyService keyService;

    @MockitoBean
    private KeyRevocationService revocationService;

    @MockitoBean
    private CredentialIssuer credentialIssuer;

    @MockitoBean
    private VehicleRepository vehicles; // needed by the vehicle API key filter in the security chain

    private DigitalKey sampleKey(UUID holderId) {
        Instant now = Instant.now();
        return new DigitalKey(
                UUID.randomUUID(), UUID.randomUUID(), holderId, UUID.randomUUID(), UUID.randomUUID(),
                null, 0, now.minusSeconds(60), now.plus(1, ChronoUnit.HOURS),
                EnumSet.of(Permission.UNLOCK), List.of(), null, now);
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/keys/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOwnKeyReturnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        DigitalKey key = sampleKey(userId);
        when(keys.findById(key.getId())).thenReturn(Optional.of(key));
        when(authz.requireViewable(key, userId)).thenReturn(key);

        mockMvc.perform(get("/api/v1/keys/{id}", key.getId())
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk());
    }

    @Test
    void getSomeoneElsesKeyReturns404NotForbidden() throws Exception {
        UUID userId = UUID.randomUUID();
        DigitalKey key = sampleKey(UUID.randomUUID());
        when(keys.findById(key.getId())).thenReturn(Optional.of(key));
        when(authz.requireViewable(key, userId)).thenThrow(new NotFoundException());

        mockMvc.perform(get("/api/v1/keys/{id}", key.getId())
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPathStatus(404));
    }

    @Test
    void getUnknownKeyReturns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        when(keys.findById(missingId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/keys/{id}", missingId)
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void shareWithMissingRequiredFieldsIsRejectedWithValidationProblem() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/keys/{id}/shares", keyId)
                        .with(jwt().jwt(j -> j.subject(userId.toString())))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPathStatus(400));
    }

    @Test
    void suspendDelegatesToKeyService() throws Exception {
        UUID userId = UUID.randomUUID();
        DigitalKey key = sampleKey(userId);
        when(keyService.suspend(key.getId(), userId)).thenReturn(key);

        mockMvc.perform(post("/api/v1/keys/{id}/suspend", key.getId())
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk());
    }

    @Test
    void resumeDelegatesToKeyService() throws Exception {
        UUID userId = UUID.randomUUID();
        DigitalKey key = sampleKey(userId);
        when(keyService.resume(key.getId(), userId)).thenReturn(key);

        mockMvc.perform(post("/api/v1/keys/{id}/resume", key.getId())
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk());
    }

    @Test
    void revokeReturnsRevokedKeyIds() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(revocationService.revoke(keyId, userId)).thenReturn(List.of(keyId, childId));

        mockMvc.perform(post("/api/v1/keys/{id}/revoke", keyId)
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.revokedKeyIds.length()").value(2));
    }

    @Test
    void mineListsKeysHeldByTheCurrentUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(keys.findByHolderId(userId)).thenReturn(List.of(sampleKey(userId), sampleKey(userId)));

        mockMvc.perform(get("/api/v1/keys/mine")
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.length()").value(2));
    }

    @Test
    void credentialReturnsTheIssuedToken() throws Exception {
        UUID userId = UUID.randomUUID();
        DigitalKey key = sampleKey(userId);
        when(keys.findByIdAndHolderId(key.getId(), userId)).thenReturn(Optional.of(key));
        when(credentialIssuer.issue(key)).thenReturn("signed-credential-token");

        mockMvc.perform(get("/api/v1/keys/{id}/credential", key.getId())
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.credential").value("signed-credential-token"));
    }

    @Test
    void credentialForSomeoneElsesKeyReturns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        when(keys.findByIdAndHolderId(keyId, userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/keys/{id}/credential", keyId)
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isNotFound());
    }

    private static org.springframework.test.web.servlet.ResultMatcher jsonPathStatus(int expected) {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .jsonPath("$.status").value(expected);
    }
}
