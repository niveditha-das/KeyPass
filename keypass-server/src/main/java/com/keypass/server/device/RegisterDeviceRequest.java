package com.keypass.server.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @NotBlank String publicKey,
        @Size(max = 100) String label) {}
