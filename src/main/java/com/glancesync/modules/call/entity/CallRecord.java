package com.glancesync.modules.call.entity;

import java.util.UUID;

import java.time.Instant;

/** A finished call, as stored. One row is written when the call ends. */
public record CallRecord(UUID id, UUID callerId, UUID calleeId, String mode, String status,
                         Instant startedAt, Instant answeredAt, Instant endedAt,
                         Integer durationSeconds) {
}
