package com.glancesync.modules.realtime.service;

import java.util.UUID;
import io.micronaut.websocket.WebSocketSession;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is connected, right now. Presence is derived from live socket connections
 * rather than stored in the database, so it cannot drift out of date: if the socket
 * is open the user is online, and if the process dies every session dies with it.
 *
 * A user may have several connections at once (multiple tabs or devices), so
 * "online" means "at least one open session".
 */
@Singleton
public class PresenceRegistry {

    private final Map<UUID, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    /** @return true when this connection is the user's first, i.e. they just came online. */
    public boolean register(UUID userId, WebSocketSession session) {
        boolean[] cameOnline = {false};
        // compute() runs atomically, so a connect racing a disconnect cannot lose its entry.
        sessionsByUser.compute(userId, (key, sessions) -> {
            Set<WebSocketSession> live = sessions == null ? ConcurrentHashMap.newKeySet() : sessions;
            live.add(session);
            cameOnline[0] = live.size() == 1;
            return live;
        });
        return cameOnline[0];
    }

    /** @return true when this was the user's last connection, i.e. they just went offline. */
    public boolean unregister(UUID userId, WebSocketSession session) {
        boolean[] wentOffline = {false};
        sessionsByUser.computeIfPresent(userId, (key, sessions) -> {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                wentOffline[0] = true;
                return null; // dropping the entry keeps onlineUserIds() accurate
            }
            return sessions;
        });
        return wentOffline[0];
    }

    public boolean isOnline(UUID userId) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public List<UUID> onlineUserIds() {
        return List.copyOf(sessionsByUser.keySet());
    }

    /** Every open session, used to broadcast presence changes to all connected clients. */
    public Collection<WebSocketSession> allSessions() {
        return sessionsByUser.values().stream()
                .flatMap(Set::stream)
                .toList();
    }

    /** The sessions belonging to one user. Phase 3 delivers chat messages through this. */
    public Collection<WebSocketSession> sessionsFor(UUID userId) {
        return List.copyOf(sessionsByUser.getOrDefault(userId, Set.of()));
    }
}
