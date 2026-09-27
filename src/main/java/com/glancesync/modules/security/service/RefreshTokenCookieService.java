package com.glancesync.modules.security.service;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.SameSite;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.Optional;

/**
 * The refresh token's cookie. HttpOnly keeps it away from page scripts, and the path
 * means the browser only sends it to /api/auth (refresh, logout), never elsewhere.
 */
@Singleton
public class RefreshTokenCookieService {

    public static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/auth";

    private final boolean cookieSecure;
    private final Duration maxAge;

    public RefreshTokenCookieService(JwtService jwtService,
                                     @Value("${glancesync.security.cookie-secure:false}") boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
        this.maxAge = jwtService.refreshTtl();
    }

    public Cookie build(String refreshToken) {
        return baseCookie(refreshToken).maxAge(maxAge);
    }

    public Cookie buildExpired() {
        return baseCookie("").maxAge(Duration.ZERO);
    }

    public Optional<String> extract(HttpRequest<?> request) {
        return request.getCookies()
                .findCookie(COOKIE_NAME)
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank());
    }

    private Cookie baseCookie(String value) {
        return Cookie.of(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path(COOKIE_PATH)
                .sameSite(SameSite.Strict);
    }
}
