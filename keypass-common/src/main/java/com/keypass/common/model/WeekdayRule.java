package com.keypass.common.model;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.Set;

/** Only allows a command on the given days of the week, in the vehicle's own time zone. */
public record WeekdayRule(Set<DayOfWeek> allowedDays) implements PolicyRule {

    public WeekdayRule {
        if (allowedDays == null || allowedDays.isEmpty()) {
            throw new IllegalArgumentException("At least one allowed day is required");
        }
        allowedDays = Set.copyOf(allowedDays);
    }

    @Override
    public RuleResult evaluate(AccessContext ctx) {
        ZonedDateTime local = ctx.now().atZone(ctx.zone());
        DayOfWeek day = local.getDayOfWeek();
        return allowedDays.contains(day)
                ? RuleResult.pass("WEEKDAYS")
                : RuleResult.fail("WEEKDAYS", "Not allowed on " + day);
    }
}
