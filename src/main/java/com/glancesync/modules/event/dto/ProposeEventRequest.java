package com.glancesync.modules.event.dto;

import java.util.UUID;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ProposeEventRequest(UUID inviteeId, String title, String location, String startsAt) {
}
