package com.glancesync.modules.security.service;

import java.util.UUID;
import com.glancesync.modules.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import com.glancesync.web.Value;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Signs and checks the two JWTs (HS256). The access token is short-lived and sent in
 * the Authorization header; the refresh token lives in an HttpOnly cookie and is only
 * good for getting a new pair. A "type" claim keeps one from being used as the other.
 */
public class JwtService {

    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(@Value("${glancesync.security.jwt.secret-key}") String secretKey,
                      @Value("${glancesync.security.jwt.expiration-time}") long accessTtlMs,
                      @Value("${glancesync.security.jwt.refresh-expiration-time}") long refreshTtlMs) {
        // Throws at startup when the secret is shorter than 256 bits.
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        this.accessTtl = Duration.ofMillis(accessTtlMs);
        this.refreshTtl = Duration.ofMillis(refreshTtlMs);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.id().toString())
                .claim("username", user.username())
                .claim(TYPE_CLAIM, ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** Carries a random id, so two refresh tokens issued in the same second still differ. */
    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.id().toString())
                .id(UUID.randomUUID().toString())
                .claim(TYPE_CLAIM, REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTtl)))
                .signWith(key)
                .compact();
    }

    /** The user id from a valid, unexpired access token. */
    public Optional<UUID> parseAccessToken(String token) {
        return parse(token, ACCESS);
    }

    /** The user id from a valid, unexpired refresh token. */
    public Optional<UUID> parseRefreshToken(String token) {
        return parse(token, REFRESH);
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    private Optional<UUID> parse(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.equals(claims.get(TYPE_CLAIM, String.class))) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            // Bad signature, expired, malformed, or a subject that is not a UUID.
            return Optional.empty();
        }
    }
}
