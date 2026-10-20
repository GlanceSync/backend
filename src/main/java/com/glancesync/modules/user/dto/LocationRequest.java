package com.glancesync.modules.user.dto;

import com.glancesync.modules.user.enums.LocationSource;

/** {@code accuracy} is the radius in metres the browser reported for a GPS fix. */
public record LocationRequest(LocationSource source, Double latitude, Double longitude, Double accuracy,
                              String city) {
}
