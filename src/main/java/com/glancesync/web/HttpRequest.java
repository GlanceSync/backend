package com.glancesync.web;

import com.glancesync.web.cookie.Cookie;
import spark.Request;

import java.util.Optional;

public final class HttpRequest<T> {
    private final Request delegate;
    public HttpRequest(Request delegate) { this.delegate = delegate; }
    public String getPath() { return delegate.pathInfo(); }
    public HttpMethod getMethod() { return HttpMethod.valueOf(delegate.requestMethod()); }
    public <V> Optional<V> getAttribute(String key, Class<V> type) {
        Object value = delegate.attribute(key);
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }
    public void setAttribute(String key, Object value) { delegate.attribute(key, value); }
    public Headers getHeaders() { return new Headers(); }
    public Cookies getCookies() { return new Cookies(); }
    public final class Headers {
        public Optional<String> findFirst(String name) { return Optional.ofNullable(delegate.headers(name)); }
    }
    public final class Cookies {
        public Optional<Cookie> findCookie(String name) {
            String value = delegate.cookie(name);
            return value == null ? Optional.empty() : Optional.of(Cookie.of(name, value));
        }
    }
}
