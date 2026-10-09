package com.glancesync.modules.fame.service;

import java.util.UUID;
import com.glancesync.modules.fame.repo.FameDao;

public class FameService {

    public static final int POINTS_PER_VIEWER = 1;
    public static final int POINTS_PER_LIKE = 5;
    public static final int POINTS_PER_CONNECTION = 10;
    public static final int POINTS_PER_REPORT = -20;

    private final FameDao fameDao;

    public FameService(FameDao fameDao) {
        this.fameDao = fameDao;
    }

    public void recompute(UUID userId) {
        fameDao.recompute(userId, POINTS_PER_VIEWER, POINTS_PER_LIKE, POINTS_PER_CONNECTION, POINTS_PER_REPORT);
    }
}
