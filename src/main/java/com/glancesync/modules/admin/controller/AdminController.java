package com.glancesync.modules.admin.controller;

import java.util.List;
import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.admin.dto.ReportedUserResponse;
import com.glancesync.modules.admin.service.AdminService;
import com.glancesync.modules.security.filter.JwtAuthenticationFilter;
import com.glancesync.web.HttpRequest;
import com.glancesync.web.HttpResponse;
import com.glancesync.web.HttpStatus;
import com.glancesync.web.annotation.Controller;
import com.glancesync.web.annotation.Delete;
import com.glancesync.web.annotation.Get;
import com.glancesync.web.annotation.PathVariable;
import com.glancesync.web.annotation.Post;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Moderation. JwtAuthenticationFilter lets only administrators reach /api/admin/*. */
@Tag(name = "Admin")
@Controller("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Get("/reports")
    public List<ReportedUserResponse> reports() {
        return adminService.reportedUsers();
    }

    @Post("/users/{userId}/freeze")
    public HttpResponse<?> freeze(HttpRequest<?> request, @PathVariable UUID userId) {
        adminService.freeze(selfId(request), userId);
        return HttpResponse.noContent();
    }

    @Post("/users/{userId}/unfreeze")
    public HttpResponse<?> unfreeze(HttpRequest<?> request, @PathVariable UUID userId) {
        adminService.unfreeze(selfId(request), userId);
        return HttpResponse.noContent();
    }

    @Delete("/users/{userId}/reports")
    public HttpResponse<?> dismissReports(HttpRequest<?> request, @PathVariable UUID userId) {
        adminService.dismissReports(selfId(request), userId);
        return HttpResponse.noContent();
    }

    private UUID selfId(HttpRequest<?> request) {
        return request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}
