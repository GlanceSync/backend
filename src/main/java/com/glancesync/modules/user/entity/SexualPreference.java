package com.glancesync.modules.user.entity;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Locale;

@Serdeable
public enum SexualPreference {
    HETEROSEXUAL, HOMOSEXUAL, BISEXUAL;

    public static final SexualPreference DEFAULT = BISEXUAL;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SexualPreference fromDb(String value) {
        return value == null ? DEFAULT : valueOf(value.toUpperCase(Locale.ROOT));
    }
}
