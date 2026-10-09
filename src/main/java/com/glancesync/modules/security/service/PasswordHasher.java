package com.glancesync.modules.security.service;

import at.favre.lib.crypto.bcrypt.BCrypt;

public class PasswordHasher {

    private static final int COST = 12;

    public String hash(String plaintext) {
        return BCrypt.withDefaults().hashToString(COST, plaintext.toCharArray());
    }

    public boolean verify(String plaintext, String hash) {
        return BCrypt.verifyer().verify(plaintext.toCharArray(), hash).verified;
    }
}
