package com.glancesync.modules.discovery.dto;

import java.util.List;

public record DiscoveryFilters(Integer minAge, Integer maxAge, Integer minFame, Integer maxFame,
                               Double distanceKm, String city, List<String> tags,
                               DiscoverySort sort, boolean ascending, int limit, int offset) {
}
