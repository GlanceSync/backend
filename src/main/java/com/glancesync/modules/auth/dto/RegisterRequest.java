package com.glancesync.modules.auth.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record RegisterRequest(String username, String password, String confirmPassword) {
}
