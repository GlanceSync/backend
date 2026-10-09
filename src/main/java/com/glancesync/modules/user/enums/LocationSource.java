package com.glancesync.modules.user.enums;


import java.util.Locale;

public enum LocationSource {
    GPS, MANUAL;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static LocationSource fromDb(String value) {
        return value == null ? null : valueOf(value.toUpperCase(Locale.ROOT));
    }
}
