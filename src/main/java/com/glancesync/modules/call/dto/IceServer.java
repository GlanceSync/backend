package com.glancesync.modules.call.dto;


import java.util.List;

/** One entry of RTCConfiguration.iceServers; STUN entries carry no credentials. */
public record IceServer(List<String> urls, String username, String credential) {
}
