package com.glancesync.modules.report.service;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.fame.service.FameService;
import com.glancesync.modules.report.repo.ReportDao;
import com.glancesync.modules.user.repo.UserDao;
import com.glancesync.web.HttpStatus;

import java.util.Map;

public class ReportService {

    public static final int MAX_REASON_LENGTH = 500;

    private final ReportDao reportDao;
    private final UserDao userDao;
    private final FameService fameService;

    public ReportService(ReportDao reportDao, UserDao userDao, FameService fameService) {
        this.reportDao = reportDao;
        this.userDao = userDao;
        this.fameService = fameService;
    }

    /**
     * Reports a user as fake.
     *
     * @param reporterId The ID of the user reporting.
     * @param reportedId The ID of the user being reported.
     * @param rawReason The reason for the report.
     */
    public void reportFake(UUID reporterId, UUID reportedId, String rawReason) {
        if (reporterId.equals(reportedId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot report yourself.");
        }
        if (userDao.findById(reportedId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "That person no longer exists.");
        }
        String reason = rawReason == null || rawReason.isBlank() ? null : rawReason.strip();
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "The reason can be at most " + MAX_REASON_LENGTH + " characters.",
                    Map.of("reason", "Too long."));
        }
        if (reportDao.report(reporterId, reportedId, reason) > 0) {
            fameService.recompute(reportedId);
        }
    }
}
