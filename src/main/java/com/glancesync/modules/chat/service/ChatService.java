package com.glancesync.modules.chat.service;

import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.block.service.BlockService;
import com.glancesync.modules.call.repo.CallDao;
import com.glancesync.modules.chat.dto.ChatEntry;
import com.glancesync.modules.chat.entity.Message;
import com.glancesync.modules.chat.repo.MessageDao;
import com.glancesync.modules.user.repo.UserDao;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Singleton
public class ChatService {

    /** Long enough for a real message, short enough to keep a row sane. */
    public static final int MAX_BODY_LENGTH = 2000;

    private static final int HISTORY_LIMIT = 200;

    private final MessageDao messageDao;
    private final CallDao callDao;
    private final UserDao userDao;
    private final BlockService blocks;

    public ChatService(MessageDao messageDao, CallDao callDao, UserDao userDao, BlockService blocks) {
        this.messageDao = messageDao;
        this.callDao = callDao;
        this.userDao = userDao;
        this.blocks = blocks;
    }

    /**
     * Validates, stores and returns the message. The stored body is the exact text the
     * user typed: it is never HTML-escaped here, because escaping belongs at the point
     * of rendering. Angular escapes interpolated text by default.
     */
    public Message send(long senderId, Long recipientId, String rawBody) {
        if (recipientId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A recipient is required.");
        }
        if (recipientId == senderId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot message yourself.");
        }
        if (userDao.findById(recipientId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "That person no longer exists.");
        }
        // Checked in both directions: blocking stops contact each way.
        blocks.assertNotBlocked(senderId, recipientId);

        String body = rawBody == null ? "" : rawBody.trim();
        if (body.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A message cannot be empty.");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "A message cannot be longer than " + MAX_BODY_LENGTH + " characters.");
        }

        long id = messageDao.insert(senderId, recipientId, body);
        return messageDao.findById(id).orElseThrow(
                () -> new IllegalStateException("Message vanished after insert: " + id));
    }

    /**
     * The conversation as one timeline: messages and calls interleaved by time, oldest
     * last-N first. Each side is limited before merging, so a burst of one kind cannot
     * crowd the other out of the query.
     */
    public List<ChatEntry> conversation(long selfId, long otherUserId) {
        if (userDao.findById(otherUserId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "That person no longer exists.");
        }

        List<ChatEntry> timeline = new ArrayList<>();
        messageDao.conversation(selfId, otherUserId, HISTORY_LIMIT)
                .forEach(message -> timeline.add(ChatEntry.of(message)));
        callDao.between(selfId, otherUserId, HISTORY_LIMIT)
                .forEach(call -> timeline.add(ChatEntry.of(call)));

        timeline.sort(Comparator.comparing(ChatEntry::createdAt).thenComparing(ChatEntry::id));

        // Keep the most recent window once both kinds are in one list.
        return timeline.size() <= HISTORY_LIMIT
                ? timeline
                : new ArrayList<>(timeline.subList(timeline.size() - HISTORY_LIMIT, timeline.size()));
    }

    /** @return how many messages were newly marked as read. */
    public int markRead(long readerId, long otherUserId) {
        return messageDao.markConversationRead(readerId, otherUserId);
    }

    public Map<Long, Integer> unreadCounts(long userId) {
        return messageDao.unreadCountsBySender(userId);
    }
}
