package com.glancesync.common.geo;

public final class ApproximateDistance {

    private ApproximateDistance() {
    }

    public static Integer km(Double meters) {
        return meters == null ? null : (int) Math.max(1, Math.round(meters / 1000));
    }

    public static String label(Double meters) {
        if (meters == null) {
            return null;
        }
        return meters < 1000 ? "less than 1 km away" : km(meters) + " km away";
    }
}
