package com.glancesync.modules.auth.service;

import com.glancesync.modules.auth.repo.SessionDao;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.SameSite;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Hand-written session management. The raw token is only ever held by the client,
 * in an HttpOnly cookie; the database stores its SHA-256 so a leaked dump cannot be
 * replayed as a live session.
 *
 * A cookie is used rather than a bearer token because the browser WebSocket API
 * cannot set request headers. The cookie rides the WebSocket handshake automatically,
 * which keeps the real-time layer authenticated without putting a token in the URL.
 */
@Singleton
public class SessionService {

    public static final String COOKIE_NAME = "sid";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SessionDao sessionDao;
    private final Duration ttl;
    private final boolean cookieSecure;

    public SessionService(SessionDao sessionDao,
                          @Value("${glancesync.session.ttl-days:7}") int ttlDays,
                          @Value("${glancesync.session.cookie-secure:false}") boolean cookieSecure) {
        this.sessionDao = sessionDao;
        this.ttl = Duration.ofDays(ttlDays);
        this.cookieSecure = cookieSecure;
    }

    /** Creates a session and returns the raw token to hand to the client. */
    public String startSession(long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        sessionDao.create(sha256(token), userId, Instant.now().plus(ttl));
        return token;
    }

    public Optional<Long> resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sessionDao.findUserId(sha256(token));
    }

    public void endSession(String token) {
        if (token != null && !token.isBlank()) {
            sessionDao.delete(sha256(token));
        }
    }

    public Cookie buildCookie(String token) {
        return baseCookie(token).maxAge(ttl);
    }

    public Cookie buildExpiredCookie() {
        return baseCookie("").maxAge(Duration.ZERO);
    }

    private Cookie baseCookie(String value) {
        return Cookie.of(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite(SameSite.Lax);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
