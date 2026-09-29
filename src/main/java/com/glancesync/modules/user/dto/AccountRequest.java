package com.glancesync.modules.user.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AccountRequest(String firstName, String lastName, String email) {
}
