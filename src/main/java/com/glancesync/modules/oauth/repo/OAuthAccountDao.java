package com.glancesync.modules.oauth.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class OAuthAccountDao {

    private final Database database;

    public OAuthAccountDao(Database database) {
        this.database = database;
    }

    public Optional<UUID> findUserId(String provider, String providerUserId) {
        return database.queryOne(
                "SELECT user_id FROM user_oauth_accounts WHERE provider = ? AND provider_user_id = ?",
                rs -> rs.getObject("user_id", UUID.class),
                provider, providerUserId);
    }

    public void link(String provider, String providerUserId, UUID userId) {
        database.update(
                "INSERT INTO user_oauth_accounts (provider, provider_user_id, user_id) VALUES (?, ?, ?) "
                        + "ON CONFLICT (provider, provider_user_id) DO NOTHING",
                provider, providerUserId, userId);
    }
}
