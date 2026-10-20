package com.glancesync.common.config;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads application.properties and resolves ${ENV:default} placeholders without a framework container. */
public final class AppConfig {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");
    private final Properties values = new Properties();

    public AppConfig() {
        loadDotEnv();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (in == null) throw new IllegalStateException("Missing application.properties");
            values.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load application.properties", e);
        }
    }

    private void loadDotEnv() {
        for (String dir : new String[]{".", "..", "../.."}) {
            Dotenv dotenv = Dotenv.configure().directory(dir).ignoreIfMalformed().ignoreIfMissing().load();
            dotenv.entries().forEach(entry -> {
                if (System.getenv(entry.getKey()) == null && System.getProperty(entry.getKey()) == null) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            });
        }
    }

    public String get(String key) {
        String raw = values.getProperty(key);
        if (raw == null) return null;
        Matcher matcher = PLACEHOLDER.matcher(raw);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String fallback = matcher.group(2);
            String value = System.getProperty(name);
            if (value == null) value = System.getenv(name);
            if (value == null) value = fallback;
            if (value == null) throw new IllegalStateException("Missing required setting " + name);
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    public String expression(String expression) {
        Matcher matcher = PLACEHOLDER.matcher(expression);
        if (!matcher.matches()) return expression;
        String key = matcher.group(1);
        String fallback = matcher.group(2);
        String value = get(key);
        return value == null ? fallback : value;
    }

    public int getInt(String key) { return Integer.parseInt(get(key)); }
    public boolean getBoolean(String key) { return Boolean.parseBoolean(get(key)); }

    public Object convert(String value, Class<?> type) {
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == long.class || type == Long.class) return Long.parseLong(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        if (type == URI.class) return URI.create(value);
        throw new IllegalArgumentException("Unsupported configuration type " + type.getName());
    }
}
