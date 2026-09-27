package com.glancesync.modules.block.service;

import com.glancesync.common.exception.ApiException;
import com.glancesync.modules.block.repo.BlockDao;
import com.glancesync.modules.notification.service.NotificationService;
import com.glancesync.modules.user.repo.UserDao;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;

@Singleton
public class BlockService {

    private final BlockDao blockDao;
    private final UserDao userDao;
    private final NotificationService notifications;

    public BlockService(BlockDao blockDao, UserDao userDao, NotificationService notifications) {
        this.blockDao = blockDao;
        this.userDao = userDao;
        this.notifications = notifications;
    }

    public void block(long blockerId, long blockedId) {
        String blockerName = requireOther(blockerId, blockedId);

        blockDao.block(blockerId, blockedId);
        notifications.notify(blockedId, NotificationService.TYPE_USER_BLOCKED, blockerId,
                blockerName + " has blocked you.");
    }

    public void unblock(long blockerId, long blockedId) {
        String blockerName = requireOther(blockerId, blockedId);

        // Only say anything if something actually changed.
        if (blockDao.unblock(blockerId, blockedId) > 0) {
            notifications.notify(blockedId, NotificationService.TYPE_USER_UNBLOCKED, blockerId,
                    blockerName + " has unblocked you.");
        }
    }

    /** True when either side has blocked the other: contact is off in both directions. */
    public boolean isBlockedEitherWay(long userA, long userB) {
        return blockDao.blockedEitherWay(userA, userB);
    }

    public void assertNotBlocked(long userA, long userB) {
        if (isBlockedEitherWay(userA, userB)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot contact this person.");
        }
    }

    /** Validates the pair and returns the blocker's username for the message text. */
    private String requireOther(long blockerId, long blockedId) {
        if (blockerId == blockedId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot block yourself.");
        }
        if (userDao.findById(blockedId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "That person no longer exists.");
        }
        return userDao.findById(blockerId).map(user -> user.username()).orElse("Someone");
    }
}
