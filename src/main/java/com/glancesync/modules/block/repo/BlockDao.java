package com.glancesync.modules.block.repo;

import java.util.UUID;
import com.glancesync.common.db.Database;

import java.util.List;
import java.util.Set;

public class BlockDao {

    private final Database database;

    public BlockDao(Database database) {
        this.database = database;
    }

    public void block(UUID blockerId, UUID blockedId) {
        // Blocking twice is not an error; it just stays blocked.
        database.update(
                "INSERT INTO blocks (blocker_id, blocked_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                blockerId, blockedId);
    }

    public int unblock(UUID blockerId, UUID blockedId) {
        return database.update(
                "DELETE FROM blocks WHERE blocker_id = ? AND blocked_id = ?",
                blockerId, blockedId);
    }

    /** True when either person has blocked the other, in either direction. */
    public boolean blockedEitherWay(UUID userA, UUID userB) {
        return database.queryOne(
                "SELECT 1 FROM blocks WHERE (blocker_id = ? AND blocked_id = ?) "
                        + "OR (blocker_id = ? AND blocked_id = ?)",
                rs -> true,
                userA, userB, userB, userA).isPresent();
    }

    /** Everyone this user has blocked. */
    public Set<UUID> blockedBy(UUID blockerId) {
        List<UUID> ids = database.query(
                "SELECT blocked_id FROM blocks WHERE blocker_id = ?",
                rs -> rs.getObject("blocked_id", UUID.class),
                blockerId);
        return Set.copyOf(ids);
    }

    /** Everyone who has blocked this user. */
    public Set<UUID> blockersOf(UUID blockedId) {
        List<UUID> ids = database.query(
                "SELECT blocker_id FROM blocks WHERE blocked_id = ?",
                rs -> rs.getObject("blocker_id", UUID.class),
                blockedId);
        return Set.copyOf(ids);
    }
}
