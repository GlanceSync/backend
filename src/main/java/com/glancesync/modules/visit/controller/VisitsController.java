package com.glancesync.modules.visit.controller;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.security.filter.JwtAuthenticationFilter;
import com.glancesync.modules.user.dto.ActivityResponse;
import com.glancesync.modules.visit.service.ProfileViewService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/** Visits are recorded by GET /api/users/{userId}/profile; this lists who came by. */
@Tag(name = "Visits")
@Controller("/api/users")
public class VisitsController {

    private final ProfileViewService viewService;

    public VisitsController(ProfileViewService viewService) {
        this.viewService = viewService;
    }

    @Get("/me/views")
    public List<ActivityResponse> viewers(HttpRequest<?> request) {
        return viewService.viewersOf(selfId(request));
    }

    private UUID selfId(HttpRequest<?> request) {
        return request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}
