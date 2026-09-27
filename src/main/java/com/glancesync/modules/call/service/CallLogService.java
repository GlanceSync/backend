package com.glancesync.modules.call.service;

import com.glancesync.modules.call.repo.CallDao;
import com.glancesync.modules.notification.service.NotificationService;
import com.glancesync.modules.realtime.dto.RealtimeMessages;
import com.glancesync.modules.realtime.service.RealtimePush;
import com.glancesync.modules.user.repo.UserDao;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;

/**
 * Writes one row per finished call and pushes it to both participants, so a call shows
 * up in the conversation the same way a message does.
 */
@Singleton
public class CallLogService {

    public static final String ANSWERED = "answered";
    public static final String MISSED = "missed";
    public static final String DECLINED = "declined";

    private final CallDao callDao;
    private final UserDao userDao;
    private final RealtimePush push;
    private final NotificationService notifications;

    public CallLogService(CallDao callDao, UserDao userDao, RealtimePush push,
                          NotificationService notifications) {
        this.callDao = callDao;
        this.userDao = userDao;
        this.push = push;
        this.notifications = notifications;
    }

    /** Records a call that had a registry entry, i.e. one that actually started ringing. */
    public void record(CallRegistry.Call call, String status) {
        Instant endedAt = Instant.now();
        Integer durationSeconds = call.answeredAt() == null
                ? null
                : (int) Duration.between(call.answeredAt(), endedAt).toSeconds();

        store(call.callerId(), call.calleeId(), call.mode(), status,
                call.startedAt(), call.answeredAt(), endedAt, durationSeconds);
    }

    /** Records a call that never rang, because the other person was not connected. */
    public void recordNotReached(long callerId, long calleeId, String mode) {
        Instant now = Instant.now();
        store(callerId, calleeId, mode, MISSED, now, null, now, null);
    }

    private void store(long callerId, long calleeId, String mode, String status,
                       Instant startedAt, Instant answeredAt, Instant endedAt, Integer durationSeconds) {
        long id = callDao.insert(callerId, calleeId, mode, status,
                startedAt, answeredAt, endedAt, durationSeconds);

        callDao.findById(id).ifPresent(record -> {
            RealtimeMessages.ChatCall payload = new RealtimeMessages.ChatCall(record);
            // Both ends see it appear in the open conversation.
            push.toUser(callerId, payload);
            push.toUser(calleeId, payload);
        });

        // Only a call the callee never took is worth a notification; they already know
        // about the ones they answered or declined.
        if (MISSED.equals(status)) {
            String callerName = userDao.findById(callerId).map(user -> user.username()).orElse("Someone");
            notifications.notify(calleeId, NotificationService.TYPE_CALL_MISSED, callerId,
                    "You missed a " + ("video".equals(mode) ? "video" : "voice")
                            + " call from " + callerName + ".");
        }
    }
}
