package com.glancesync.modules.notification.controller;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.security.filter.JwtAuthenticationFilter;
import com.glancesync.modules.notification.entity.Notification;
import com.glancesync.modules.notification.service.NotificationService;
import com.glancesync.web.HttpRequest;
import com.glancesync.web.HttpResponse;
import com.glancesync.web.HttpStatus;
import com.glancesync.web.annotation.Controller;
import com.glancesync.web.annotation.Get;
import com.glancesync.web.annotation.Post;
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

    private UUID selfId(HttpRequest<?> request) {
        return request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}
