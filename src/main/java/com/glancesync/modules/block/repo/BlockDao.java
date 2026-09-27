package com.glancesync.modules.block.repo;

import com.glancesync.common.db.Database;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

@Singleton
public class BlockDao {

    private final Database database;

    public BlockDao(Database database) {
        this.database = database;
    }

    public void block(long blockerId, long blockedId) {
        // Blocking twice is not an error; it just stays blocked.
        database.update(
                "INSERT INTO blocks (blocker_id, blocked_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                blockerId, blockedId);
    }

    public int unblock(long blockerId, long blockedId) {
        return database.update(
                "DELETE FROM blocks WHERE blocker_id = ? AND blocked_id = ?",
                blockerId, blockedId);
    }

    /** True when either person has blocked the other, in either direction. */
    public boolean blockedEitherWay(long userA, long userB) {
        return database.queryOne(
                "SELECT 1 FROM blocks WHERE (blocker_id = ? AND blocked_id = ?) "
                        + "OR (blocker_id = ? AND blocked_id = ?)",
                rs -> true,
                userA, userB, userB, userA).isPresent();
    }

    /** Everyone this user has blocked. */
    public Set<Long> blockedBy(long blockerId) {
        List<Long> ids = database.query(
                "SELECT blocked_id FROM blocks WHERE blocker_id = ?",
                rs -> rs.getLong("blocked_id"),
                blockerId);
        return Set.copyOf(ids);
    }

    /** Everyone who has blocked this user. */
    public Set<Long> blockersOf(long blockedId) {
        List<Long> ids = database.query(
                "SELECT blocker_id FROM blocks WHERE blocked_id = ?",
                rs -> rs.getLong("blocker_id"),
                blockedId);
        return Set.copyOf(ids);
    }
}
