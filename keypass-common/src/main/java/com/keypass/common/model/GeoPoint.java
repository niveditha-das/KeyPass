package com.keypass.common.model;

public record GeoPoint(double lat, double lon) {
    public GeoPoint {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Invalid coordinates: " + lat + "," + lon);
        }
    }
}
