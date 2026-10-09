package com.glancesync.modules.realtime.service;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
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
    private final ObjectMapper json;

    public RealtimePush(PresenceRegistry presence, ObjectMapper json) {
        this.presence = presence;
        this.json = json;
    }

    /** Sends to every tab or device belonging to one user. */
    public void toUser(UUID userId, Object message) {
        for (Session session : presence.sessionsFor(userId)) {
            toSession(session, message);
        }
    }

    /** Sends to everyone connected except the user the event is about. */
    public void toEveryoneExcept(UUID exceptUserId, Object message) {
        for (Session session : presence.allSessions()) {
            UUID target = presence.userId(session);
            if (target != null && !target.equals(exceptUserId)) {
                toSession(session, message);
            }
        }
    }

    public void toSession(Session session, Object message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.getRemote().sendString(json.writeValueAsString(message), new WriteCallback() {
                @Override public void writeSuccess() {}
                @Override public void writeFailed(Throwable error) {
                    LOG.warn("Failed to push to session {}", session.getRemoteAddress(), error);
                }
            });
        } catch (Exception error) {
            LOG.warn("Failed to push to session {}", session.getRemoteAddress(), error);
        }
    }
}
