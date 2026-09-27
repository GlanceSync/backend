package com.glancesync.modules.chat.repo;

import com.glancesync.common.db.Database;
import com.glancesync.modules.chat.entity.Message;
import jakarta.inject.Singleton;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class MessageDao {

    private static final String COLUMNS =
            "id, sender_id, recipient_id, body, created_at, read_at";

    private final Database database;

    public MessageDao(Database database) {
        this.database = database;
    }

    public long insert(long senderId, long recipientId, String body) {
        return database.insertReturningId(
                "INSERT INTO messages (sender_id, recipient_id, body) VALUES (?, ?, ?)",
                senderId, recipientId, body);
    }

    public Optional<Message> findById(long id) {
        return database.queryOne(
                "SELECT " + COLUMNS + " FROM messages WHERE id = ?",
                MessageDao::mapMessage,
                id);
    }

    /**
     * The conversation between two users, oldest first. Both directions live in one
     * table, so the pair is matched in either order.
     */
    public List<Message> conversation(long userA, long userB, int limit) {
        // Newest-first in SQL so LIMIT keeps the most recent messages, then flipped
        // back to oldest-first for display.
        List<Message> newestFirst = database.query(
                "SELECT " + COLUMNS + " FROM messages "
                        + "WHERE (sender_id = ? AND recipient_id = ?) "
                        + "   OR (sender_id = ? AND recipient_id = ?) "
                        + "ORDER BY created_at DESC, id DESC LIMIT ?",
                MessageDao::mapMessage,
                userA, userB, userB, userA, limit);
        List<Message> oldestFirst = new ArrayList<>(newestFirst);
        Collections.reverse(oldestFirst);
        return oldestFirst;
    }

    /** Marks everything the other person sent me as read. @return how many changed. */
    public int markConversationRead(long readerId, long otherUserId) {
        return database.update(
                "UPDATE messages SET read_at = now() "
                        + "WHERE recipient_id = ? AND sender_id = ? AND read_at IS NULL",
                readerId, otherUserId);
    }

    /** Unread counts for one user, keyed by who sent them. */
    public Map<Long, Integer> unreadCountsBySender(long recipientId) {
        return database.query(
                "SELECT sender_id, COUNT(*) AS unread FROM messages "
                        + "WHERE recipient_id = ? AND read_at IS NULL GROUP BY sender_id",
                rs -> Map.entry(rs.getLong("sender_id"), rs.getInt("unread")),
                recipientId)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * The newest message exchanged with each other person, keyed by that person's id.
     * DISTINCT ON keeps one row per counterpart, which is what a conversation list needs.
     */
    public Map<Long, Message> latestPerCounterpart(long selfId) {
        List<Map.Entry<Long, Message>> rows = database.query(
                "SELECT DISTINCT ON (other_id) other_id, id, sender_id, recipient_id, body, created_at, read_at "
                        + "FROM (SELECT CASE WHEN sender_id = ? THEN recipient_id ELSE sender_id END AS other_id, "
                        + "             id, sender_id, recipient_id, body, created_at, read_at "
                        + "      FROM messages WHERE sender_id = ? OR recipient_id = ?) threads "
                        + "ORDER BY other_id, created_at DESC, id DESC",
                rs -> Map.entry(rs.getLong("other_id"), mapMessage(rs)),
                selfId, selfId, selfId);

        Map<Long, Message> latest = new HashMap<>();
        rows.forEach(row -> latest.put(row.getKey(), row.getValue()));
        return latest;
    }

    private static Message mapMessage(ResultSet rs) throws SQLException {
        return new Message(
                rs.getLong("id"),
                rs.getLong("sender_id"),
                rs.getLong("recipient_id"),
                rs.getString("body"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("read_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
