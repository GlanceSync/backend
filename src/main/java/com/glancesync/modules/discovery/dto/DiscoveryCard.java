package com.glancesync.modules.discovery.dto;

import java.util.UUID;
import com.glancesync.modules.user.enums.Gender;

public record DiscoveryCard(UUID id, String username, String avatarUrl, Gender gender, Integer age, String city,
                            Integer distanceKm, String distance, int fameRating, int commonTags, boolean online) {
}
