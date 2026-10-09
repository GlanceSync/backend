package com.glancesync.modules.auth.dto;


public record RegisterRequest(String email, String username, String firstName, String lastName,
                              String password, String confirmPassword) {
}
