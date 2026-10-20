package com.glancesync.modules.auth.service;

import com.glancesync.modules.auth.dto.RegisterRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Validation rules for user credentials.
*/
public class CredentialRules {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 30;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final int EMAIL_MAX = 254;

    private static final Pattern NAME_PATTERN = Pattern.compile("^\\p{L}[\\p{L}\\p{M} '’-]*$");
    private static final int NAME_MAX = 50;

    private static final int PASSWORD_MIN = 8;

    private static final int PASSWORD_MAX = 72;

    private final Set<String> commonPasswords;

    public CredentialRules() {
        this.commonPasswords = loadCommonPasswords();
    }

    public Map<String, String> validateRegistration(RegisterRequest request) {
        Map<String, String> errors = new HashMap<>();

        putIfInvalid(errors, "email", validateEmail(request.email()));
        putIfInvalid(errors, "username", validateUsername(request.username()));
        putIfInvalid(errors, "firstName", validateName(request.firstName(), "First name"));
        putIfInvalid(errors, "lastName", validateName(request.lastName(), "Last name"));
        putIfInvalid(errors, "password", validatePassword(request.password(), request.username(),
                request.firstName(), request.lastName(), request.email()));
        putIfMismatched(errors, request.password(), request.confirmPassword());

        return errors;
    }

    public Map<String, String> validateAccount(String firstName, String lastName, String email) {
        Map<String, String> errors = new HashMap<>();
        putIfInvalid(errors, "firstName", validateName(firstName, "First name"));
        putIfInvalid(errors, "lastName", validateName(lastName, "Last name"));
        putIfInvalid(errors, "email", validateEmail(email));
        return errors;
    }

    /** A replacement password for an existing account, as in a reset. */
    public Map<String, String> validateNewPassword(String password, String confirmPassword, String username,
                                                   String firstName, String lastName, String email) {
        Map<String, String> errors = new HashMap<>();
        putIfInvalid(errors, "password", validatePassword(password, username, firstName, lastName, email));
        putIfMismatched(errors, password, confirmPassword);
        return errors;
    }

    private static void putIfMismatched(Map<String, String> errors, String password, String confirm) {
        if (confirm == null || confirm.isEmpty()) {
            errors.put("confirmPassword", "Please confirm your password.");
        } else if (password != null && !password.equals(confirm)) {
            errors.put("confirmPassword", "Passwords do not match.");
        }
    }

    private static void putIfInvalid(Map<String, String> errors, String field, String error) {
        if (error != null) {
            errors.put(field, error);
        }
    }

    private String validateEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "Email address is required.";
        }
        if (email.length() > EMAIL_MAX || !EMAIL_PATTERN.matcher(email).matches()) {
            return "Please enter a valid email address.";
        }
        return null;
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

    private String validateName(String name, String label) {
        if (name == null || name.isEmpty()) {
            return label + " is required.";
        }
        if (name.length() > NAME_MAX) {
            return label + " must be at most " + NAME_MAX + " characters.";
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            return label + " may only contain letters, spaces, hyphens and apostrophes.";
        }
        return null;
    }

    private String validatePassword(String password, String username, String firstName, String lastName,
                                    String email) {
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
        if (containsPersonalInfo(password, username, firstName, lastName, email)) {
            return "Password must not contain your username, name or email.";
        }
        if (password.chars().noneMatch(Character::isDigit)
                || password.chars().noneMatch(Character::isLetter)) {
            return "Password must contain at least one letter and one digit.";
        }
        return null;
    }

    /**
     * Checks the password against a list of common passwords, and also checks for simple variations
     */
    private boolean isCommon(String password) {
        String normalized = normalize(password);
        if (commonPasswords.contains(normalized)) {
            return true;
        }
        String stripped = normalized
                .replaceAll("^[0-9!@#$%^&*._-]+", "")
                .replaceAll("[0-9!@#$%^&*._-]+$", "");
        if (!stripped.isEmpty() && commonPasswords.contains(stripped)) {
            return true;
        }
        String unleeted = normalized
                .replace('0', 'o').replace('1', 'i').replace('3', 'e').replace('4', 'a')
                .replace('5', 's').replace('7', 't').replace('@', 'a').replace('$', 's')
                .replaceAll("[0-9!#%^&*._-]+$", "");
        return !unleeted.isEmpty() && commonPasswords.contains(unleeted);
    }

    private boolean containsPersonalInfo(String password, String username, String firstName, String lastName,
                                         String email) {
        String normalized = normalize(password);
        String emailLocalPart = email == null ? null : email.split("@", 2)[0];
        return Stream.of(username, firstName, lastName, emailLocalPart)
                .filter(part -> part != null && part.length() >= 3)
                .map(CredentialRules::normalize)
                .anyMatch(normalized::contains);
    }

    /** Lowercase with accents removed, so "Été" and "ete" compare equal. */
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private Set<String> loadCommonPasswords() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("common-passwords.txt")) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: common-passwords.txt");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(line -> normalize(line.trim()))
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load common-passwords.txt", e);
        }
    }
}
