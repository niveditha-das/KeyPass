package com.keypass.server.key;

import com.keypass.common.model.Permission;
import com.keypass.common.model.PolicyRule;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record IssueKeyRequest(
        @NotNull UUID holderId,
        @NotNull UUID deviceId,
        @NotEmpty Set<Permission> permissions,
        @NotNull Instant notBefore,
        @NotNull @Future Instant notAfter,
        List<PolicyRule> policy,
        @Min(10) @Max(250) Integer maxSpeedKmh) {

    public List<PolicyRule> policyOrEmpty() {
        return policy == null ? List.of() : policy;
    }
}
