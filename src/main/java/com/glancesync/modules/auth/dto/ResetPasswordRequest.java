package com.glancesync.modules.auth.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ResetPasswordRequest(String token, String password, String confirmPassword) {
}
