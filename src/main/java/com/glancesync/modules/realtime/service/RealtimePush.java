package com.glancesync.modules.realtime.service;

import java.util.UUID;
import io.micronaut.http.MediaType;
import io.micronaut.websocket.WebSocketSession;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends messages out over open sockets. Kept separate from the socket endpoint so any
 * service can push without depending on the endpoint, which would be a cycle.
 */
@Singleton
public class RealtimePush {

    private static final Logger LOG = LoggerFactory.getLogger(RealtimePush.class);

    private final PresenceRegistry presence;

    public RealtimePush(PresenceRegistry presence) {
        this.presence = presence;
    }

    /** Sends to every tab or device belonging to one user. */
    public void toUser(UUID userId, Object message) {
        for (WebSocketSession session : presence.sessionsFor(userId)) {
            toSession(session, message);
        }
    }

    /** Sends to everyone connected except the user the event is about. */
    public void toEveryoneExcept(UUID exceptUserId, Object message) {
        for (WebSocketSession session : presence.allSessions()) {
            UUID target = session.get("userId", UUID.class).orElse(null);
            if (target != null && !target.equals(exceptUserId)) {
                toSession(session, message);
            }
        }
    }

    public void toSession(WebSocketSession session, Object message) {
        if (!session.isOpen()) {
            return;
        }
        // One failed client must not stop the others from being notified.
        session.sendAsync(message, MediaType.APPLICATION_JSON_TYPE)
                .exceptionally(error -> {
                    LOG.warn("Failed to push to session {}", session.getId(), error);
                    return null;
                });
    }
}
