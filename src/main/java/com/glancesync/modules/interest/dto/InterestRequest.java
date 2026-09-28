package com.glancesync.modules.interest.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record InterestRequest(String name) {
}
