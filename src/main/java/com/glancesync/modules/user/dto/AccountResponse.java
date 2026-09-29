package com.glancesync.modules.user.dto;

import java.util.UUID;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AccountResponse(UUID id, String username, String email, String firstName, String lastName,
                              String pendingEmail) {
}
