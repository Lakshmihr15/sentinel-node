package com.finalproject.model;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class WorkerSnapshot {
    private final String workerId;
    private String host = "";
    private String username = "";
    private boolean connected;
    private boolean busy;
    private String taskType = "IDLE";
    private String taskId = "";
    private int progress;
    private double cpu;
    private double memory;
    private double heapUsed;
    private int threads;
    private int credits = -1;
    private int budget = -1;
    private Instant lastSeen = Instant.now();
    private Instant connectedAt = null;
    private final Deque<MetricSample> history = new ArrayDeque<>();

    public WorkerSnapshot(String workerId) {
        this.workerId = workerId;
    }

    public synchronized void markConnected(String host) {
        this.host = host == null ? "" : host;
        this.connected = true;
        this.connectedAt = Instant.now();
        this.lastSeen = Instant.now();
    }

    public synchronized void setUsername(String username) {
        this.username = username == null ? "" : username;
    }

    public synchronized String username() {
        return username == null || username.isBlank() ? workerId : username;
    }

    public synchronized void markDisconnected() {
        this.connected = false;
        this.busy = false;
        this.taskType = "IDLE";
        this.taskId = "";
        this.progress = 0;
        this.lastSeen = Instant.now();
    }

    public synchronized void applyMetric(double cpu, double memory, double heapUsed, int threads, double procCpuMs, String taskType, String taskId, int progress) {
        this.cpu = cpu;
        this.heapUsed = heapUsed;
        this.threads = threads;
        this.memory = memory;
        this.taskType = taskType == null || taskType.isBlank() ? this.taskType : taskType;
        this.taskId = taskId == null ? this.taskId : taskId;
        this.progress = progress;
        this.busy = progress < 100 && !"IDLE".equalsIgnoreCase(this.taskType);
        this.lastSeen = Instant.now();
        history.addLast(new MetricSample(Instant.now(), cpu, memory, heapUsed, threads, procCpuMs));
        while (history.size() > 60) {
            history.removeFirst();
        }
    }

    public synchronized double heapUsed() {
        return heapUsed;
    }

    public synchronized int threads() {
        return threads;
    }

    public synchronized void setQuota(int credits, int budget) {
        if (credits >= 0) this.credits = credits;
        if (budget > 0)   this.budget = budget;
    }

    public synchronized int credits() {
        return credits;
    }

    public synchronized int budget() {
        return budget;
    }

    public synchronized void updateTaskState(String taskType, String taskId, int progress, boolean busy) {
        this.taskType = taskType;
        this.taskId = taskId == null ? "" : taskId;
        this.progress = progress;
        this.busy = busy;
        this.lastSeen = Instant.now();
    }

    public synchronized List<MetricSample> history() {
        return new ArrayList<>(history);
    }

    public String workerId() {
        return workerId;
    }

    public synchronized String host() {
        return host;
    }

    public synchronized boolean connected() {
        return connected;
    }

    public synchronized boolean busy() {
        return busy;
    }

    public synchronized String taskType() {
        return taskType;
    }

    public synchronized String taskId() {
        return taskId;
    }

    public synchronized int progress() {
        return progress;
    }

    public synchronized double cpu() {
        return cpu;
    }

    public synchronized double memory() {
        return memory;
    }

    public synchronized Instant lastSeen() {
        return lastSeen;
    }

    /** Returns how long (in whole seconds) this worker has been connected, or -1 if not connected. */
    public synchronized long uptimeSeconds() {
        if (!connected || connectedAt == null) {
            return -1L;
        }
        return java.time.Duration.between(connectedAt, Instant.now()).getSeconds();
    }
}