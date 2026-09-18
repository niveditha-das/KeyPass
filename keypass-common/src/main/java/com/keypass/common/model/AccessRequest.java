package com.keypass.common.model;

import java.time.Instant;

/**
 * What a car sends to ask "can this phone do this?". The device signature covers
 * vin|command|challenge|timestamp so a signed request can't be replayed against a
 * different car, command or time.
 */
public record AccessRequest(
        String credential,
        Command command,
        String challenge,
        Instant timestamp,
        GeoPoint location,
        String deviceSignature) {}
