package com.glancesync.web;

import java.util.Optional;

public final class Session {
    private final spark.Session delegate;
    public Session(spark.Session delegate) { this.delegate = delegate; }
    public void put(String key, Object value) { delegate.attribute(key, value); }
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = delegate.attribute(key);
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }
    public void remove(String key) { delegate.removeAttribute(key); }
}
