package com.glancesync.common.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public record ErrorResponse(String message, Map<String, String> fields) {
    public static ErrorResponse of(String message) {
        return new ErrorResponse(message, Map.of());
    }
}
