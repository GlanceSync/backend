package com.glancesync.modules.event.service;

import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.event.entity.Event;
import com.glancesync.modules.event.repo.EventDao;
import com.glancesync.modules.notification.service.NotificationService;
import com.glancesync.modules.user.repo.UserDao;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

@Singleton
public class EventService {

    public static final String PENDING = "pending";
    public static final String ACCEPTED = "accepted";
    public static final String DECLINED = "declined";
    public static final String CANCELLED = "cancelled";

    private static final int MAX_TITLE = 120;
    private static final int MAX_LOCATION = 160;

    private final EventDao eventDao;
    private final UserDao userDao;
    private final NotificationService notifications;

    public EventService(EventDao eventDao, UserDao userDao, NotificationService notifications) {
        this.eventDao = eventDao;
        this.userDao = userDao;
        this.notifications = notifications;
    }

    public List<Event> forUser(long userId) {
        return eventDao.findForUser(userId);
    }

    public Event propose(long organizerId, Long inviteeId, String rawTitle,
                         String rawLocation, Instant startsAt) {
        if (inviteeId == null || inviteeId == organizerId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose someone else to meet.");
        }
        if (userDao.findById(inviteeId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "That person no longer exists.");
        }

        String title = trimmed(rawTitle);
        if (title.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Give the date a title.");
        }
        if (title.length() > MAX_TITLE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "The title cannot be longer than " + MAX_TITLE + " characters.");
        }

        String location = trimmed(rawLocation);
        if (location.length() > MAX_LOCATION) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "The location cannot be longer than " + MAX_LOCATION + " characters.");
        }

        if (startsAt == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Pick a date and time.");
        }
        if (startsAt.isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That date is in the past.");
        }

        long id = eventDao.insert(organizerId, inviteeId, title,
                location.isEmpty() ? null : location, startsAt);
        Event event = load(id);

        notifications.notify(inviteeId, NotificationService.TYPE_EVENT_INVITED, organizerId,
                event.organizerUsername() + " invited you to \"" + event.title() + "\".");
        return event;
    }

    /** The invitee accepts or declines; only they may. */
    public Event respond(long userId, long eventId, boolean accept) {
        Event event = load(eventId);
        if (event.inviteeId() != userId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the person invited can answer this.");
        }
        if (!PENDING.equals(event.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "This invitation has already been answered.");
        }

        String status = accept ? ACCEPTED : DECLINED;
        eventDao.updateStatus(eventId, status);

        notifications.notify(event.organizerId(),
                accept ? NotificationService.TYPE_EVENT_ACCEPTED : NotificationService.TYPE_EVENT_DECLINED,
                userId,
                event.inviteeUsername() + (accept ? " accepted " : " declined ")
                        + "\"" + event.title() + "\".");
        return load(eventId);
    }

    /** Either participant may call the date off. */
    public Event cancel(long userId, long eventId) {
        Event event = load(eventId);
        if (event.organizerId() != userId && event.inviteeId() != userId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This is not your date.");
        }
        if (CANCELLED.equals(event.status())) {
            return event;
        }

        eventDao.updateStatus(eventId, CANCELLED);

        long otherParty = event.organizerId() == userId ? event.inviteeId() : event.organizerId();
        String actorName = event.organizerId() == userId
                ? event.organizerUsername() : event.inviteeUsername();
        notifications.notify(otherParty, NotificationService.TYPE_EVENT_CANCELLED, userId,
                actorName + " cancelled \"" + event.title() + "\".");
        return load(eventId);
    }

    private Event load(long eventId) {
        return eventDao.findById(eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That date no longer exists."));
    }

    private String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
