package com.keypass.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.keypass.common.model.AccessContext;
import com.keypass.common.model.Command;
import com.keypass.common.model.CurfewRule;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class CurfewRuleTest {

    private static final ZoneId DUBLIN = ZoneId.of("Europe/Dublin");
    private static final CurfewRule OVERNIGHT = new CurfewRule(LocalTime.of(23, 0), LocalTime.of(6, 0));
    private static final CurfewRule AFTERNOON = new CurfewRule(LocalTime.of(13, 0), LocalTime.of(15, 0));

    private AccessContext at(String isoLocal) {
        Instant instant = LocalDateTime.parse(isoLocal).atZone(DUBLIN).toInstant();
        return new AccessContext(instant, Command.START_ENGINE, null, DUBLIN);
    }

    @Test
    void nonEngineCommandsIgnoreCurfew() {
        AccessContext ctx = new AccessContext(Instant.now(), Command.UNLOCK, null, DUBLIN);
        assertThat(OVERNIGHT.evaluate(ctx).passed()).isTrue();
    }

    @Test
    void overnightCurfewBlocksAfterMidnight() {
        assertThat(OVERNIGHT.evaluate(at("2026-01-10T02:00:00")).passed()).isFalse();
    }

    @Test
    void overnightCurfewBlocksBeforeMidnight() {
        assertThat(OVERNIGHT.evaluate(at("2026-01-10T23:30:00")).passed()).isFalse();
    }

    @Test
    void overnightCurfewAllowsDaytime() {
        assertThat(OVERNIGHT.evaluate(at("2026-01-10T12:00:00")).passed()).isTrue();
    }

    @Test
    void overnightCurfewStartBoundaryIsBlocked() {
        assertThat(OVERNIGHT.evaluate(at("2026-01-10T23:00:00")).passed()).isFalse();
    }

    @Test
    void overnightCurfewEndBoundaryIsAllowed() {
        assertThat(OVERNIGHT.evaluate(at("2026-01-10T06:00:00")).passed()).isTrue();
    }

    @Test
    void sameDayCurfewBlocksWithinWindow() {
        assertThat(AFTERNOON.evaluate(at("2026-01-10T14:00:00")).passed()).isFalse();
    }

    @Test
    void sameDayCurfewAllowsOutsideWindow() {
        assertThat(AFTERNOON.evaluate(at("2026-01-10T09:00:00")).passed()).isTrue();
    }

    @Test
    void overnightCurfewIsCorrectDuringIrishSummerTime() {
        // Dublin is UTC+1 in July (IST). A naive UTC-based rule would be off by one hour here.
        assertThat(OVERNIGHT.evaluate(at("2026-07-15T23:30:00")).passed()).isFalse();
        assertThat(OVERNIGHT.evaluate(at("2026-07-15T22:30:00")).passed()).isTrue();
    }
}
