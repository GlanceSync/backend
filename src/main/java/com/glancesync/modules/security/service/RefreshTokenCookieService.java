package com.glancesync.modules.security.service;

import com.glancesync.web.Value;
import com.glancesync.web.HttpRequest;
import com.glancesync.web.cookie.Cookie;
import com.glancesync.web.cookie.SameSite;

import java.time.Duration;
import java.util.Optional;

/**
 * The refresh token's cookie. HttpOnly keeps it away from page scripts, and the path
 * means the browser only sends it to /api/auth (refresh, logout), never elsewhere.
 */
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
                .map(Cookie::value)
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
