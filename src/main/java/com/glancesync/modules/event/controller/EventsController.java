package com.glancesync.modules.event.controller;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.auth.filter.AuthenticationFilter;
import com.glancesync.modules.event.dto.ProposeEventRequest;
import com.glancesync.modules.event.entity.Event;
import com.glancesync.modules.event.service.EventService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Instant;
import java.util.List;

@Tag(name = "Events")
@Controller("/api/events")
public class EventsController {

    private final EventService eventService;

    public EventsController(EventService eventService) {
        this.eventService = eventService;
    }

    @Get
    public List<Event> list(HttpRequest<?> request) {
        return eventService.forUser(selfId(request));
    }

    @Post(consumes = MediaType.APPLICATION_JSON)
    public HttpResponse<Event> propose(HttpRequest<?> request, @Body ProposeEventRequest body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A request body is required.");
        }
        Event event = eventService.propose(selfId(request), body.inviteeId(),
                body.title(), body.location(), parseStartsAt(body.startsAt()));
        return HttpResponse.created(event);
    }

    @Post("/{eventId}/accept")
    public Event accept(HttpRequest<?> request, @PathVariable UUID eventId) {
        return eventService.respond(selfId(request), eventId, true);
    }

    @Post("/{eventId}/decline")
    public Event decline(HttpRequest<?> request, @PathVariable UUID eventId) {
        return eventService.respond(selfId(request), eventId, false);
    }

    @Post("/{eventId}/cancel")
    public Event cancel(HttpRequest<?> request, @PathVariable UUID eventId) {
        return eventService.cancel(selfId(request), eventId);
    }

    /** The browser sends an ISO instant; anything else is a client error, not a crash. */
    private Instant parseStartsAt(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Pick a date and time.");
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That date could not be read.");
        }
    }

    private UUID selfId(HttpRequest<?> request) {
        return request.getAttribute(AuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}
