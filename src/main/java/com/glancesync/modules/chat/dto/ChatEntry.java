package com.glancesync.modules.chat.dto;

import com.glancesync.modules.call.entity.CallRecord;
import com.glancesync.modules.chat.entity.Message;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/**
 * One item in a conversation. Messages and calls share a timeline, so they share a
 * shape: {@code kind} says which it is, and the fields that do not apply are null.
 *
 * Ids are only unique within a kind, so a client keys on kind and id together.
 */
@Serdeable
public record ChatEntry(String kind, long id, long senderId, long recipientId,
                        String body, Instant createdAt, Instant readAt,
                        String callStatus, String callMode, Integer callDurationSeconds) {

    public static final String KIND_MESSAGE = "message";
    public static final String KIND_CALL = "call";

    public static ChatEntry of(Message message) {
        return new ChatEntry(KIND_MESSAGE, message.id(), message.senderId(), message.recipientId(),
                message.body(), message.createdAt(), message.readAt(), null, null, null);
    }

    public static ChatEntry of(CallRecord call) {
        // A call is "from" whoever placed it, which is what the conversation should show.
        return new ChatEntry(KIND_CALL, call.id(), call.callerId(), call.calleeId(),
                null, call.startedAt(), null,
                call.status(), call.mode(), call.durationSeconds());
    }
}
