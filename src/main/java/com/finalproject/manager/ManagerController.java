package com.finalproject.manager;

import com.finalproject.db.AppDatabase;
import com.finalproject.model.TaskType;
import com.finalproject.net.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ManagerController {
    /** How often to send a PING to each worker (seconds). */
    private static final int PING_INTERVAL_SECONDS = 15;
    /** How long without a PONG before a session is considered dead (ms). */
    private static final long STALE_THRESHOLD_MS = 30_000;

    private final AppDatabase database;
    private final WorkerRegistry registry = new WorkerRegistry();
    private final com.finalproject.auth.AuthService authService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "heartbeat");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean serverStarted;

    public ManagerController(AppDatabase database, com.finalproject.auth.AuthService authService) {
        this.database = database;
        this.authService = authService;
    }

    public WorkerRegistry registry() {
        return registry;
    }

    public AppDatabase database() {
        return database;
    }

    public synchronized void startServer(int port) {
        if (serverStarted) {
            return;
        }
        serverStarted = true;
        executor.submit(() -> acceptLoop(port));
        heartbeatScheduler.scheduleAtFixedRate(
            this::heartbeatTick,
            PING_INTERVAL_SECONDS,
            PING_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
    }

    private void acceptLoop(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = serverSocket.accept();
                WorkerSession session = new WorkerSession(socket, this);
                executor.submit(session);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Manager server failed", exception);
        }
    }

    /**
     * Runs every {@code PING_INTERVAL_SECONDS}. Pings all registered sessions and
     * forcibly disconnects any that have not replied within {@code STALE_THRESHOLD_MS}.
     */
    private void heartbeatTick() {
        for (WorkerSession session : registry.allSessions()) {
            if (session.isStale(STALE_THRESHOLD_MS)) {
                // Worker missed its PONG window — close the session
                String workerId = session.workerId() == null ? "unknown" : session.workerId();
                database.logWorkerEvent(workerId, "HEARTBEAT_TIMEOUT", "no PONG received in " + STALE_THRESHOLD_MS + "ms");
                registry.disconnect(session);
                session.closeQuietly();
            } else {
                session.ping();
            }
        }
    }

    public void handleMessage(WorkerSession session, Message message) {
        switch (message.type()) {
            case "HELLO"         -> onHello(session, message);
            case "METRIC"        -> onMetric(session, message);
            case "PONG"          -> session.recordPong();
            case "TASK_ACCEPTED" -> onTaskAccepted(session, message);
            case "TASK_PROGRESS" -> onTaskProgress(session, message);
            case "TASK_DONE"     -> onTaskDone(session, message);
            case "TASK_FAILED"   -> onTaskFailed(session, message);
            default              -> database.logWorkerEvent(resolveWorkerId(session, message), "UNKNOWN_MESSAGE", message.type());
        }
    }

    public void handleDisconnect(WorkerSession session, String reason) {
        registry.disconnect(session);
        String workerId = session.workerId() == null ? "unknown" : session.workerId();
        database.logWorkerEvent(workerId, "DISCONNECTED", reason == null ? "socket closed" : reason);
    }

    public boolean sendTask(String workerId, TaskType taskType, String payload) {
        String taskId = UUID.randomUUID().toString();
        boolean sent = registry.sendTask(workerId, taskId, taskType, payload);
        database.logTaskEvent(workerId, taskId, taskType.name(), sent ? "SENT" : "REJECTED", sent ? "task dispatched" : "worker busy or disconnected");
        return sent;
    }

    public boolean forceDisconnect(String workerId) {
        boolean ok = registry.disconnectById(workerId);
        if (ok) {
            database.logWorkerEvent(workerId, "FORCED_DISCONNECT", "disconnected via manager");
        }
        return ok;
    }

    private void onHello(WorkerSession session, Message message) {
        String workerId = message.fields().getOrDefault("workerId", "worker-" + Instant.now().toEpochMilli());
        String host = message.fields().getOrDefault("host", "unknown");
        String username = message.fields().getOrDefault("username", "");
        String token = message.fields().getOrDefault("token", "");
        if (!token.isBlank() && authService != null) {
            var maybe = authService.validateToken(token);
            if (maybe.isPresent()) {
                username = maybe.get().username();
            }
        }
        // First HELLO resets the pong timer so the session isn't immediately stale
        session.recordPong();
        registry.register(workerId, session, host, username);
        database.logWorkerEvent(workerId, "CONNECTED", "host=" + host + (username.isBlank() ? "" : ", username=" + username));
    }

    private void onMetric(WorkerSession session, Message message) {
        String workerId = resolveWorkerId(session, message);
        double cpu = parseDouble(message.fields().getOrDefault("cpu", "0"));
        double memory = parseDouble(message.fields().getOrDefault("memory", "0"));
        double heapUsed = parseDouble(message.fields().getOrDefault("heapUsed", "0"));
        int threads = parseInt(message.fields().getOrDefault("threads", "0"));
        long procCpuNs = parseLong(message.fields().getOrDefault("procCpuNs", "0"));
        double procCpuMs = procCpuNs / 1_000_000.0;
        String taskType = message.fields().getOrDefault("taskType", "IDLE");
        String taskId = message.fields().getOrDefault("taskId", "");
        int progress = parseInt(message.fields().getOrDefault("progress", "0"));
        registry.snapshotFor(workerId).applyMetric(cpu, memory, heapUsed, threads, procCpuMs, taskType, taskId, progress);
        database.logMetric(workerId, cpu, memory, procCpuNs, heapUsed, threads, taskType, taskId, progress);
    }

    private void onTaskAccepted(WorkerSession session, Message message) {
        String workerId = resolveWorkerId(session, message);
        String taskId = message.fields().getOrDefault("taskId", "");
        String taskType = message.fields().getOrDefault("taskType", "UNKNOWN");
        registry.snapshotFor(workerId).updateTaskState(taskType, taskId, 0, true);
        database.logTaskEvent(workerId, taskId, taskType, "ACCEPTED", message.fields().getOrDefault("details", "task accepted"));
    }

    private void onTaskProgress(WorkerSession session, Message message) {
        String workerId = resolveWorkerId(session, message);
        String taskId = message.fields().getOrDefault("taskId", "");
        String taskType = message.fields().getOrDefault("taskType", "UNKNOWN");
        int progress = parseInt(message.fields().getOrDefault("progress", "0"));
        registry.snapshotFor(workerId).updateTaskState(taskType, taskId, progress, progress < 100);
        database.logTaskEvent(workerId, taskId, taskType, "PROGRESS", "progress=" + progress);
    }

    private void onTaskDone(WorkerSession session, Message message) {
        String workerId = resolveWorkerId(session, message);
        String taskId = message.fields().getOrDefault("taskId", "");
        String taskType = message.fields().getOrDefault("taskType", "UNKNOWN");
        String result = message.fields().getOrDefault("result", "done");
        registry.snapshotFor(workerId).updateTaskState("IDLE", taskId, 100, false);
        database.logTaskEvent(workerId, taskId, taskType, "DONE", result);
    }

    private void onTaskFailed(WorkerSession session, Message message) {
        String workerId = resolveWorkerId(session, message);
        String taskId = message.fields().getOrDefault("taskId", "");
        String taskType = message.fields().getOrDefault("taskType", "UNKNOWN");
        String error = message.fields().getOrDefault("error", "failed");
        registry.snapshotFor(workerId).updateTaskState("IDLE", taskId, 0, false);
        database.logTaskEvent(workerId, taskId, taskType, "FAILED", error);
    }

    private String resolveWorkerId(WorkerSession session, Message message) {
        String workerId = session.workerId();
        if (workerId != null) {
            return workerId;
        }
        return message.fields().getOrDefault("workerId", "unknown");
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}