package com.finalproject.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class AppConfig {
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int    DEFAULT_PORT = 6000;
    public static final String DEFAULT_APP_DB = "jdbc:sqlite:sentinelnode.db";
    public static final String DEFAULT_AUTH_DB = "jdbc:sqlite:workforce.db";
    public static final String VERSION = "1.1.0";

    private final String managerHost;
    private final int managerPort;
    private final String appJdbcUrl;
    private final String authJdbcUrl;
    private final int metricIntervalMs;
    private final int heartbeatSeconds;
    private final long staleThresholdMs;

    private AppConfig(String managerHost, int managerPort, String appJdbcUrl, String authJdbcUrl,
                      int metricIntervalMs, int heartbeatSeconds, long staleThresholdMs) {
        this.managerHost = managerHost;
        this.managerPort = managerPort;
        this.appJdbcUrl = appJdbcUrl;
        this.authJdbcUrl = authJdbcUrl;
        this.metricIntervalMs = metricIntervalMs;
        this.heartbeatSeconds = heartbeatSeconds;
        this.staleThresholdMs = staleThresholdMs;
    }

    public static AppConfig load() {
        Properties props = new Properties();
        Path file = Paths.get(System.getProperty("user.home"), ".sentinelnode", "config.properties");
        if (Files.isRegularFile(file)) {
            try (var input = Files.newInputStream(file)) {
                props.load(input);
            } catch (IOException ignored) {
            }
        }
        return new AppConfig(
            pick("SENTINEL_HOST",     "manager.host", props, DEFAULT_HOST),
            parseInt(pick("SENTINEL_PORT", "manager.port", props, String.valueOf(DEFAULT_PORT)), DEFAULT_PORT),
            pick("SENTINEL_APP_DB",  "app.db.url",  props, DEFAULT_APP_DB),
            pick("SENTINEL_AUTH_DB", "auth.db.url", props, DEFAULT_AUTH_DB),
            parseInt(pick("SENTINEL_METRIC_MS",  "metric.interval.ms", props, "1000"), 1000),
            parseInt(pick("SENTINEL_HEARTBEAT_S", "heartbeat.seconds", props, "15"),    15),
            parseLong(pick("SENTINEL_STALE_MS",  "stale.threshold.ms", props, "30000"), 30_000L)
        );
    }

    private static String pick(String envKey, String propKey, Properties props, String fallback) {
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) return env;
        String sys = System.getProperty(propKey);
        if (sys != null && !sys.isBlank()) return sys;
        String file = props.getProperty(propKey);
        if (file != null && !file.isBlank()) return file;
        return fallback;
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); }
        catch (Exception e) { return fallback; }
    }

    private static long parseLong(String value, long fallback) {
        try { return Long.parseLong(value.trim()); }
        catch (Exception e) { return fallback; }
    }

    public String managerHost()       { return managerHost; }
    public int    managerPort()       { return managerPort; }
    public String appJdbcUrl()        { return appJdbcUrl; }
    public String authJdbcUrl()       { return authJdbcUrl; }
    public int    metricIntervalMs()  { return metricIntervalMs; }
    public int    heartbeatSeconds()  { return heartbeatSeconds; }
    public long   staleThresholdMs()  { return staleThresholdMs; }
}
