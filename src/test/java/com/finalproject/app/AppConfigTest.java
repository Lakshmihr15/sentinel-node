package com.finalproject.app;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @AfterEach
    void cleanup() {
        System.clearProperty("manager.host");
        System.clearProperty("manager.port");
        System.clearProperty("app.db.url");
        System.clearProperty("metric.interval.ms");
        System.clearProperty("heartbeat.seconds");
        System.clearProperty("stale.threshold.ms");
    }

    @Test
    void defaultsAreSensible() {
        AppConfig config = AppConfig.load();
        assertEquals(AppConfig.DEFAULT_HOST, config.managerHost());
        assertEquals(AppConfig.DEFAULT_PORT, config.managerPort());
        assertEquals(AppConfig.DEFAULT_APP_DB, config.appJdbcUrl());
        assertEquals(AppConfig.DEFAULT_AUTH_DB, config.authJdbcUrl());
        assertEquals(1000, config.metricIntervalMs());
        assertEquals(15, config.heartbeatSeconds());
        assertEquals(30_000L, config.staleThresholdMs());
    }

    @Test
    void systemPropertyOverridesDefault() {
        System.setProperty("manager.host", "10.0.0.5");
        System.setProperty("manager.port", "7777");
        AppConfig config = AppConfig.load();
        assertEquals("10.0.0.5", config.managerHost());
        assertEquals(7777, config.managerPort());
    }

    @Test
    void invalidPortFallsBackToDefault() {
        System.setProperty("manager.port", "not-a-number");
        AppConfig config = AppConfig.load();
        assertEquals(AppConfig.DEFAULT_PORT, config.managerPort());
    }
}
