package com.keypass.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keypass.common.model.AccessContext;
import com.keypass.common.model.Command;
import com.keypass.common.model.GeoPoint;
import com.keypass.common.model.GeofenceRule;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeofenceRuleTest {

    // A simple 10x10 square from (0,0) to (10,10).
    private static final GeofenceRule SQUARE = new GeofenceRule(List.of(
            new GeoPoint(0, 0), new GeoPoint(0, 10), new GeoPoint(10, 10), new GeoPoint(10, 0)));

    private AccessContext ctxAt(GeoPoint p) {
        return new AccessContext(Instant.now(), Command.UNLOCK, p, ZoneOffset.UTC);
    }

    @Test
    void pointInsideSquareIsAllowed() {
        assertThat(SQUARE.evaluate(ctxAt(new GeoPoint(5, 5))).passed()).isTrue();
    }

    @Test
    void pointOutsideSquareIsDenied() {
        assertThat(SQUARE.evaluate(ctxAt(new GeoPoint(20, 20))).passed()).isFalse();
    }

    @Test
    void missingLocationIsDenied() {
        assertThat(SQUARE.evaluate(ctxAt(null)).passed()).isFalse();
    }

    @Test
    void concavePolygonExcludesTheNotch() {
        // A "C" shape: a 10x10 square with a notch bitten out of the middle of the right edge.
        GeofenceRule cShape = new GeofenceRule(List.of(
                new GeoPoint(0, 0), new GeoPoint(10, 0), new GeoPoint(10, 4),
                new GeoPoint(5, 4), new GeoPoint(5, 6), new GeoPoint(10, 6),
                new GeoPoint(10, 10), new GeoPoint(0, 10)));

        assertThat(cShape.evaluate(ctxAt(new GeoPoint(1, 5))).passed()).isTrue();
        assertThat(cShape.evaluate(ctxAt(new GeoPoint(8, 5))).passed()).isFalse();
    }

    @Test
    void requiresAtLeastThreePoints() {
        assertThatThrownBy(() -> new GeofenceRule(List.of(new GeoPoint(0, 0), new GeoPoint(1, 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
