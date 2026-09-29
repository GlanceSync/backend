package com.glancesync.modules.event.entity;

import java.util.UUID;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/** A proposed real-life meeting between two people. Usernames are joined in for display. */
@Serdeable
public record Event(UUID id, UUID organizerId, String organizerUsername,
                    UUID inviteeId, String inviteeUsername,
                    String title, String location, Instant startsAt,
                    String status, Instant createdAt) {
}
