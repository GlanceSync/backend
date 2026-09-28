package com.glancesync.modules.user.dto;

import com.glancesync.modules.user.enums.LocationSource;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record LocationRequest(LocationSource source, Double latitude, Double longitude, String city) {
}
