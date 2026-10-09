package com.glancesync.web;

import com.glancesync.web.cookie.Cookie;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpResponse<T> {
    private final int status;
    private T body;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private Cookie cookie;

    private HttpResponse(int status, T body) { this.status = status; this.body = body; }
    public static <T> HttpResponse<T> ok(T body) { return new HttpResponse<>(200, body); }
    public static <T> HttpResponse<T> created(T body) { return new HttpResponse<>(201, body); }
    public static HttpResponse<Object> accepted() { return new HttpResponse<>(202, null); }
    public static HttpResponse<Object> noContent() { return new HttpResponse<>(204, null); }
    public static <T> HttpResponse<T> notFound() { return new HttpResponse<>(404, null); }
    public static <T> HttpResponse<T> redirect(URI uri) { return new HttpResponse<T>(302, null).header("Location", uri.toString()); }
    public static HttpResponse<Object> seeOther(URI uri) { return status(HttpStatus.SEE_OTHER).header("Location", uri.toString()); }
    public static <T> HttpResponse<T> status(HttpStatus status) { return new HttpResponse<>(status.getCode(), null); }
    public HttpResponse<T> body(T body) { this.body = body; return this; }
    public HttpResponse<T> header(String name, String value) { headers.put(name, value); return this; }
    public HttpResponse<T> contentType(String value) { return header("Content-Type", value); }
    public HttpResponse<T> cookie(Cookie value) { this.cookie = value; return this; }
    public int status() { return status; }
    public T body() { return body; }
    public Map<String, String> headers() { return headers; }
    public Cookie cookie() { return cookie; }
}
