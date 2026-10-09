package com.glancesync.modules.discovery.dto;

import java.util.UUID;
import com.glancesync.modules.user.enums.Gender;

import java.util.List;

/** Enough to show someone in a list or on a card without opening (and so visiting) their profile. */
public record DiscoveryCard(UUID id, String username, String firstName, String biography, String avatarUrl,
                            List<String> pictures, Gender gender, Integer age, String city, Integer distanceKm,
                            String distance, int fameRating, int commonTags, List<String> interests, boolean online) {
}
