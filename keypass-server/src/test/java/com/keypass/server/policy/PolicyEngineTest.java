package com.keypass.server.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.keypass.common.model.AccessContext;
import com.keypass.common.model.Command;
import com.keypass.common.model.CurfewRule;
import com.keypass.common.model.Permission;
import com.keypass.server.key.DigitalKey;
import com.keypass.server.key.KeyStatus;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PolicyEngineTest {

    private final PolicyEngine engine = new PolicyEngine();

    private DigitalKey activeKey(Set<Permission> permissions, Instant notBefore, Instant notAfter, List<com.keypass.common.model.PolicyRule> policy) {
        return new DigitalKey(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, 0, notBefore, notAfter, permissions, policy, null, Instant.now());
    }

    @Test
    void evaluatesEveryRuleWithoutShortCircuiting() {
        Instant now = Instant.parse("2026-01-10T12:00:00Z");
        DigitalKey key = activeKey(Set.of(Permission.LOCK),
                now.plusSeconds(3600), now.plusSeconds(7200), List.of());

        AccessContext ctx = new AccessContext(now, Command.UNLOCK, null, ZoneOffset.UTC);
        Evaluation eval = engine.evaluate(key, ctx);

        assertThat(eval.granted()).isFalse();
        // VALIDITY (not yet started) and PERMISSION (lacks UNLOCK) must BOTH be reported.
        assertThat(eval.trace()).extracting("rule").contains("STATUS", "VALIDITY", "PERMISSION");
        assertThat(eval.trace()).filteredOn(r -> !r.passed()).extracting("rule")
                .containsExactlyInAnyOrder("VALIDITY", "PERMISSION");
    }

    @Test
    void grantsWhenEveryRulePasses() {
        Instant now = Instant.parse("2026-01-10T12:00:00Z");
        DigitalKey key = activeKey(Set.of(Permission.UNLOCK),
                now.minusSeconds(60), now.plusSeconds(3600), List.of());

        Evaluation eval = engine.evaluate(key, new AccessContext(now, Command.UNLOCK, null, ZoneOffset.UTC));

        assertThat(eval.granted()).isTrue();
        assertThat(eval.trace()).allMatch(com.keypass.common.model.RuleResult::passed);
    }

    @Test
    void suspendedKeyFailsStatusEvenIfValidityAndPermissionPass() {
        Instant now = Instant.parse("2026-01-10T12:00:00Z");
        DigitalKey key = activeKey(Set.of(Permission.UNLOCK),
                now.minusSeconds(60), now.plusSeconds(3600), List.of());
        key.suspend();

        Evaluation eval = engine.evaluate(key, new AccessContext(now, Command.UNLOCK, null, ZoneOffset.UTC));

        assertThat(eval.granted()).isFalse();
        assertThat(eval.trace().get(0).rule()).isEqualTo("STATUS");
        assertThat(eval.trace().get(0).passed()).isFalse();
    }

    @Test
    void embeddedPolicyRulesAreEvaluated() {
        Instant curfewTime = Instant.parse("2026-01-10T23:30:00Z");
        CurfewRule curfew = new CurfewRule(LocalTime.of(23, 0), LocalTime.of(6, 0));
        DigitalKey key = activeKey(Set.of(Permission.START_ENGINE),
                curfewTime.minusSeconds(3600), curfewTime.plusSeconds(3600), List.of(curfew));

        Evaluation eval = engine.evaluate(key, new AccessContext(curfewTime, Command.START_ENGINE, null, ZoneOffset.UTC));

        assertThat(eval.granted()).isFalse();
        assertThat(eval.trace()).extracting("rule").contains("CURFEW");
    }
}
