package com.glancesync.web.cookie;

import java.time.Duration;

public final class Cookie {
    private final String name;
    private final String value;
    private boolean httpOnly;
    private boolean secure;
    private String path = "/";
    private SameSite sameSite = SameSite.Lax;
    private Duration maxAge;

    private Cookie(String name, String value) { this.name = name; this.value = value; }
    public static Cookie of(String name, String value) { return new Cookie(name, value); }
    public Cookie httpOnly(boolean value) { httpOnly = value; return this; }
    public Cookie secure(boolean value) { secure = value; return this; }
    public Cookie path(String value) { path = value; return this; }
    public Cookie sameSite(SameSite value) { sameSite = value; return this; }
    public Cookie maxAge(Duration value) { maxAge = value; return this; }
    public String name() { return name; }
    public String value() { return value; }
    public boolean httpOnly() { return httpOnly; }
    public boolean secure() { return secure; }
    public String path() { return path; }
    public SameSite sameSite() { return sameSite; }
    public Duration maxAge() { return maxAge; }
}
