package com.glancesync.modules.user.enums;


import java.util.Locale;

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
