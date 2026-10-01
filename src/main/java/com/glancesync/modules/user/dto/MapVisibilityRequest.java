package com.glancesync.modules.user.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record MapVisibilityRequest(Boolean showOnMap) {
}
