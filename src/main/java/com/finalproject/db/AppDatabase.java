package com.finalproject.db;

import com.finalproject.model.TaskEventRecord;
import com.finalproject.model.WorkerMetricRecord;
import com.finalproject.model.WorkerEventRecord;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AppDatabase {
    private final String jdbcUrl;

    public AppDatabase(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public static AppDatabase defaultDatabase() {
        return new AppDatabase("jdbc:sqlite:sentinelnode.db");
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public void initialize() {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS worker_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts TEXT NOT NULL,
                    worker_id TEXT NOT NULL,
                    event_type TEXT NOT NULL,
                    details TEXT NOT NULL
                )
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS worker_metrics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts TEXT NOT NULL,
                    worker_id TEXT NOT NULL,
                    cpu REAL NOT NULL,
                    memory REAL NOT NULL,
                    proc_cpu_ns INTEGER NOT NULL DEFAULT 0,
                    heap_used REAL NOT NULL DEFAULT 0,
                    thread_count INTEGER NOT NULL DEFAULT 0,
                    task_type TEXT NOT NULL,
                    task_id TEXT NOT NULL,
                    progress INTEGER NOT NULL
                )
                """);
            tryAddColumn(statement, "worker_metrics", "proc_cpu_ns",  "INTEGER NOT NULL DEFAULT 0");
            tryAddColumn(statement, "worker_metrics", "heap_used",    "REAL NOT NULL DEFAULT 0");
            tryAddColumn(statement, "worker_metrics", "thread_count", "INTEGER NOT NULL DEFAULT 0");
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS task_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts TEXT NOT NULL,
                    worker_id TEXT NOT NULL,
                    task_id TEXT NOT NULL,
                    task_type TEXT NOT NULL,
                    status TEXT NOT NULL,
                    details TEXT NOT NULL
                )
                """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts TEXT NOT NULL,
                    sender_username TEXT NOT NULL,
                    recipient_worker_id TEXT,
                    recipient_tag TEXT,
                    body TEXT NOT NULL,
                    delivered_at TEXT,
                    ack_at TEXT
                )
                """);
            statement.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_notes_recipient ON notes(recipient_worker_id, ack_at)");

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS task_templates (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT UNIQUE NOT NULL,
                    task_type TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    created_by TEXT NOT NULL
                )
                """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS worker_tags (
                    username TEXT NOT NULL,
                    tag TEXT NOT NULL,
                    PRIMARY KEY(username, tag)
                )
                """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS banned_workers (
                    username TEXT PRIMARY KEY,
                    banned_at TEXT NOT NULL,
                    banned_by TEXT NOT NULL,
                    reason TEXT
                )
                """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize database", exception);
        }
    }

    private void tryAddColumn(Statement statement, String table, String column, String definition) {
        try {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (Exception ignored) {
        }
    }

    public void logWorkerEvent(String workerId, String eventType, String details) {
        execute("INSERT INTO worker_events(ts, worker_id, event_type, details) VALUES(?, ?, ?, ?)",
            Instant.now().toString(), workerId, eventType, details);
    }

    public void logMetric(String workerId, double cpu, double memory, long procCpuNs, double heapUsed, int threadCount, String taskType, String taskId, int progress) {
        execute("INSERT INTO worker_metrics(ts, worker_id, cpu, memory, proc_cpu_ns, heap_used, thread_count, task_type, task_id, progress) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            Instant.now().toString(), workerId, cpu, memory, procCpuNs, heapUsed, threadCount, taskType, taskId, progress);
    }

    public void logTaskEvent(String workerId, String taskId, String taskType, String status, String details) {
        execute("INSERT INTO task_events(ts, worker_id, task_id, task_type, status, details) VALUES(?, ?, ?, ?, ?, ?)",
            Instant.now().toString(), workerId, taskId, taskType, status, details);
    }

    public List<WorkerEventRecord> recentWorkerEvents(int limit) {
        List<WorkerEventRecord> events = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT ts, worker_id, event_type, details FROM worker_events ORDER BY id DESC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    events.add(new WorkerEventRecord(
                        Instant.parse(resultSet.getString("ts")),
                        resultSet.getString("worker_id"),
                        resultSet.getString("event_type"),
                        resultSet.getString("details")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load worker events", exception);
        }
        return events;
    }

    public List<TaskEventRecord> recentTaskEvents(int limit) {
        List<TaskEventRecord> events = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT ts, worker_id, task_id, task_type, status, details FROM task_events ORDER BY id DESC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    events.add(new TaskEventRecord(
                        Instant.parse(resultSet.getString("ts")),
                        resultSet.getString("worker_id"),
                        resultSet.getString("task_id"),
                        resultSet.getString("task_type"),
                        resultSet.getString("status"),
                        resultSet.getString("details")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load task events", exception);
        }
        return events;
    }

    public List<WorkerMetricRecord> recentWorkerMetrics(int limit) {
        List<WorkerMetricRecord> metrics = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT ts, worker_id, cpu, memory, proc_cpu_ns, heap_used, thread_count, task_type, task_id, progress FROM worker_metrics ORDER BY id DESC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    metrics.add(new WorkerMetricRecord(
                        Instant.parse(resultSet.getString("ts")),
                        resultSet.getString("worker_id"),
                        resultSet.getDouble("cpu"),
                        resultSet.getDouble("memory"),
                        resultSet.getLong("proc_cpu_ns"),
                        resultSet.getDouble("heap_used"),
                        resultSet.getInt("thread_count"),
                        resultSet.getString("task_type"),
                        resultSet.getString("task_id"),
                        resultSet.getInt("progress")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load worker metrics", exception);
        }
        return metrics;
    }

    public Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void execute(String sql, Object... values) {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Database write failed", exception);
        }
    }
}
