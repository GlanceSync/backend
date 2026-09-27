package com.glancesync.modules.auth.service;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.auth.dto.RegisterRequest;
import com.glancesync.modules.security.service.PasswordHasher;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.user.repo.UserDao;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class AuthService {

    private final UserDao userDao;
    private final PasswordHasher passwordHasher;
    private final CredentialRules rules;
    private final EmailVerificationService emailVerification;

    public AuthService(UserDao userDao, PasswordHasher passwordHasher, CredentialRules rules,
                       EmailVerificationService emailVerification) {
        this.userDao = userDao;
        this.passwordHasher = passwordHasher;
        this.rules = rules;
        this.emailVerification = emailVerification;
    }

    /** Creates an unverified account and emails the link that activates it. */
    public User register(RegisterRequest raw) {
        RegisterRequest request = new RegisterRequest(trim(raw.email()), raw.username(),
                trim(raw.firstName()), trim(raw.lastName()), raw.password(), raw.confirmPassword());

        Map<String, String> errors = rules.validateRegistration(request);
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.", errors);
        }

        Map<String, String> taken = new HashMap<>();
        if (userDao.usernameExists(request.username())) {
            taken.put("username", "That username is already taken.");
        }
        if (userDao.emailExists(request.email())) {
            taken.put("email", "An account already uses that email address.");
        }
        if (!taken.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "Please correct the highlighted fields.", taken);
        }

        UUID id = userDao.create(request.username(), request.email(), request.firstName(),
                request.lastName(), passwordHasher.hash(request.password()));
        User user = userDao.findById(id).orElseThrow(
                () -> new IllegalStateException("User vanished immediately after creation: " + id));
        emailVerification.sendLink(user);
        return user;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * Verifies credentials. Failures are deliberately indistinguishable from each other,
     * in both message and timing, so the endpoint cannot be used to enumerate usernames.
     */
    public User login(String username, String password) {
        ApiException invalid = new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect username or password.");

        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw invalid;
        }

        User user = userDao.findByUsername(username).orElse(null);
        if (user == null) {
            passwordHasher.wasteTime(password);
            throw invalid;
        }

        if (!passwordHasher.verify(password, user.passwordHash())) {
            throw invalid;
        }

        // Only reached with the right password, so it reveals nothing to a guesser.
        if (!user.emailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Please confirm your email address first. We sent you a link when you signed up.");
        }

        userDao.touchLastSeen(user.id());
        return user;
    }
}
