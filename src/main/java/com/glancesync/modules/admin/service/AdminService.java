package com.glancesync.modules.admin.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.admin.dto.ReportedUserResponse;
import com.glancesync.modules.admin.repo.AdminDao;
import com.glancesync.modules.auth.repo.RefreshTokenDao;
import com.glancesync.modules.fame.service.FameService;
import com.glancesync.modules.realtime.service.PresenceRegistry;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.user.repo.UserDao;
import com.glancesync.web.HttpStatus;
import org.eclipse.jetty.websocket.api.Session;

public class AdminService {

    /** WebSocket close code the client reads as "your account was frozen". */
    public static final int FROZEN_CLOSE_CODE = 4003;

    private final AdminDao adminDao;
    private final UserDao userDao;
    private final RefreshTokenDao refreshTokenDao;
    private final PresenceRegistry presence;
    private final FameService fameService;

    public AdminService(AdminDao adminDao, UserDao userDao, RefreshTokenDao refreshTokenDao,
                        PresenceRegistry presence, FameService fameService) {
        this.adminDao = adminDao;
        this.userDao = userDao;
        this.refreshTokenDao = refreshTokenDao;
        this.presence = presence;
        this.fameService = fameService;
    }

    public List<ReportedUserResponse> reportedUsers() {
        Map<UUID, List<ReportedUserResponse.Report>> reportsByUser = adminDao.reports().stream()
                .collect(Collectors.groupingBy(AdminDao.ReportRow::reportedId,
                        Collectors.mapping(AdminDao.ReportRow::report, Collectors.toList())));
        return adminDao.reportedUsers().stream()
                .map(user -> new ReportedUserResponse(user.id(), user.username(), user.firstName(),
                        user.lastName(), user.avatarUrl(), user.reportCount(), user.lastReportedAt(),
                        user.active(), reportsByUser.getOrDefault(user.id(), List.of())))
                .toList();
    }

    /** Freezes the account and ends every session it has, so the freeze applies at once. */
    public void freeze(UUID adminId, UUID userId) {
        User target = requireTarget(adminId, userId);
        if (!target.active()) {
            return;
        }
        userDao.setActive(userId, false);
        refreshTokenDao.deleteAllFor(userId);
        for (Session session : presence.sessionsFor(userId)) {
            session.close(FROZEN_CLOSE_CODE, "Account frozen");
        }
    }

    public void unfreeze(UUID adminId, UUID userId) {
        requireTarget(adminId, userId);
        userDao.setActive(userId, true);
    }

    /** Throws the reports away, for when the account turns out to be genuine. */
    public void dismissReports(UUID adminId, UUID userId) {
        requireTarget(adminId, userId);
        if (adminDao.dismissReports(userId) > 0) {
            fameService.recompute(userId);
        }
    }

    private User requireTarget(UUID adminId, UUID userId) {
        if (adminId.equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot moderate your own account.");
        }
        User target = userDao.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That person no longer exists."));
        if (target.admin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Administrators cannot be moderated.");
        }
        return target;
    }
}
