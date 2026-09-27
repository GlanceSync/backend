package com.glancesync.common.exception;

import io.micronaut.http.HttpStatus;

import java.util.Map;

/** An expected, client-facing failure. Carries the status the client should see. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> fields;

    public ApiException(HttpStatus status, String message) {
        this(status, message, Map.of());
    }

    public ApiException(HttpStatus status, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.fields = fields;
    }

    public HttpStatus status() {
        return status;
    }

    public Map<String, String> fields() {
        return fields;
    }
}
