package com.finalproject.manager;

import com.finalproject.app.AppConfig;
import com.finalproject.auth.AuthService;
import com.finalproject.db.AppDatabase;
import com.finalproject.manager.bans.BanService;
import com.finalproject.manager.quota.QuotaRequest;
import com.finalproject.manager.quota.QuotaService;
import com.finalproject.manager.tags.TagService;
import com.finalproject.manager.templates.TemplateService;
import com.finalproject.model.TaskType;
import com.finalproject.model.WorkerSnapshot;
import com.finalproject.net.Message;
import com.finalproject.net.MessageTypes;
import com.finalproject.notes.Note;
import com.finalproject.notes.NotesService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ManagerController {
    private final AppConfig config;
    private final AppDatabase database;
    private final WorkerRegistry registry = new WorkerRegistry();
    private final AuthService authService;
    private final NotesService notesService;
    private final TemplateService templateService;
    private final TagService tagService;
    private final BanService banService;
    private final QuotaService quotaService;
    private final List<NoteListener> noteListeners = new CopyOnWriteArrayList<>();
    private final List<QuotaListener> quotaListeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "heartbeat");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean serverStarted;
    private volatile int boundPort = -1;

    public ManagerController(AppDatabase database, AuthService authService) {
        this(AppConfig.load(), database, authService);
    }

    public ManagerController(AppConfig config, AppDatabase database, AuthService authService) {
        this.config = config;
        this.database = database;
        this.authService = authService;
        this.notesService = new NotesService(database);
        this.templateService = new TemplateService(database);
        this.tagService = new TagService(database);
        this.banService = new BanService(database);
        this.quotaService = new QuotaService(database);
    }

    public AppConfig config() { return config; }
    public WorkerRegistry registry() { return registry; }
    public AppDatabase database() { return database; }
    public AuthService auth() { return authService; }
    public NotesService notes() { return notesService; }
    public TemplateService templates() { return templateService; }
    public TagService tags() { return tagService; }
    public BanService bans() { return banService; }
    public QuotaService quotas() { return quotaService; }
    public int boundPort() { return boundPort > 0 ? boundPort : config.managerPort(); }

    public void addQuotaListener(QuotaListener listener) {
        if (listener != null) quotaListeners.add(listener);
    }

    public void removeQuotaListener(QuotaListener listener) {
        quotaListeners.remove(listener);
    }

    private void publishQuota(QuotaRequest request, String kind) {
        for (QuotaListener listener : quotaListeners) {
            try { listener.onQuotaEvent(request, kind); } catch (Exception ignored) {}
        }
    }

    public void addNoteListener(NoteListener listener) {
        noteListeners.add(listener);
    }

    public void removeNoteListener(NoteListener listener) {
        noteListeners.remove(listener);
    }

    private void publishNote(Note note, String kind) {
        for (NoteListener listener : noteListeners) {
            try { listener.onNoteEvent(note, kind); } catch (Exception ignored) {}
        }
    }

    public synchronized void startServer(int port) {
        if (serverStarted) {
            return;
        }
        serverStarted = true;
        boundPort = port;
        executor.submit(() -> acceptLoop(port));
        heartbeatScheduler.scheduleAtFixedRate(
            this::heartbeatTick,
            config.heartbeatSeconds(),
            config.heartbeatSeconds(),
            TimeUnit.SECONDS
        );
        templateService.seedDefaultsIfEmpty("system");
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

    private void heartbeatTick() {
        for (WorkerSession session : registry.allSessions()) {
            if (session.isStale(config.staleThresholdMs())) {
                String workerId = session.workerId() == null ? "unknown" : session.workerId();
                database.logWorkerEvent(workerId, "HEARTBEAT_TIMEOUT", "no PONG received in " + config.staleThresholdMs() + "ms");
                registry.disconnect(session);
                session.closeQuietly();
            } else {
                session.ping();
            }
        }
    }

    public void handleMessage(WorkerSession session, Message message) {
        switch (message.type()) {
            case MessageTypes.HELLO         -> onHello(session, message);
            case MessageTypes.METRIC        -> onMetric(session, message);
            case MessageTypes.PONG          -> session.recordPong();
            case MessageTypes.TASK_ACCEPTED -> onTaskAccepted(session, message);
            case MessageTypes.TASK_PROGRESS -> onTaskProgress(session, message);
            case MessageTypes.TASK_DONE     -> onTaskDone(session, message);
            case MessageTypes.TASK_FAILED   -> onTaskFailed(session, message);
            case MessageTypes.NOTE          -> onNote(session, message);
            case MessageTypes.NOTE_ACK      -> onNoteAck(session, message);
            case MessageTypes.QUOTA_REQUEST -> onQuotaRequest(session, message);
            default                          -> database.logWorkerEvent(resolveWorkerId(session, message), "UNKNOWN_MESSAGE", message.type());
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
        database.logTaskEvent(workerId, taskId, taskType.name(), sent ? "SENT" : "REJECTED",
            sent ? "task dispatched" : "worker busy or disconnected");
        return sent;
    }

    public List<String> dispatchToTag(String tag, TaskType taskType, String payload) {
        List<String> dispatched = new ArrayList<>();
        for (String username : tagService.usersByTag(tag)) {
            for (WorkerSnapshot snapshot : registry.snapshotsForUsername(username)) {
                if (snapshot.connected() && !snapshot.busy()) {
                    if (sendTask(snapshot.workerId(), taskType, payload)) {
                        dispatched.add(snapshot.workerId());
                    }
                }
            }
        }
        return dispatched;
    }

    public boolean forceDisconnect(String workerId) {
        boolean ok = registry.disconnectById(workerId);
        if (ok) {
            database.logWorkerEvent(workerId, "FORCED_DISCONNECT", "disconnected via manager");
        }
        return ok;
    }

    public long sendNote(String fromUser, String toWorkerId, String toTag, String body) {
        long id = notesService.send(fromUser, toWorkerId, toTag, body);
        if (id < 0) return id;
        Note saved = notesService.find(id).orElse(null);
        if (saved != null) {
            publishNote(saved, "SENT");
            deliverNote(saved);
        }
        return id;
    }

    private void deliverNote(Note note) {
        if (note.recipientWorkerId() != null && !note.recipientWorkerId().isBlank()) {
            registry.sessionFor(note.recipientWorkerId()).ifPresent(session -> sendNoteToSession(session, note));
            return;
        }
        if (note.recipientTag() != null && !note.recipientTag().isBlank()) {
            for (String username : tagService.usersByTag(note.recipientTag())) {
                for (WorkerSnapshot snapshot : registry.snapshotsForUsername(username)) {
                    registry.sessionFor(snapshot.workerId()).ifPresent(session -> sendNoteToSession(session, note));
                }
            }
            return;
        }
        for (WorkerSession session : registry.allSessions()) {
            sendNoteToSession(session, note);
        }
    }

    private void sendNoteToSession(WorkerSession session, Note note) {
        session.send(Message.of(MessageTypes.NOTE)
            .with("noteId", String.valueOf(note.id()))
            .with("from", note.senderUsername())
            .with("body", note.body())
            .with("ts", note.timestamp().toString()));
        notesService.markDelivered(note.id());
    }

    public void flushPendingNotes(String workerId) {
        List<Note> pending = notesService.pendingFor(workerId);
        Optional<WorkerSession> session = registry.sessionFor(workerId);
        if (session.isEmpty()) return;
        for (Note note : pending) {
            sendNoteToSession(session.get(), note);
        }
    }

    private void onHello(WorkerSession session, Message message) {
        String workerId = message.fields().getOrDefault("workerId", "worker-" + Instant.now().toEpochMilli());
        String host = message.fields().getOrDefault("host", "unknown");
        String username = message.fields().getOrDefault("username", "");
        String token = message.fields().getOrDefault("token", "");
        if (!token.isBlank() && authService != null) {
            Optional<com.finalproject.model.User> maybe = authService.validateToken(token);
            if (maybe.isPresent()) {
                username = maybe.get().username();
            }
        }
        if (!username.isBlank() && banService.isBanned(username)) {
            database.logWorkerEvent(workerId, "REJECTED", "user banned: " + username);
            session.send(Message.of(MessageTypes.AUTH_FAILED).with("reason", "Account banned"));
            session.closeQuietly();
            return;
        }
        session.recordPong();
        registry.register(workerId, session, host, username);
        database.logWorkerEvent(workerId, "CONNECTED", "host=" + host + (username.isBlank() ? "" : ", username=" + username));
        flushPendingNotes(workerId);
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
        var snapshot = registry.snapshotFor(workerId);
        snapshot.applyMetric(cpu, memory, heapUsed, threads, procCpuMs, taskType, taskId, progress);
        if (message.fields().containsKey("credits") || message.fields().containsKey("budget")) {
            int credits = parseInt(message.fields().getOrDefault("credits", "-1"));
            int budget  = parseInt(message.fields().getOrDefault("budget",  "-1"));
            snapshot.setQuota(credits, budget);
        }
        database.logMetric(workerId, cpu, memory, procCpuNs, heapUsed, threads, taskType, taskId, progress);
    }

    private void onQuotaRequest(WorkerSession session, Message message) {
        String workerId = resolveWorkerId(session, message);
        String taskId = message.fields().getOrDefault("taskId", "");
        String taskType = message.fields().getOrDefault("taskType", "");
        String payload = message.fields().getOrDefault("payload", "");
        int requested = parseInt(message.fields().getOrDefault("requested", "1"));
        int have = parseInt(message.fields().getOrDefault("have", "0"));
        long requestId = quotaService.record(workerId, taskId, taskType, payload, requested, have);
        database.logWorkerEvent(workerId, "QUOTA_REQUEST",
            String.format("type=%s need=%d have=%d", taskType, requested, have));
        quotaService.find(requestId).ifPresent(request -> publishQuota(request, "REQUESTED"));
    }

    public boolean grantQuota(long requestId, int amount, String grantedBy) {
        var requestOpt = quotaService.find(requestId);
        if (requestOpt.isEmpty() || !requestOpt.get().isOpen()) return false;
        QuotaRequest request = requestOpt.get();
        boolean granted = quotaService.grant(requestId, amount, grantedBy);
        if (!granted) return false;
        registry.sessionFor(request.workerId()).ifPresent(session -> session.send(
            Message.of(MessageTypes.QUOTA_GRANT)
                .with("workerId", request.workerId())
                .with("amount", String.valueOf(amount))
                .with("requestId", String.valueOf(requestId))));
        database.logWorkerEvent(request.workerId(), "QUOTA_GRANTED",
            "grantedBy=" + grantedBy + " amount=" + amount);
        quotaService.find(requestId).ifPresent(updated -> publishQuota(updated, "GRANTED"));
        replayRejectedTask(request);
        return true;
    }

    private void replayRejectedTask(QuotaRequest request) {
        if (!request.canReplay()) return;
        com.finalproject.model.TaskType type;
        try {
            type = com.finalproject.model.TaskType.fromString(request.taskType());
        } catch (Exception e) {
            return;
        }
        // Tiny delay so the QUOTA_GRANT lands first and credits are topped up
        // before the replayed TASK arrives.
        executor.submit(() -> {
            try { Thread.sleep(120); } catch (InterruptedException ignored) {}
            sendTask(request.workerId(), type, request.payload());
        });
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

    private void onNote(WorkerSession session, Message message) {
        String workerId = resolveWorkerId(session, message);
        String body = message.fields().getOrDefault("body", "");
        String fromUser = session.username();
        if (fromUser.isBlank()) fromUser = workerId;
        long id = notesService.send(fromUser, null, null, body);
        notesService.markDelivered(id);
        notesService.markAcked(id);
        Note note = notesService.find(id).orElse(null);
        if (note != null) {
            publishNote(note, "RECEIVED");
        }
        database.logWorkerEvent(workerId, "NOTE_FROM_WORKER", body);
    }

    private void onNoteAck(WorkerSession session, Message message) {
        String idText = message.fields().getOrDefault("noteId", "");
        if (idText.isBlank()) return;
        try {
            long noteId = Long.parseLong(idText);
            notesService.markAcked(noteId);
            notesService.find(noteId).ifPresent(note -> publishNote(note, "ACK"));
        } catch (NumberFormatException ignored) {
        }
    }

    private String resolveWorkerId(WorkerSession session, Message message) {
        String workerId = session.workerId();
        if (workerId != null) {
            return workerId;
        }
        return message.fields().getOrDefault("workerId", "unknown");
    }

    private double parseDouble(String value) {
        try { return Double.parseDouble(value); } catch (NumberFormatException e) { return 0; }
    }

    private int parseInt(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return 0; }
    }

    private long parseLong(String value) {
        try { return Long.parseLong(value); } catch (NumberFormatException e) { return 0L; }
    }
}
