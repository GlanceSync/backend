package com.glancesync.auth;

import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Hand-written validation. The subject forbids validator frameworks, so the rules
 * live here in plain Java and are the single source of truth for both endpoints.
 */
@Singleton
public class CredentialRules {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 30;
    private static final int PASSWORD_MIN = 8;
    /** bcrypt silently ignores bytes past 72, so longer passwords must be rejected. */
    private static final int PASSWORD_MAX = 72;

    private final Set<String> commonPasswords;

    public CredentialRules() {
        this.commonPasswords = loadCommonPasswords();
    }

    /** Returns field name -> message. Empty means valid. */
    public Map<String, String> validateRegistration(String username, String password, String confirm) {
        Map<String, String> errors = new HashMap<>();

        String usernameError = validateUsername(username);
        if (usernameError != null) {
            errors.put("username", usernameError);
        }

        String passwordError = validatePassword(password);
        if (passwordError != null) {
            errors.put("password", passwordError);
        }

        if (confirm == null || confirm.isEmpty()) {
            errors.put("confirmPassword", "Please confirm your password.");
        } else if (password != null && !password.equals(confirm)) {
            errors.put("confirmPassword", "Passwords do not match.");
        }

        return errors;
    }

    private String validateUsername(String username) {
        if (username == null || username.isBlank()) {
            return "Username is required.";
        }
        if (username.length() < USERNAME_MIN || username.length() > USERNAME_MAX) {
            return "Username must be between " + USERNAME_MIN + " and " + USERNAME_MAX + " characters.";
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return "Username may only contain letters, numbers and underscores.";
        }
        return null;
    }

    private String validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return "Password is required.";
        }
        if (password.length() < PASSWORD_MIN) {
            return "Password must be at least " + PASSWORD_MIN + " characters.";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX) {
            return "Password must be at most " + PASSWORD_MAX + " bytes.";
        }
        if (isCommon(password)) {
            return "That password is too common. Please choose a less predictable one.";
        }
        if (password.chars().noneMatch(Character::isDigit)
                || password.chars().noneMatch(Character::isLetter)) {
            return "Password must contain at least one letter and one digit.";
        }
        return null;
    }

    /**
     * Rejects dictionary passwords, including the usual decorations people add to them
     * (trailing digits, a trailing !, or digit-for-letter substitutions).
     */
    private boolean isCommon(String password) {
        String normalized = password.toLowerCase(Locale.ROOT);
        if (commonPasswords.contains(normalized)) {
            return true;
        }
        String stripped = normalized.replaceAll("[0-9!@#$%^&*._-]+$", "");
        if (!stripped.isEmpty() && commonPasswords.contains(stripped)) {
            return true;
        }
        String unleeted = stripped
                .replace('0', 'o').replace('1', 'i').replace('3', 'e')
                .replace('4', 'a').replace('5', 's').replace('7', 't').replace('@', 'a');
        return !unleeted.isEmpty() && commonPasswords.contains(unleeted);
    }

    private Set<String> loadCommonPasswords() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("common-passwords.txt")) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: common-passwords.txt");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(line -> line.trim().toLowerCase(Locale.ROOT))
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load common-passwords.txt", e);
        }
    }
}
