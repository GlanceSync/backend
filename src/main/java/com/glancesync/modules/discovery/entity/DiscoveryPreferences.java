package com.glancesync.modules.discovery.entity;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record DiscoveryPreferences(Integer minAge, Integer maxAge, Integer minFame, Integer maxFame,
                                   Double distance, String city, List<String> tags, String sort, String order) {

    public static final DiscoveryPreferences NONE =
            new DiscoveryPreferences(null, null, null, null, null, null, List.of(), null, null);

    public DiscoveryPreferences overriddenBy(DiscoveryPreferences overrides) {
        return new DiscoveryPreferences(
                pick(overrides.minAge(), minAge), pick(overrides.maxAge(), maxAge),
                pick(overrides.minFame(), minFame), pick(overrides.maxFame(), maxFame),
                pick(overrides.distance(), distance), pick(overrides.city(), city),
                overrides.tags() == null || overrides.tags().isEmpty() ? tags : overrides.tags(),
                pick(overrides.sort(), sort), pick(overrides.order(), order));
    }

    private static <T> T pick(T override, T saved) {
        return override != null ? override : saved;
    }
}
