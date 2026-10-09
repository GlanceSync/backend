package com.glancesync.modules.like.service;

import java.util.UUID;
import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.block.service.BlockService;
import com.glancesync.modules.fame.service.FameService;
import com.glancesync.modules.like.dto.LikeStatus;
import com.glancesync.modules.like.repo.LikeDao;
import com.glancesync.modules.notification.service.NotificationService;
import com.glancesync.modules.user.dto.ActivityResponse;
import com.glancesync.modules.user.entity.User;
import com.glancesync.modules.user.repo.UserDao;
import com.glancesync.web.HttpStatus;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class LikeService {

    private final LikeDao likeDao;
    private final UserDao userDao;
    private final BlockService blockService;
    private final FameService fameService;
    private final NotificationService notifications;

    public LikeService(LikeDao likeDao, UserDao userDao, BlockService blockService, FameService fameService,
                       NotificationService notifications) {
        this.likeDao = likeDao;
        this.userDao = userDao;
        this.blockService = blockService;
        this.fameService = fameService;
        this.notifications = notifications;
    }

    public LikeStatus like(UUID selfId, UUID otherId) {
        User self = requirePair(selfId, otherId);
        blockService.assertNotBlocked(selfId, otherId);
        if (self.profilePictureId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Add a profile picture before liking someone.");
        }

        if (likeDao.like(selfId, otherId) > 0) {
            boolean connected = likeDao.likes(otherId, selfId);
            fameService.recompute(otherId);
            if (connected) {
                fameService.recompute(selfId);
                String otherName = userDao.findById(otherId).map(User::username).orElse("Someone");
                notifications.notify(otherId, NotificationService.TYPE_MATCH, selfId,
                        "You and " + self.username() + " are now connected.");
                notifications.notify(selfId, NotificationService.TYPE_MATCH, otherId,
                        "You and " + otherName + " are now connected.");
            } else {
                notifications.notify(otherId, NotificationService.TYPE_LIKE, selfId,
                        self.username() + " liked your profile.");
            }
        }
        return status(selfId, otherId);
    }

    public LikeStatus unlike(UUID selfId, UUID otherId) {
        User self = requirePair(selfId, otherId);

        if (likeDao.unlike(selfId, otherId) > 0) {
            boolean wasConnected = likeDao.likes(otherId, selfId);
            fameService.recompute(otherId);
            if (wasConnected) {
                fameService.recompute(selfId);
                if (!blockService.isBlockedEitherWay(selfId, otherId)) {
                    notifications.notify(otherId, NotificationService.TYPE_UNLIKE, selfId,
                            self.username() + " unliked you. You are no longer connected.");
                }
            }
        }
        return status(selfId, otherId);
    }

    public LikeStatus status(UUID selfId, UUID otherId) {
        boolean likedByMe = likeDao.likes(selfId, otherId);
        boolean likesMe = likeDao.likes(otherId, selfId);
        return new LikeStatus(likedByMe, likesMe, likedByMe && likesMe);
    }

    public boolean isConnected(UUID userA, UUID userB) {
        return likeDao.likes(userA, userB) && likeDao.likes(userB, userA);
    }

    public List<ActivityResponse> likedBy(UUID selfId) {
        return likeDao.likedBy(selfId);
    }

    private User requirePair(UUID selfId, UUID otherId) {
        if (selfId.equals(otherId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot like yourself.");
        }
        if (userDao.findById(otherId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "That person no longer exists.");
        }
        return userDao.findById(selfId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "You must be signed in."));
    }
}
