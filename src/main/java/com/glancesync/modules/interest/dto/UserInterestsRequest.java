package com.glancesync.modules.interest.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record UserInterestsRequest(List<String> names) {
}
