package com.glancesync.modules.discovery.dto;

public enum DiscoverySort {
    AGE("age", "c.age", true),
    DISTANCE("distance", "c.distance_m", true),
    FAME("fame", "c.fame_rating", false),
    COMMON_TAGS("commonTags", "c.common_tags", false);

    private final String paramName;
    private final String column;
    private final boolean ascendingByDefault;

    DiscoverySort(String paramName, String column, boolean ascendingByDefault) {
        this.paramName = paramName;
        this.column = column;
        this.ascendingByDefault = ascendingByDefault;
    }

    public String paramName() {
        return paramName;
    }

    public String column() {
        return column;
    }

    public boolean ascendingByDefault() {
        return ascendingByDefault;
    }
}
