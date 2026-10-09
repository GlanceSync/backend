package com.glancesync.modules.auth.service;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.auth.repo.PasswordResetDao;
import com.glancesync.modules.auth.repo.RefreshTokenDao;
import com.glancesync.modules.mail.service.AccountMails;
import com.glancesync.modules.security.service.PasswordHasher;
import com.glancesync.modules.security.service.SecureTokens;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.user.repo.UserDao;
import com.glancesync.web.Value;
import com.glancesync.web.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class PasswordResetService {

    private static final int RESEND_COOLDOWN_SECONDS = 60;

    private final PasswordResetDao resetDao;
    private final RefreshTokenDao refreshTokenDao;
    private final UserDao userDao;
    private final PasswordHasher passwordHasher;
    private final CredentialRules rules;
    private final AccountMails accountMails;
    private final String appUrl;
    private final Duration linkTtl;

    public PasswordResetService(PasswordResetDao resetDao, RefreshTokenDao refreshTokenDao, UserDao userDao,
                                PasswordHasher passwordHasher, CredentialRules rules, AccountMails accountMails,
                                @Value("${glancesync.app-url:http://localhost:4200}") String appUrl,
                                @Value("${glancesync.security.password-reset-expiration-minutes:30}")
                                int expirationMinutes) {
        this.resetDao = resetDao;
        this.refreshTokenDao = refreshTokenDao;
        this.userDao = userDao;
        this.passwordHasher = passwordHasher;
        this.rules = rules;
        this.accountMails = accountMails;
        this.appUrl = appUrl.replaceAll("/+$", "");
        this.linkTtl = Duration.ofMinutes(expirationMinutes);
    }

    /** Silent whether or not the address has an account, so it cannot be used to probe for one. */
    public void request(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userDao.findByEmail(email.trim())
                .filter(user -> !resetDao.sentWithin(user.id(), RESEND_COOLDOWN_SECONDS))
                .ifPresent(this::sendLink);
    }

    public void reset(String token, String password, String confirmPassword) {
        ApiException invalidLink = new ApiException(HttpStatus.BAD_REQUEST,
                "This reset link is invalid or has expired. Ask for a new one.");
        if (token == null || !token.matches("^[0-9a-f]{64}$")) {
            throw invalidLink;
        }
        String tokenHash = SecureTokens.sha256(token);
        User user = resetDao.findLive(tokenHash).flatMap(userDao::findById).orElseThrow(() -> invalidLink);

        Map<String, String> errors = rules.validateNewPassword(password, confirmPassword, user.username(),
                user.firstName(), user.lastName(), user.email());
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.", errors);
        }

        UUID userId = resetDao.consume(tokenHash).orElseThrow(() -> invalidLink);
        userDao.updatePasswordHash(userId, passwordHasher.hash(password));
        userDao.markEmailVerified(userId);
        resetDao.deleteForUser(userId);
        refreshTokenDao.deleteAllFor(userId);
    }

    private void sendLink(User user) {
        String token = SecureTokens.generate();
        resetDao.create(SecureTokens.sha256(token), user.id(), Instant.now().plus(linkTtl));
        accountMails.sendPasswordReset(user.email(), user.firstName(), appUrl + "/reset-password?token=" + token,
                linkTtl);
    }
}
