package com.glancesync.modules.notification.controller;

import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.auth.filter.AuthenticationFilter;
import com.glancesync.modules.notification.entity.Notification;
import com.glancesync.modules.notification.service.NotificationService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Notifications")
@Controller("/api/notifications")
public class NotificationsController {

    private final NotificationService notificationService;

    public NotificationsController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Get
    public List<Notification> list(HttpRequest<?> request) {
        return notificationService.recent(selfId(request));
    }

    @Post("/read")
    public HttpResponse<?> markAllRead(HttpRequest<?> request) {
        notificationService.markAllRead(selfId(request));
        return HttpResponse.noContent();
    }

    private long selfId(HttpRequest<?> request) {
        return request.getAttribute(AuthenticationFilter.USER_ID_ATTRIBUTE, Long.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}
