package com.glancesync.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One reported account in the admin queue, with every report filed against it. */
public record ReportedUserResponse(
        UUID id,
        String username,
        String firstName,
        String lastName,
        String avatarUrl,
        int reportCount,
        Instant lastReportedAt,
        @JsonProperty("isActive") boolean active,
        List<Report> reports) {

    public record Report(UUID reporterId, String reporterUsername, String reason, Instant createdAt) {
    }
}
