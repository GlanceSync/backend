package com.glancesync.modules.user.enums;


import java.util.Locale;

public enum Gender {
    MALE, FEMALE;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Gender fromDb(String value) {
        return value == null ? null : valueOf(value.toUpperCase(Locale.ROOT));
    }
}
