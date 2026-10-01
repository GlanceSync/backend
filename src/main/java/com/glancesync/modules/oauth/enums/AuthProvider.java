package com.glancesync.modules.oauth.enums;

public enum AuthProvider {
    INTRA("42", "Intra 42"),
    GOOGLE("google", "Google"),
    FACEBOOK("facebook", "Facebook");

    private final String key;
    private final String displayName;

    AuthProvider(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }
}
