package com.glancesync.web;

public enum HttpStatus {
    SEE_OTHER(303),
    BAD_REQUEST(400), UNAUTHORIZED(401), FORBIDDEN(403), NOT_FOUND(404),
    CONFLICT(409), REQUEST_ENTITY_TOO_LARGE(413), UNSUPPORTED_MEDIA_TYPE(415), UNPROCESSABLE_ENTITY(422),
    BAD_GATEWAY(502);

    private final int code;
    HttpStatus(int code) { this.code = code; }
    public int getCode() { return code; }
}
