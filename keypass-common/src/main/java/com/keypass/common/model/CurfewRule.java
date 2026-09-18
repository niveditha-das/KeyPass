package com.keypass.common.model;

import java.time.LocalTime;
import java.time.ZonedDateTime;

/** Blocks engine starts during a nightly window, evaluated in the vehicle's own time zone. */
public record CurfewRule(LocalTime start, LocalTime end) implements PolicyRule {

    @Override
    public RuleResult evaluate(AccessContext ctx) {
        if (ctx.command() != Command.START_ENGINE) {
            return RuleResult.pass("CURFEW");
        }
        ZonedDateTime local = ctx.now().atZone(ctx.zone());
        LocalTime t = local.toLocalTime();
        boolean inCurfew = start.isBefore(end)
                ? !t.isBefore(start) && t.isBefore(end)
                : !t.isBefore(start) || t.isBefore(end);
        return inCurfew
                ? RuleResult.fail("CURFEW", "Engine start blocked during curfew " + start + "–" + end)
                : RuleResult.pass("CURFEW");
    }
}
