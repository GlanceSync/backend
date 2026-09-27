package com.glancesync.modules.call.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record IceServersResponse(List<IceServer> iceServers) {
}
