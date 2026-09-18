package com.keypass.server.offline;

import com.keypass.server.vehicle.Vehicle;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles/{vin}")
public class OfflineBundleController {

    private final OfflineBundleService bundleService;

    public OfflineBundleController(OfflineBundleService bundleService) {
        this.bundleService = bundleService;
    }

    @GetMapping("/offline-bundle")
    public OfflineBundleResponse bundle(@AuthenticationPrincipal Vehicle vehicle, @PathVariable String vin) {
        return bundleService.buildBundle(vehicle.getId());
    }
}
