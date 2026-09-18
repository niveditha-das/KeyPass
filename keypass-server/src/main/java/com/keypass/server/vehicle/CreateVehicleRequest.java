package com.keypass.server.vehicle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateVehicleRequest(
        @NotBlank @Pattern(regexp = "^[A-HJ-NPR-Z0-9]{17}$", message = "must be a valid 17-character VIN")
                String vin,
        @NotBlank String model,
        String timeZone) {}
