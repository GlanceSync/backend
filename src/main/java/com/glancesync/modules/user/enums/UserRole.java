package com.glancesync.modules.user.enums;


import java.util.Locale;

public enum UserRole {
    USER, ADMIN;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static UserRole fromDb(String value) {
        return value == null ? USER : valueOf(value.toUpperCase(Locale.ROOT));
    }
}
