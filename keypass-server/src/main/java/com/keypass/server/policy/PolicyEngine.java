package com.keypass.server.policy;

import com.keypass.common.model.AccessContext;
import com.keypass.common.model.Permission;
import com.keypass.common.model.RuleResult;
import com.keypass.server.key.DigitalKey;
import com.keypass.server.key.KeyStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Evaluates every rule and never short-circuits, so the returned trace always explains every
 * reason a request was granted or denied — not just the first failure.
 */
@Component
public class PolicyEngine {

    public Evaluation evaluate(DigitalKey key, AccessContext ctx) {
        List<RuleResult> trace = new ArrayList<>();

        trace.add(key.getStatus() == KeyStatus.ACTIVE
                ? RuleResult.pass("STATUS")
                : RuleResult.fail("STATUS", "Key is " + key.getStatus()));

        trace.add(ctx.now().isBefore(key.getNotBefore()) || !ctx.now().isBefore(key.getNotAfter())
                ? RuleResult.fail("VALIDITY", "Outside validity window")
                : RuleResult.pass("VALIDITY"));

        Permission required = Permission.valueOf(ctx.command().name());
        trace.add(key.getPermissions().contains(required)
                ? RuleResult.pass("PERMISSION")
                : RuleResult.fail("PERMISSION", "Key lacks " + ctx.command()));

        key.getPolicy().forEach(rule -> trace.add(rule.evaluate(ctx)));

        boolean granted = trace.stream().allMatch(RuleResult::passed);
        return new Evaluation(granted, List.copyOf(trace));
    }
}
