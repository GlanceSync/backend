package com.glancesync.modules.realtime.socket;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.auth.service.SessionService;
import com.glancesync.modules.block.service.BlockService;
import com.glancesync.modules.call.service.CallLogService;
import com.glancesync.modules.call.service.CallRegistry;
import com.glancesync.modules.chat.entity.Message;
import com.glancesync.modules.chat.service.ChatService;
import com.glancesync.modules.notification.service.NotificationService;
import com.glancesync.modules.realtime.dto.RealtimeMessages;
import com.glancesync.modules.realtime.service.PresenceRegistry;
import com.glancesync.modules.realtime.service.RealtimePush;
import com.glancesync.modules.user.repo.UserDao;
import io.micronaut.http.HttpRequest;
import io.micronaut.json.JsonMapper;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnError;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * The single real-time connection. Phase 2 uses it for presence; chat, notifications
 * and call signalling ride the same socket in later phases.
 *
 * Authentication reuses the session cookie, which the browser attaches to the
 * handshake automatically. That is precisely why sessions are cookie-based: the
 * browser's WebSocket API cannot set an Authorization header.
 */
@ServerWebSocket("/ws")
public class RealtimeSocket {

    private static final Logger LOG = LoggerFactory.getLogger(RealtimeSocket.class);
    private static final String USER_ID = "userId";

    private final SessionService sessionService;
    private final PresenceRegistry presence;
    private final UserDao userDao;
    private final ChatService chatService;
    private final JsonMapper jsonMapper;
    private final RealtimePush push;
    private final NotificationService notifications;
    private final CallRegistry calls;
    private final CallLogService callLog;
    private final BlockService blocks;

    public RealtimeSocket(SessionService sessionService, PresenceRegistry presence,
                          UserDao userDao, ChatService chatService, JsonMapper jsonMapper,
                          RealtimePush push, NotificationService notifications,
                          CallRegistry calls, CallLogService callLog, BlockService blocks) {
        this.sessionService = sessionService;
        this.presence = presence;
        this.userDao = userDao;
        this.chatService = chatService;
        this.jsonMapper = jsonMapper;
        this.push = push;
        this.notifications = notifications;
        this.calls = calls;
        this.callLog = callLog;
        this.blocks = blocks;
    }

    @OnOpen
    public void onOpen(WebSocketSession session, HttpRequest<?> request) {
        UUID userId = request.getCookies()
                .findCookie(SessionService.COOKIE_NAME)
                .flatMap(cookie -> sessionService.resolveUserId(cookie.getValue()))
                .orElse(null);

        if (userId == null) {
            // Nothing is registered for this session, so no presence event is emitted.
            session.close(CloseReason.POLICY_VIOLATION);
            return;
        }

        session.put(USER_ID, userId);
        boolean cameOnline = presence.register(userId, session);

        // Tell the new client who is already here.
        send(session, new RealtimeMessages.PresenceInit(presence.onlineUserIds()));

        // The unread badge must be right the moment any page loads.
        send(session, new RealtimeMessages.ChatUnread(chatService.unreadCounts(userId)));
        send(session, new RealtimeMessages.NotificationUnread(notifications.unreadCount(userId)));

        if (cameOnline) {
            userDao.touchLastSeen(userId);
            broadcastExcept(userId, new RealtimeMessages.PresenceChanged(userId, true, null));
        }
    }

    @OnClose
    public void onClose(WebSocketSession session) {
        UUID userId = userId(session);
        if (userId == null) {
            return;
        }
        if (presence.unregister(userId, session)) {
            for (CallRegistry.Call call : calls.endAllFor(userId)) {
                send(call.otherParty(userId), new RealtimeMessages.CallEnded(call.id(), "disconnected"));
                callLog.record(call, call.answered() ? CallLogService.ANSWERED : CallLogService.MISSED);
            }

            Instant lastSeen = Instant.now();
            userDao.touchLastSeen(userId);
            broadcastExcept(userId, new RealtimeMessages.PresenceChanged(userId, false, lastSeen));
        }
    }

    @OnMessage
    public void onMessage(String raw, WebSocketSession session) {
        UUID userId = userId(session);
        if (userId == null) {
            return;
        }

        RealtimeMessages.Inbound inbound;
        try {
            inbound = jsonMapper.readValue(raw, RealtimeMessages.Inbound.class);
        } catch (Exception e) {
            // Malformed input is a client problem, not a server error.
            send(session, new RealtimeMessages.ChatError("That message could not be read."));
            return;
        }

        try {
            switch (inbound.type() == null ? "" : inbound.type()) {
                case "chat:send" -> handleSend(userId, inbound);
                case "chat:read" -> handleRead(userId, inbound);
                case "call:offer" -> handleCallOffer(userId, inbound);
                case "call:answer" -> handleCallAnswer(userId, inbound);
                case "call:ice" -> relayCallSignal(userId, inbound, "call:ice");
                case "call:reject" -> handleCallEnd(userId, inbound, "rejected");
                case "call:hangup" -> handleCallEnd(userId, inbound, "hangup");
                default -> LOG.debug("Ignoring unsupported message type {}", inbound.type());
            }
        } catch (ApiException e) {
            // An expected rejection: report it to the sender, keep the socket open.
            send(session, new RealtimeMessages.ChatError(e.getMessage()));
        } catch (Exception e) {
            LOG.error("Failed to handle {} from user {}", inbound.type(), userId, e);
            send(session, new RealtimeMessages.ChatError("Your message could not be delivered."));
        }
    }

    private void handleSend(UUID senderId, RealtimeMessages.Inbound inbound) {
        Message message = chatService.send(senderId, inbound.recipientId(), inbound.body());
        RealtimeMessages.ChatMessage payload = new RealtimeMessages.ChatMessage(message);

        // The sender sees it on every one of their tabs, including this one, so the
        // message they see is always the stored one with its real id and timestamp.
        sendToUser(senderId, payload);

        UUID recipientId = message.recipientId();
        sendToUser(recipientId, payload);
        // Their badge changes even if they are not looking at this conversation.
        sendToUser(recipientId, new RealtimeMessages.ChatUnread(chatService.unreadCounts(recipientId)));

        // A durable record, so a message received while offline is still surfaced later.
        String senderName = userDao.findById(senderId).map(user -> user.username()).orElse("Someone");
        notifications.notify(recipientId, NotificationService.TYPE_MESSAGE, senderId,
                senderName + " sent you a message.");
    }

    private void handleRead(UUID readerId, RealtimeMessages.Inbound inbound) {
        UUID otherUserId = inbound.withUserId();
        if (otherUserId == null) {
            return;
        }

        int marked = chatService.markRead(readerId, otherUserId);
        // Always refresh the reader's own badge, even when nothing changed, so a tab
        // that reconnected with a stale count is corrected.
        sendToUser(readerId, new RealtimeMessages.ChatUnread(chatService.unreadCounts(readerId)));

        if (marked > 0) {
            sendToUser(otherUserId, new RealtimeMessages.ChatRead(readerId));
        }
    }

    // ---------- Calls ----------
    //
    // The server is a signalling relay only: it forwards SDP offers, answers and ICE
    // candidates between two participants. Audio and video travel peer-to-peer and
    // never reach this process.

    private void handleCallOffer(UUID callerId, RealtimeMessages.Inbound inbound) {
        UUID calleeId = inbound.targetUserId();
        if (calleeId == null || calleeId == callerId) {
            send(callerId, new RealtimeMessages.CallEnded(null, "invalid"));
            return;
        }
        if (userDao.findById(calleeId).isEmpty()) {
            send(callerId, new RealtimeMessages.CallEnded(null, "unknown"));
            return;
        }
        if (blocks.isBlockedEitherWay(callerId, calleeId)) {
            send(callerId, new RealtimeMessages.CallEnded(null, "blocked"));
            return;
        }

        String mode = "audio".equals(inbound.mode()) ? "audio" : "video";

        if (!presence.isOnline(calleeId)) {
            send(callerId, new RealtimeMessages.CallEnded(null, "offline"));
            callLog.recordNotReached(callerId, calleeId, mode);
            return;
        }

        CallRegistry.Call call = calls.start(callerId, calleeId, mode);

        // The caller learns the id the server assigned, so both ends agree on it.
        send(callerId, new RealtimeMessages.CallRinging(call.id(), calleeId, mode));
        send(calleeId, signal("call:incoming", call.id(), callerId, mode, inbound.payload()));
    }

    private void handleCallAnswer(UUID calleeId, RealtimeMessages.Inbound inbound) {
        CallRegistry.Call call = calls.findParticipating(inbound.callId(), calleeId).orElse(null);
        if (call == null) {
            return;
        }
        calls.markAnswered(call.id());
        send(call.otherParty(calleeId),
                signal("call:answered", call.id(), calleeId, call.mode(), inbound.payload()));
    }

    /** Forwards a signal to the other participant, after checking the sender is in the call. */
    private void relayCallSignal(UUID senderId, RealtimeMessages.Inbound inbound, String type) {
        calls.findParticipating(inbound.callId(), senderId).ifPresent(call ->
                send(call.otherParty(senderId),
                        signal(type, call.id(), senderId, call.mode(), inbound.payload())));
    }

    private void handleCallEnd(UUID senderId, RealtimeMessages.Inbound inbound, String reason) {
        CallRegistry.Call call = calls.findParticipating(inbound.callId(), senderId).orElse(null);
        if (call == null) {
            return;
        }
        calls.end(call.id());

        UUID other = call.otherParty(senderId);
        send(other, new RealtimeMessages.CallEnded(call.id(), reason));
        send(senderId, new RealtimeMessages.CallEnded(call.id(), reason));

        callLog.record(call, endStatus(call, senderId, reason));
    }

    /**
     * Declined means the callee turned it down before answering. Anything else that
     * ends without an answer is a missed call, however it was cut short.
     */
    private String endStatus(CallRegistry.Call call, UUID senderId, String reason) {
        if (call.answered()) {
            return CallLogService.ANSWERED;
        }
        boolean declinedByCallee = "rejected".equals(reason) && senderId.equals(call.calleeId());
        return declinedByCallee ? CallLogService.DECLINED : CallLogService.MISSED;
    }

    private RealtimeMessages.CallSignal signal(String type, String callId, UUID fromUserId,
                                               String mode, java.util.Map<String, Object> payload) {
        // Who is calling, as the callee's screen shows them: name and photo.
        var from = userDao.findById(fromUserId);
        return new RealtimeMessages.CallSignal(type, callId, fromUserId,
                from.map(user -> user.username()).orElse(null),
                from.map(user -> user.avatarUrl()).orElse(null),
                mode, payload);
    }

    private void send(UUID userId, Object message) {
        push.toUser(userId, message);
    }

    private void sendToUser(UUID userId, Object message) {
        push.toUser(userId, message);
    }

    @OnError
    public void onError(WebSocketSession session, Throwable error) {
        LOG.warn("WebSocket error on session {}", session.getId(), error);
    }

    private UUID userId(WebSocketSession session) {
        return session.get(USER_ID, UUID.class).orElse(null);
    }

    private void broadcastExcept(UUID exceptUserId, Object message) {
        push.toEveryoneExcept(exceptUserId, message);
    }

    private void send(WebSocketSession session, Object message) {
        push.toSession(session, message);
    }
}
