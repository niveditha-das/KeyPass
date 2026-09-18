package com.keypass.server.key;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keypass.common.model.Permission;
import com.keypass.server.common.ApiExceptionHandler;
import com.keypass.server.common.NotFoundException;
import com.keypass.server.config.JwtKeysConfig;
import com.keypass.server.config.SecurityConfig;
import com.keypass.server.vehicle.Vehicle;
import com.keypass.server.vehicle.VehicleRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = VehicleKeyController.class)
@Import({SecurityConfig.class, JwtKeysConfig.class, ApiExceptionHandler.class})
class VehicleKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KeyService keyService;

    @MockitoBean
    private KeyAuthorization authz;

    @MockitoBean
    private DigitalKeyRepository keys;

    @MockitoBean
    private VehicleRepository vehicles;

    private Vehicle vehicle(UUID ownerId, String vin) {
        return new Vehicle(UUID.randomUUID(), vin, ownerId, "Model", "Europe/Dublin", "hash", Instant.now());
    }

    private DigitalKey sampleKey(UUID vehicleId, UUID holderId) {
        Instant now = Instant.now();
        return new DigitalKey(
                UUID.randomUUID(), vehicleId, holderId, UUID.randomUUID(), UUID.randomUUID(),
                null, 0, now.minusSeconds(60), now.plusSeconds(3600), EnumSet.of(Permission.UNLOCK),
                List.of(), null, now);
    }

    @Test
    void issueKeyReturns201() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String vin = "1HGCM82633A123456";
        Vehicle v = vehicle(ownerId, vin);
        when(authz.requireOwnedVehicle(vin, ownerId)).thenReturn(v);
        DigitalKey created = sampleKey(v.getId(), UUID.randomUUID());
        when(keyService.issue(eq(v), any(IssueKeyRequest.class), eq(ownerId))).thenReturn(created);

        mockMvc.perform(post("/api/v1/vehicles/{vin}/keys", vin)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())))
                        .contentType("application/json")
                        .content(issueRequestJson(created.getHolderId(), created.getDeviceId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(created.getId().toString()));
    }

    @Test
    void issueKeyOnSomeoneElsesVehicleReturns404() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String vin = "1HGCM82633A654321";
        when(authz.requireOwnedVehicle(vin, ownerId)).thenThrow(new NotFoundException());

        mockMvc.perform(post("/api/v1/vehicles/{vin}/keys", vin)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())))
                        .contentType("application/json")
                        .content(issueRequestJson(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    private static String issueRequestJson(UUID holderId, UUID deviceId) {
        Instant now = Instant.now();
        return """
                {
                  "holderId": "%s",
                  "deviceId": "%s",
                  "permissions": ["UNLOCK"],
                  "notBefore": "%s",
                  "notAfter": "%s"
                }
                """.formatted(holderId, deviceId, now, now.plusSeconds(3600));
    }

    @Test
    void listKeysWithoutStatusFilterReturnsAllKeysForVehicle() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String vin = "1HGCM82633A111222";
        Vehicle v = vehicle(ownerId, vin);
        when(authz.requireOwnedVehicle(vin, ownerId)).thenReturn(v);
        when(keys.findByVehicleId(v.getId())).thenReturn(List.of(sampleKey(v.getId(), UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/vehicles/{vin}/keys", vin)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listKeysWithStatusFilterFiltersByStatus() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String vin = "1HGCM82633A333444";
        Vehicle v = vehicle(ownerId, vin);
        when(authz.requireOwnedVehicle(vin, ownerId)).thenReturn(v);
        when(keys.findByVehicleIdAndStatus(v.getId(), KeyStatus.REVOKED)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/vehicles/{vin}/keys?status=REVOKED", vin)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
