package com.glancesync.modules.security.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.inject.Singleton;

/**
 * bcrypt hashing. A hashing primitive, not a user-account framework: sessions,
 * validation and login flow are written by hand in the auth module.
 */
@Singleton
public class PasswordHasher {

    private static final int COST = 12;

    /** A valid hash of a throwaway value, used to keep failed logins constant-time. */
    private static final String DUMMY_HASH =
            BCrypt.withDefaults().hashToString(COST, "not-a-real-password".toCharArray());

    public String hash(String plaintext) {
        return BCrypt.withDefaults().hashToString(COST, plaintext.toCharArray());
    }

    public boolean verify(String plaintext, String hash) {
        return BCrypt.verifyer().verify(plaintext.toCharArray(), hash).verified;
    }

    /**
     * Runs a verification against a dummy hash so that logging in with an unknown
     * username costs the same as logging in with a known one. Without this, response
     * timing reveals which usernames exist.
     */
    public void wasteTime(String plaintext) {
        BCrypt.verifyer().verify(plaintext.toCharArray(), DUMMY_HASH);
    }
}
