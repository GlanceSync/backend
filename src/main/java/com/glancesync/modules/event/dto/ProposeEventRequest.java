package com.glancesync.modules.event.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ProposeEventRequest(Long inviteeId, String title, String location, String startsAt) {
}
