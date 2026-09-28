package com.glancesync.modules.user.enums;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Locale;

@Serdeable
public enum LocationSource {
    GPS, MANUAL;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static LocationSource fromDb(String value) {
        return value == null ? null : valueOf(value.toUpperCase(Locale.ROOT));
    }
}
