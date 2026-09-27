package com.glancesync.modules.notification.service;

import com.glancesync.modules.notification.entity.Notification;
import com.glancesync.modules.notification.repo.NotificationDao;
import com.glancesync.modules.realtime.dto.RealtimeMessages;
import com.glancesync.modules.realtime.service.RealtimePush;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Creates notifications and pushes them immediately. Every notification is stored
 * first, so a user who was offline still sees it when they next sign in.
 */
@Singleton
public class NotificationService {

    public static final String TYPE_MESSAGE = "message";
    public static final String TYPE_CALL_MISSED = "call_missed";
    public static final String TYPE_EVENT_INVITED = "event_invited";
    public static final String TYPE_EVENT_ACCEPTED = "event_accepted";
    public static final String TYPE_EVENT_DECLINED = "event_declined";
    public static final String TYPE_EVENT_CANCELLED = "event_cancelled";
    public static final String TYPE_USER_BLOCKED = "user_blocked";
    public static final String TYPE_USER_UNBLOCKED = "user_unblocked";

    private static final int RECENT_LIMIT = 50;

    private final NotificationDao notificationDao;
    private final RealtimePush push;

    public NotificationService(NotificationDao notificationDao, RealtimePush push) {
        this.notificationDao = notificationDao;
        this.push = push;
    }

    public void notify(long userId, String type, Long actorId, String body) {
        long id = notificationDao.insert(userId, type, actorId, body);
        notificationDao.findById(id).ifPresent(notification ->
                push.toUser(userId, new RealtimeMessages.NotificationNew(notification)));
        pushUnreadCount(userId);
    }

    public List<Notification> recent(long userId) {
        return notificationDao.recentFor(userId, RECENT_LIMIT);
    }

    public int unreadCount(long userId) {
        return notificationDao.unreadCount(userId);
    }

    public void markAllRead(long userId) {
        notificationDao.markAllRead(userId);
        pushUnreadCount(userId);
    }

    /** Keeps the bell in the header correct on every open tab. */
    public void pushUnreadCount(long userId) {
        push.toUser(userId, new RealtimeMessages.NotificationUnread(notificationDao.unreadCount(userId)));
    }
}
