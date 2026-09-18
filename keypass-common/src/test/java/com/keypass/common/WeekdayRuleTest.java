package com.keypass.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keypass.common.model.AccessContext;
import com.keypass.common.model.Command;
import com.keypass.common.model.WeekdayRule;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WeekdayRuleTest {

    private static final ZoneId DUBLIN = ZoneId.of("Europe/Dublin");
    // 2026-01-12 is a Monday, 2026-01-17 is a Saturday.
    private static final WeekdayRule WEEKDAYS_ONLY =
            new WeekdayRule(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));

    private AccessContext at(String isoLocal) {
        return new AccessContext(
                LocalDateTime.parse(isoLocal).atZone(DUBLIN).toInstant(), Command.UNLOCK, null, DUBLIN);
    }

    @Test
    void allowsAnAllowedDay() {
        assertThat(WEEKDAYS_ONLY.evaluate(at("2026-01-12T10:00:00")).passed()).isTrue();
    }

    @Test
    void blocksADisallowedDay() {
        assertThat(WEEKDAYS_ONLY.evaluate(at("2026-01-17T10:00:00")).passed()).isFalse();
    }

    @Test
    void evaluatesInTheVehiclesOwnTimeZone() {
        // 2026-01-17T23:30 in Dublin (UTC) is still Saturday locally, so still blocked.
        assertThat(WEEKDAYS_ONLY.evaluate(at("2026-01-17T23:30:00")).passed()).isFalse();
    }

    @Test
    void rejectsAnEmptyDaySet() {
        assertThatThrownBy(() -> new WeekdayRule(Set.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
