package com.finalproject.manager;

import com.finalproject.model.TaskType;
import com.finalproject.model.WorkerSnapshot;
import com.finalproject.net.Message;
import com.finalproject.net.MessageTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class WorkerRegistry {
    private final Map<String, WorkerSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, WorkerSnapshot> snapshots = new ConcurrentHashMap<>();

    public WorkerSnapshot snapshotFor(String workerId) {
        return snapshots.computeIfAbsent(workerId, WorkerSnapshot::new);
    }

    public List<WorkerSnapshot> snapshots() {
        return Collections.unmodifiableList(new ArrayList<>(snapshots.values()));
    }

    public List<WorkerSession> allSessions() {
        return Collections.unmodifiableList(new ArrayList<>(sessions.values()));
    }

    public Optional<WorkerSession> sessionFor(String workerId) {
        return Optional.ofNullable(sessions.get(workerId));
    }

    public List<WorkerSnapshot> snapshotsForUsername(String username) {
        if (username == null || username.isBlank()) return List.of();
        List<WorkerSnapshot> out = new ArrayList<>();
        for (WorkerSnapshot snapshot : snapshots.values()) {
            if (username.equalsIgnoreCase(snapshot.username())) {
                out.add(snapshot);
            }
        }
        return out;
    }

    public void register(String workerId, WorkerSession session, String host) {
        register(workerId, session, host, "");
    }

    public void register(String workerId, WorkerSession session, String host, String username) {
        session.setWorkerId(workerId);
        session.setUsername(username);
        sessions.put(workerId, session);
        WorkerSnapshot snapshot = snapshotFor(workerId);
        snapshot.markConnected(host);
        snapshot.setUsername(username);
    }

    public void disconnect(WorkerSession session) {
        if (session.workerId() != null) {
            sessions.remove(session.workerId(), session);
            snapshotFor(session.workerId()).markDisconnected();
        }
    }

    public boolean sendTask(String workerId, String taskId, TaskType taskType, String payload) {
        WorkerSession session = sessions.get(workerId);
        WorkerSnapshot snapshot = snapshots.get(workerId);
        if (session == null || snapshot == null) {
            return false;
        }
        synchronized (session) {
            if (snapshot.busy()) {
                return false;
            }
            snapshot.updateTaskState(taskType.name(), taskId, 0, true);
            session.send(Message.of(MessageTypes.TASK)
                .with("workerId", workerId)
                .with("taskId", taskId)
                .with("taskType", taskType.name())
                .with("payload", payload == null ? "" : payload));
            return true;
        }
    }

    public boolean disconnectById(String workerId) {
        WorkerSession session = sessions.get(workerId);
        if (session == null) return false;
        try {
            session.send(Message.of(MessageTypes.KICK).with("reason", "manager forced disconnect"));
        } catch (Exception ignored) {
        }
        try {
            session.closeQuietly();
        } catch (Exception ignored) {}
        sessions.remove(workerId);
        snapshotFor(workerId).markDisconnected();
        return true;
    }
}
