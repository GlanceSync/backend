package com.glancesync.modules.auth.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record RegisterRequest(String email, String username, String firstName, String lastName,
                              String password, String confirmPassword) {
}
