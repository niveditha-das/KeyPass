package com.keypass.common.model;

import java.time.Instant;
import java.time.ZoneId;

/** Everything a policy rule needs to make its decision, gathered at the moment of the access check. */
public record AccessContext(Instant now, Command command, GeoPoint location, ZoneId zone) {}
