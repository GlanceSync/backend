package com.glancesync.modules.event.dto;

import java.util.UUID;

public record ProposeEventRequest(UUID inviteeId, String title, String location, String startsAt) {
}
