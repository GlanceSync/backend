package com.glancesync.modules.call.service;


import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-flight calls. The server carries no media — it only relays signalling — but it
 * does track who is in each call, so a third party cannot inject signalling into a
 * conversation they are not part of.
 */
public class CallRegistry {

    public record Call(String id, UUID callerId, UUID calleeId, String mode,
                       java.time.Instant startedAt, java.time.Instant answeredAt) {

        /** A call is answered once the callee has sent an answer. */
        public boolean answered() {
            return answeredAt != null;
        }

        public boolean involves(UUID userId) {
            return callerId.equals(userId) || calleeId.equals(userId);
        }

        /** The participant who is not {@code userId}. */
        public UUID otherParty(UUID userId) {
            return callerId.equals(userId) ? calleeId : callerId;
        }
    }

    private final Map<String, Call> callsById = new ConcurrentHashMap<>();

    public Call start(UUID callerId, UUID calleeId, String mode) {
        // The id is generated here, never taken from the client, so it cannot be forged
        // to collide with someone else's call.
        Call call = new Call(UUID.randomUUID().toString(), callerId, calleeId, mode,
                java.time.Instant.now(), null);
        callsById.put(call.id(), call);
        return call;
    }

    public Optional<Call> find(String callId) {
        return callId == null ? Optional.empty() : Optional.ofNullable(callsById.get(callId));
    }

    /** @return the call as it was, if it existed and the caller may act on it. */
    public Optional<Call> findParticipating(String callId, UUID userId) {
        return find(callId).filter(call -> call.involves(userId));
    }

    public void markAnswered(String callId) {
        callsById.computeIfPresent(callId, (id, call) -> call.answered()
                ? call
                : new Call(call.id(), call.callerId(), call.calleeId(), call.mode(),
                        call.startedAt(), java.time.Instant.now()));
    }

    public Optional<Call> end(String callId) {
        return Optional.ofNullable(callsById.remove(callId));
    }

    /** Ends every call a user was in, used when their last socket closes. */
    public java.util.List<Call> endAllFor(UUID userId) {
        java.util.List<Call> ended = new java.util.ArrayList<>();
        callsById.values().removeIf(call -> {
            if (call.involves(userId)) {
                ended.add(call);
                return true;
            }
            return false;
        });
        return ended;
    }
}
