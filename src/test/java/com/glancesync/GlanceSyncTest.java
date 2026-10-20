package com.glancesync;

import com.glancesync.common.config.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GlanceSyncTest {
    @Test
    void loadsSparkServerConfiguration() {
        assertTrue(new AppConfig().getInt("server.port") > 0);
    }
}
