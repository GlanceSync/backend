package com.glancesync.modules.auth.dto;


public record ResetPasswordRequest(String token, String password, String confirmPassword) {
}
