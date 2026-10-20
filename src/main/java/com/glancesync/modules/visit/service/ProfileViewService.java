package com.glancesync.modules.visit.service;

import java.util.UUID;
import com.glancesync.modules.block.service.BlockService;
import com.glancesync.modules.fame.service.FameService;
import com.glancesync.modules.notification.service.NotificationService;
import com.glancesync.modules.user.dto.ActivityResponse;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.visit.repo.ProfileViewDao;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class ProfileViewService {

    private static final Duration NOTIFY_AGAIN_AFTER = Duration.ofHours(1);

    private final ProfileViewDao viewDao;
    private final BlockService blockService;
    private final FameService fameService;
    private final NotificationService notifications;

    public ProfileViewService(ProfileViewDao viewDao, BlockService blockService, FameService fameService,
                              NotificationService notifications) {
        this.viewDao = viewDao;
        this.blockService = blockService;
        this.fameService = fameService;
        this.notifications = notifications;
    }

    public void record(User viewer, UUID viewedId) {
        if (viewer.id().equals(viewedId) || blockService.isBlockedEitherWay(viewer.id(), viewedId)) {
            return;
        }
        Optional<Instant> previous = viewDao.record(viewer.id(), viewedId);
        if (previous.isEmpty()) {
            fameService.recompute(viewedId);
        }
        if (previous.map(at -> at.isBefore(Instant.now().minus(NOTIFY_AGAIN_AFTER))).orElse(true)) {
            notifications.notify(viewedId, NotificationService.TYPE_PROFILE_VIEW, viewer.id(),
                    viewer.username() + " viewed your profile.");
        }
    }

    public List<ActivityResponse> viewersOf(UUID userId) {
        return viewDao.viewersOf(userId);
    }
}
