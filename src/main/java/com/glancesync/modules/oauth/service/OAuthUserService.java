package com.glancesync.modules.oauth.service;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.oauth.dto.OAuthUserResponse;
import com.glancesync.modules.oauth.enums.AuthProvider;
import com.glancesync.modules.oauth.repo.OAuthAccountDao;
import com.glancesync.modules.security.service.PasswordHasher;
import com.glancesync.modules.security.service.SecureTokens;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.user.repo.PictureDao;
import com.glancesync.modules.user.repo.UserDao;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Turns a 42, Google or Facebook account into a user. The first login links the
 * provider account to the user with the same verified email, or creates a new user.
 */
@Singleton
public class OAuthUserService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 30;
    private static final int NAME_MAX = 50;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OAuthAccountDao accountDao;
    private final UserDao userDao;
    private final PictureDao pictureDao;
    private final PasswordHasher passwordHasher;

    public OAuthUserService(OAuthAccountDao accountDao, UserDao userDao, PictureDao pictureDao,
                            PasswordHasher passwordHasher) {
        this.accountDao = accountDao;
        this.userDao = userDao;
        this.pictureDao = pictureDao;
        this.passwordHasher = passwordHasher;
    }

    /** Finds, links or creates the user behind the provider account. */
    public User findOrCreateOAuthUser(AuthProvider provider, OAuthUserResponse oauthUser) {
        if (oauthUser.providerId() == null || oauthUser.providerId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The provider did not identify the account.");
        }

        User user = accountDao.findUserId(provider.key(), oauthUser.providerId())
                .flatMap(userDao::findById)
                .orElseGet(() -> linkOrCreate(provider, oauthUser));

        userDao.touchLastSeen(user.id());
        return user;
    }

    private User linkOrCreate(AuthProvider provider, OAuthUserResponse oauthUser) {
        String email = oauthUser.email() == null ? null : oauthUser.email().trim();
        if (email == null || !EMAIL_PATTERN.matcher(email).matches() || email.length() > 254) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Your account at that provider did not share an email address.");
        }
        // Linking on an unverified address would let anyone claim someone else's account.
        if (!oauthUser.emailVerified()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Your email address at that provider is not verified.");
        }

        User user = userDao.findByEmail(email).orElseGet(() -> create(oauthUser, email));
        accountDao.link(provider.key(), oauthUser.providerId(), user.id());
        userDao.markEmailVerified(user.id());
        return userDao.findById(user.id()).orElseThrow();
    }

    private User create(OAuthUserResponse oauthUser, String email) {
        String username = uniqueUsername(oauthUser.username(), email);
        String firstName = name(oauthUser.firstName(), username);
        String lastName = name(oauthUser.lastName(), firstName);
        // No password to sign in with; "forgot password" sets one if they ever want it.
        String unusablePassword = passwordHasher.hash(SecureTokens.generate());

        UUID id = userDao.create(username, email, firstName, lastName, unusablePassword);

        String avatar = oauthUser.imageUrl();
        if (avatar != null && avatar.startsWith("https://") && avatar.length() <= 2048) {
            pictureDao.addExternal(UUID.randomUUID(), id, avatar);
            pictureDao.ensureProfile(id);
            userDao.refreshProfileCompleted(id);
        }

        return userDao.findById(id).orElseThrow(
                () -> new IllegalStateException("User vanished immediately after creation: " + id));
    }

    private String uniqueUsername(String suggested, String email) {
        String base = sanitizeUsername(suggested);
        if (base == null) {
            base = sanitizeUsername(email.substring(0, email.indexOf('@')));
        }
        if (base == null) {
            base = "user";
        }
        if (!userDao.usernameExists(base)) {
            return base;
        }
        String stem = base.length() > USERNAME_MAX - 5 ? base.substring(0, USERNAME_MAX - 5) : base;
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = stem + "_" + (1000 + RANDOM.nextInt(9000));
            if (!userDao.usernameExists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find a free username for " + base);
    }

    private static String sanitizeUsername(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replaceAll("[^A-Za-z0-9_]", "_").replaceAll("_+", "_");
        if (cleaned.length() > USERNAME_MAX) {
            cleaned = cleaned.substring(0, USERNAME_MAX);
        }
        return cleaned.replace("_", "").length() < USERNAME_MIN ? null : cleaned;
    }

    private static String name(String raw, String fallback) {
        String value = raw == null || raw.isBlank() ? fallback : raw.trim();
        return value.length() > NAME_MAX ? value.substring(0, NAME_MAX) : value;
    }
}
