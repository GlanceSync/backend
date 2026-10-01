package com.glancesync.modules.user.dto;

import com.glancesync.modules.user.enums.LocationSource;
import io.micronaut.serde.annotation.Serdeable;

/** {@code accuracy} is the radius in metres the browser reported for a GPS fix. */
@Serdeable
public record LocationRequest(LocationSource source, Double latitude, Double longitude, Double accuracy,
                              String city) {
}
