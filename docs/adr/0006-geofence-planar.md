# 0006: Geofence stored as JSONB polygon, not PostGIS

Status: Accepted

## Context

Geofence rules need a "is this point inside this polygon" check. PostGIS is the standard
answer for anything geospatial at scale, but it's also another extension to install, configure
and reason about.

## Decision

A geofence is a JSON list of `{lat, lon}` points, embedded directly in a key's policy (JSONB
column) and evaluated in Java with the ray-casting algorithm (`GeofenceRule.contains`,
`O(n)` in the number of polygon vertices).

## Consequences

Simple to implement, test (`GeofenceRuleTest` covers inside/outside/edge/concave cases) and
reason about, and needs no extra infrastructure. The known limitation: this treats latitude and
longitude as flat (planar) coordinates. That's accurate at city scale — the scale every realistic
geofence in this system operates at (a home, a workplace, a neighborhood) — but breaks down
across the antimeridian (a polygon spanning longitude ±180°) or over very large areas, where the
Earth's curvature actually matters.

## Alternatives considered

- **PostGIS**: the correct choice at genuine geospatial scale (a fleet operator drawing
  city-sized or larger regions), and the natural next step if this system needed to support
  that. Deliberately not used here — it would be solving a problem this system doesn't have yet,
  at the cost of an extension every environment (including CI and Testcontainers) has to carry.
