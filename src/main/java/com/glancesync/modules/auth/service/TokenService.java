package com.glancesync.modules.auth.service;

import java.util.UUID;
import com.glancesync.modules.auth.repo.RefreshTokenDao;
import com.glancesync.modules.security.service.JwtService;
import com.glancesync.modules.security.service.SecureTokens;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.user.repo.UserDao;

import java.time.Instant;
import java.util.Optional;

/**
 * Issues, rotates and revokes token pairs. Every refresh token is recorded (as a hash),
 * so logging out really ends it, and each one can be exchanged only once.
 */
public class TokenService {

    public record Tokens(User user, String accessToken, String refreshToken) {
    }

    private final JwtService jwtService;
    private final RefreshTokenDao refreshTokenDao;
    private final UserDao userDao;

    public TokenService(JwtService jwtService, RefreshTokenDao refreshTokenDao, UserDao userDao) {
        this.jwtService = jwtService;
        this.refreshTokenDao = refreshTokenDao;
        this.userDao = userDao;
    }

    public Tokens issue(User user) {
        String refreshToken = jwtService.generateRefreshToken(user);
        refreshTokenDao.deleteExpiredFor(user.id());
        refreshTokenDao.create(SecureTokens.sha256(refreshToken), user.id(),
                Instant.now().plus(jwtService.refreshTtl()));
        return new Tokens(user, jwtService.generateAccessToken(user), refreshToken);
    }

    /** Swaps a refresh token for a new pair, or empty if it is invalid, expired or already used. */
    public Optional<Tokens> refresh(String refreshToken) {
        UUID userId = jwtService.parseRefreshToken(refreshToken).orElse(null);
        if (userId == null) {
            return Optional.empty();
        }
        return refreshTokenDao.consume(SecureTokens.sha256(refreshToken))
                .filter(userId::equals)
                .flatMap(userDao::findById)
                .map(this::issue);
    }

    public void revoke(String refreshToken) {
        refreshTokenDao.delete(SecureTokens.sha256(refreshToken));
    }

    public long accessTokenExpiresInSeconds() {
        return jwtService.accessTtl().toSeconds();
    }
}
