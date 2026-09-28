package com.glancesync.modules.user.entity;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Locale;

@Serdeable
public enum Gender {
    MALE, FEMALE;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Gender fromDb(String value) {
        return value == null ? null : valueOf(value.toUpperCase(Locale.ROOT));
    }
}
