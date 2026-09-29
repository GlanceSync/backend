package com.glancesync.modules.call.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/** One entry of RTCConfiguration.iceServers; STUN entries carry no credentials. */
@Serdeable
public record IceServer(List<String> urls, String username, String credential) {
}
