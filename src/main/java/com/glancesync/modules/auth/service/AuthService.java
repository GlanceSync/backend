package com.glancesync.modules.auth.service;

import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.user.repo.UserDao;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class AuthService {

    private final UserDao userDao;
    private final PasswordHasher passwordHasher;
    private final CredentialRules rules;

    public AuthService(UserDao userDao, PasswordHasher passwordHasher, CredentialRules rules) {
        this.userDao = userDao;
        this.passwordHasher = passwordHasher;
        this.rules = rules;
    }

    public User register(String username, String password, String confirmPassword) {
        Map<String, String> errors = rules.validateRegistration(username, password, confirmPassword);
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.", errors);
        }

        if (userDao.usernameExists(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "That username is already taken.",
                    Map.of("username", "That username is already taken."));
        }

        long id = userDao.create(username, passwordHasher.hash(password));
        return userDao.findById(id).orElseThrow(
                () -> new IllegalStateException("User vanished immediately after creation: " + id));
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

        userDao.touchLastSeen(user.id());
        return user;
    }
}
