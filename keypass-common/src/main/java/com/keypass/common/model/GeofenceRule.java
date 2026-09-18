package com.keypass.common.model;

import java.util.List;

/**
 * Restricts a command to inside a polygon, checked with the ray-casting point-in-polygon
 * algorithm. Treats lat/lon as flat coordinates, which is accurate at city scale but wrong
 * across the antimeridian or over very large areas (see ADR 0006).
 */
public record GeofenceRule(List<GeoPoint> polygon) implements PolicyRule {

    public GeofenceRule {
        if (polygon == null || polygon.size() < 3) {
            throw new IllegalArgumentException("Polygon needs at least 3 points");
        }
        polygon = List.copyOf(polygon);
    }

    @Override
    public RuleResult evaluate(AccessContext ctx) {
        if (ctx.location() == null) {
            return RuleResult.fail("GEOFENCE", "Location missing");
        }
        return contains(ctx.location())
                ? RuleResult.pass("GEOFENCE")
                : RuleResult.fail("GEOFENCE", "Vehicle outside allowed area");
    }

    boolean contains(GeoPoint p) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            GeoPoint a = polygon.get(i);
            GeoPoint b = polygon.get(j);
            if ((a.lat() > p.lat()) != (b.lat() > p.lat())) {
                double lonAtLat = (b.lon() - a.lon()) * (p.lat() - a.lat()) / (b.lat() - a.lat()) + a.lon();
                if (p.lon() < lonAtLat) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }
}
