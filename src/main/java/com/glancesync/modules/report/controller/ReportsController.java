package com.glancesync.modules.report.controller;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.report.dto.ReportRequest;
import com.glancesync.modules.report.service.ReportService;
import com.glancesync.modules.security.filter.JwtAuthenticationFilter;
import com.glancesync.web.Nullable;
import com.glancesync.web.HttpRequest;
import com.glancesync.web.HttpResponse;
import com.glancesync.web.HttpStatus;
import com.glancesync.web.MediaType;
import com.glancesync.web.annotation.Body;
import com.glancesync.web.annotation.Controller;
import com.glancesync.web.annotation.PathVariable;
import com.glancesync.web.annotation.Post;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Reports")
@Controller("/api/users")
public class ReportsController {

    private final ReportService reportService;

    public ReportsController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Post(value = "/{userId}/report", consumes = MediaType.APPLICATION_JSON)
    public HttpResponse<?> report(HttpRequest<?> request, @PathVariable UUID userId,
                                  @Body @Nullable ReportRequest body) {
        reportService.reportFake(selfId(request), userId, body == null ? null : body.reason());
        return HttpResponse.noContent();
    }

    private UUID selfId(HttpRequest<?> request) {
        return request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE, UUID.class)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}
