package com.glancesync.modules.auth.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record LoginRequest(String username, String password) {
}
