package com.glancesync.common.dto;


import java.util.Map;

public record ErrorResponse(String message, Map<String, String> fields) {
    public static ErrorResponse of(String message) {
        return new ErrorResponse(message, Map.of());
    }
}
