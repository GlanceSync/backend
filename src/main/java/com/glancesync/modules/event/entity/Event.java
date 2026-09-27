package com.glancesync.modules.event.entity;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/** A proposed real-life meeting between two people. Usernames are joined in for display. */
@Serdeable
public record Event(long id, long organizerId, String organizerUsername,
                    long inviteeId, String inviteeUsername,
                    String title, String location, Instant startsAt,
                    String status, Instant createdAt) {
}
