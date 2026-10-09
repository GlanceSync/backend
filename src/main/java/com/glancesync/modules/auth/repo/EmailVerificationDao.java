package com.glancesync.modules.auth.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public class EmailVerificationDao {

    private final Database database;

    public EmailVerificationDao(Database database) {
        this.database = database;
    }

    public void create(String tokenHash, UUID userId, Instant expiresAt) {
        database.update(
                "INSERT INTO email_verifications (token_hash, user_id, expires_at) VALUES (?, ?, ?)",
                tokenHash, userId, Timestamp.from(expiresAt));
    }

    /** A link confirming that the user owns {@code newEmail}, which replaces their address once followed. */
    public void createForEmailChange(String tokenHash, UUID userId, String newEmail, Instant expiresAt) {
        database.update(
                "INSERT INTO email_verifications (token_hash, user_id, new_email, expires_at) VALUES (?, ?, ?, ?)",
                tokenHash, userId, newEmail, Timestamp.from(expiresAt));
    }

    public void deleteEmailChangesFor(UUID userId) {
        database.update("DELETE FROM email_verifications WHERE user_id = ? AND new_email IS NOT NULL", userId);
    }

    public Optional<String> pendingEmailFor(UUID userId) {
        return database.queryOne(
                "SELECT new_email FROM email_verifications WHERE user_id = ? AND new_email IS NOT NULL "
                        + "AND expires_at > now() ORDER BY created_at DESC LIMIT 1",
                rs -> rs.getString("new_email"),
                userId);
    }

    /**
     * Consumes the token and marks its user verified, in one statement so a link can
     * never be used twice. An expired token is consumed too, but verifies nobody. A token
     * for an email change also swaps in the new address.
     */
    public Optional<UUID> consume(String tokenHash) {
        return database.queryOne(
                "WITH used AS (DELETE FROM email_verifications WHERE token_hash = ? "
                        + "RETURNING user_id, new_email, expires_at) "
                        + "UPDATE users SET email_verified_at = COALESCE(email_verified_at, now()), "
                        + "email = COALESCE(used.new_email, users.email) "
                        + "FROM used WHERE users.id = used.user_id AND used.expires_at > now() "
                        + "RETURNING users.id",
                rs -> rs.getObject("id", UUID.class),
                tokenHash);
    }

    /** True when a link was sent to this user recently, to stop resend spam. */
    public boolean sentWithin(UUID userId, int seconds) {
        return database.queryOne(
                "SELECT 1 FROM email_verifications "
                        + "WHERE user_id = ? AND created_at > now() - make_interval(secs => ?)",
                rs -> true,
                userId, seconds).isPresent();
    }

    public void deleteForUser(UUID userId) {
        database.update("DELETE FROM email_verifications WHERE user_id = ?", userId);
    }
}
