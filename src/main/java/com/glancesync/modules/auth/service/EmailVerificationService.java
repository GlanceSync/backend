package com.glancesync.modules.auth.service;

import java.util.UUID;
import com.glancesync.common.db.DatabaseException;
import com.glancesync.modules.auth.repo.EmailVerificationDao;
import com.glancesync.modules.mail.service.AccountMails;
import com.glancesync.modules.security.service.SecureTokens;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.user.repo.UserDao;
import com.glancesync.web.Value;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Singleton
public class EmailVerificationService {

    private static final int RESEND_COOLDOWN_SECONDS = 60;

    private final EmailVerificationDao verificationDao;
    private final UserDao userDao;
    private final AccountMails accountMails;
    private final String appUrl;
    private final Duration linkTtl;

    public EmailVerificationService(EmailVerificationDao verificationDao, UserDao userDao,
                                    AccountMails accountMails,
                                    @Value("${glancesync.app-url:http://localhost:4200}") String appUrl,
                                    @Value("${glancesync.security.verification-expiration-minutes:15}")
                                    int expirationMinutes) {
        this.verificationDao = verificationDao;
        this.userDao = userDao;
        this.accountMails = accountMails;
        this.appUrl = appUrl.replaceAll("/+$", "");
        this.linkTtl = Duration.ofMinutes(expirationMinutes);
    }

    public String appUrl() {
        return appUrl;
    }

    public void sendLink(User user) {
        String token = SecureTokens.generate();
        verificationDao.create(SecureTokens.sha256(token), user.id(), Instant.now().plus(linkTtl));

        String link = appUrl + "/api/auth/verify-email?token=" + token;
        accountMails.sendVerification(user.email(), user.firstName(), link, linkTtl);
    }

    /** Sends a link to {@code newEmail}; the account switches to it only once the link is followed. */
    public void sendEmailChangeLink(User user, String newEmail) {
        verificationDao.deleteEmailChangesFor(user.id());
        String token = SecureTokens.generate();
        verificationDao.createForEmailChange(SecureTokens.sha256(token), user.id(), newEmail,
                Instant.now().plus(linkTtl));

        String link = appUrl + "/api/auth/verify-email?token=" + token;
        accountMails.sendEmailChange(newEmail, user.firstName(), link, linkTtl);
    }

    public Optional<String> pendingEmail(UUID userId) {
        return verificationDao.pendingEmailFor(userId);
    }

    public Optional<UUID> verify(String token) {
        if (token == null || !token.matches("^[0-9a-f]{64}$")) {
            return Optional.empty();
        }
        Optional<UUID> userId;
        try {
            userId = verificationDao.consume(SecureTokens.sha256(token));
        } catch (DatabaseException e) {
            // The new address was taken by another account after the link was sent.
            return Optional.empty();
        }
        userId.ifPresent(verificationDao::deleteForUser);
        return userId;
    }

    public void resend(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userDao.findByEmail(email.trim())
                .filter(user -> !user.emailVerified())
                .filter(user -> !verificationDao.sentWithin(user.id(), RESEND_COOLDOWN_SECONDS))
                .ifPresent(user -> {
                    verificationDao.deleteForUser(user.id());
                    sendLink(user);
                });
    }
}
