package com.keypass.server.access;

import com.keypass.common.model.AccessRequest;
import com.keypass.server.vehicle.Vehicle;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles/{vin}")
public class AccessCheckController {

    private final AccessCheckService accessCheckService;

    public AccessCheckController(AccessCheckService accessCheckService) {
        this.accessCheckService = accessCheckService;
    }

    @PostMapping("/access-checks")
    public AccessDecision check(
            @AuthenticationPrincipal Vehicle vehicle, @PathVariable String vin, @RequestBody AccessRequest request) {
        return accessCheckService.check(vehicle, request);
    }
}
