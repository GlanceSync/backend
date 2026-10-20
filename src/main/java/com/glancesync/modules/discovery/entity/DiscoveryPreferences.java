package com.glancesync.modules.discovery.entity;


import java.util.List;

public record DiscoveryPreferences(Integer minAge, Integer maxAge, Integer minFame, Integer maxFame,
                                   Double distance, String city, List<String> tags, Integer minCommonTags,
                                   String sort, String order) {

    public static final DiscoveryPreferences NONE =
            new DiscoveryPreferences(null, null, null, null, null, null, List.of(), null, null, null);

    public DiscoveryPreferences overriddenBy(DiscoveryPreferences overrides) {
        return new DiscoveryPreferences(
                pick(overrides.minAge(), minAge), pick(overrides.maxAge(), maxAge),
                pick(overrides.minFame(), minFame), pick(overrides.maxFame(), maxFame),
                pick(overrides.distance(), distance), pick(overrides.city(), city),
                overrides.tags() == null || overrides.tags().isEmpty() ? tags : overrides.tags(),
                pick(overrides.minCommonTags(), minCommonTags),
                pick(overrides.sort(), sort), pick(overrides.order(), order));
    }

    public DiscoveryPreferences withoutDistance() {
        return new DiscoveryPreferences(minAge, maxAge, minFame, maxFame, null, city, tags, minCommonTags, sort,
                order);
    }

    public DiscoveryPreferences withoutSort() {
        return new DiscoveryPreferences(minAge, maxAge, minFame, maxFame, distance, city, tags, minCommonTags, null,
                null);
    }

    private static <T> T pick(T override, T saved) {
        return override != null ? override : saved;
    }
}
