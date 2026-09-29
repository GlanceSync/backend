package com.glancesync.modules.realtime.dto;

import java.util.UUID;
import com.glancesync.modules.call.entity.CallRecord;
import com.glancesync.modules.chat.dto.ChatEntry;
import com.glancesync.modules.chat.entity.Message;
import com.glancesync.modules.notification.entity.Notification;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Everything the server pushes over the socket. Each message carries a {@code type}
 * so the client can dispatch on it; later phases add chat, notification and call types
 * to this same envelope rather than opening more connections.
 */
public final class RealtimeMessages {

    private RealtimeMessages() {
    }

    /** Sent once, to a freshly connected client, so it knows who is already online. */
    @Serdeable
    public record PresenceInit(String type, List<UUID> onlineUserIds) {
        public PresenceInit(List<UUID> onlineUserIds) {
            this("presence:init", onlineUserIds);
        }
    }

    /** Broadcast when someone's first connection opens or last connection closes. */
    @Serdeable
    public record PresenceChanged(String type, UUID userId, boolean online, Instant lastSeenAt) {
        public PresenceChanged(UUID userId, boolean online, Instant lastSeenAt) {
            this("presence:changed", userId, online, lastSeenAt);
        }
    }

    /** A chat message, pushed to the recipient and echoed to the sender's other tabs. */
    @Serdeable
    public record ChatMessage(String type, Message message) {
        public ChatMessage(Message message) {
            this("chat:message", message);
        }
    }

    /**
     * The recipient's unread tally, so the badge in the header is correct on every page
     * without each page having to ask.
     */
    @Serdeable
    public record ChatUnread(String type, Map<UUID, Integer> byUser, int total) {
        public ChatUnread(Map<UUID, Integer> byUser) {
            this("chat:unread", byUser, byUser.values().stream().mapToInt(Integer::intValue).sum());
        }
    }

    /** Tells a sender that the other person has read their messages. */
    @Serdeable
    public record ChatRead(String type, UUID byUserId) {
        public ChatRead(UUID byUserId) {
            this("chat:read", byUserId);
        }
    }

    /** A rejected inbound message, reported back to the tab that sent it. */
    @Serdeable
    public record ChatError(String type, String message) {
        public ChatError(String message) {
            this("chat:error", message);
        }
    }

    /**
     * Everything a client may send. One permissive record covers all inbound types;
     * each handler reads only the fields its own type defines.
     */
    @Serdeable
    public record Inbound(String type, UUID recipientId, String body, UUID withUserId,
                          UUID targetUserId, String callId, String mode,
                          Map<String, Object> payload) {
    }

    /** A newly created notification, pushed the moment it is stored. */
    @Serdeable
    public record NotificationNew(String type, Notification notification) {
        public NotificationNew(Notification notification) {
            this("notification:new", notification);
        }
    }

    /** The unread notification tally behind the bell in the header. */
    @Serdeable
    public record NotificationUnread(String type, int total) {
        public NotificationUnread(int total) {
            this("notification:unread", total);
        }
    }

    /**
     * Call signalling. {@code payload} carries the SDP or ICE candidate untouched:
     * the server relays WebRTC negotiation without understanding it, and media never
     * passes through the server at all.
     */
    @Serdeable
    public record CallSignal(String type, String callId, UUID fromUserId, String fromUsername,
                             String fromAvatarUrl, String mode, Map<String, Object> payload) {
    }

    /** Sent to the caller once a call id exists, so both ends agree on it. */
    @Serdeable
    public record CallRinging(String type, String callId, UUID calleeId, String mode) {
        public CallRinging(String callId, UUID calleeId, String mode) {
            this("call:ringing", callId, calleeId, mode);
        }
    }

    /** The call could not start or has stopped; {@code reason} explains which. */
    @Serdeable
    public record CallEnded(String type, String callId, String reason) {
        public CallEnded(String callId, String reason) {
            this("call:ended", callId, reason);
        }
    }

    /** A finished call, pushed so an open conversation can append it to the timeline. */
    @Serdeable
    public record ChatCall(String type, ChatEntry entry) {
        public ChatCall(CallRecord call) {
            this("chat:call", ChatEntry.of(call));
        }
    }
}
