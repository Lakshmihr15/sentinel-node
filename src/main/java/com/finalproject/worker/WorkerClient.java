package com.finalproject.worker;

import com.finalproject.net.Message;
import com.finalproject.net.MessageCodec;
import com.finalproject.net.MessageTypes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkerClient implements Runnable {
    private static final int MAX_RETRIES = 30;
    private static final long RETRY_DELAY_SECONDS = 3;

    private final String workerId;
    private final String host;
    private final int port;
    private final String username;
    private final String token;
    private final int metricIntervalMs;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicBoolean taskLock = new AtomicBoolean(false);
    private final SystemMetrics metrics = new SystemMetrics();
    private final CopyOnWriteArrayList<WorkerListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicInteger credits;
    private final int budget;

    private volatile String currentTaskId = "";
    private volatile String currentTaskType = "IDLE";
    private volatile int currentTaskCost;
    private volatile int progress;
    private volatile Socket socket;
    private volatile BufferedReader reader;
    private volatile BufferedWriter writer;

    public WorkerClient(String workerId, String host, int port) {
        this(workerId, host, port,
            System.getenv().getOrDefault("WORKER_USERNAME", System.getProperty("worker.username", "")),
            System.getenv().getOrDefault("WORKER_TOKEN",    System.getProperty("worker.token", "")),
            1000);
    }

    public WorkerClient(String workerId, String host, int port,
                        String username, String token, int metricIntervalMs) {
        this.workerId = workerId;
        this.host = host;
        this.port = port;
        this.username = username == null ? "" : username;
        this.token = token == null ? "" : token;
        this.metricIntervalMs = Math.max(250, metricIntervalMs);
        this.budget = parseEnvInt("WORKER_CREDITS", TaskCost.DEFAULT_BUDGET);
        this.credits = new AtomicInteger(this.budget);
    }

    private static int parseEnvInt(String key, int fallback) {
        String value = System.getenv(key);
        if (value == null) value = System.getProperty(key.toLowerCase());
        if (value == null || value.isBlank()) return fallback;
        try { return Math.max(1, Integer.parseInt(value.trim())); }
        catch (NumberFormatException e) { return fallback; }
    }

    public void addListener(WorkerListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(WorkerListener listener) {
        listeners.remove(listener);
    }

    public String workerId()  { return workerId; }
    public String username()  { return username; }
    public String managerHost() { return host; }
    public int managerPort()  { return port; }
    public int credits()      { return credits.get(); }
    public int budget()       { return budget; }

    public void shutdown() {
        stopped.set(true);
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        executor.shutdownNow();
    }

    private void emit(WorkerEvent event) {
        for (WorkerListener listener : listeners) {
            try { listener.onEvent(event); } catch (Exception ignored) {}
        }
    }

    @Override
    public void run() {
        int attempts = 0;
        while (!stopped.get() && attempts <= MAX_RETRIES) {
            if (attempts > 0) {
                try {
                    TimeUnit.SECONDS.sleep(RETRY_DELAY_SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            try {
                runSession();
            } catch (IOException exception) {
                emit(new WorkerEvent.Disconnected(exception.getMessage()));
            }
            attempts++;
        }
        executor.shutdownNow();
    }

    private void runSession() throws IOException {
        try (Socket connected = new Socket(host, port)) {
            this.socket = connected;
            this.reader = new BufferedReader(new InputStreamReader(connected.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new BufferedWriter(new OutputStreamWriter(connected.getOutputStream(), StandardCharsets.UTF_8));

            send(Message.of(MessageTypes.HELLO)
                .with("workerId", workerId)
                .with("host", connected.getLocalAddress().getHostAddress())
                .with("username", username)
                .with("token", token)
                .with("budget", String.valueOf(budget)));

            emit(new WorkerEvent.Connected(host, port));
            emit(new WorkerEvent.QuotaChanged(credits.get(), budget));
            executor.submit(this::metricLoop);
            String line;
            while ((line = reader.readLine()) != null) {
                handle(MessageCodec.decode(line));
            }
            emit(new WorkerEvent.Disconnected("server closed"));
        } finally {
            this.reader = null;
            this.writer = null;
        }
    }

    private void metricLoop() {
        while (!Thread.currentThread().isInterrupted() && !stopped.get()) {
            double cpu = metrics.cpuPercent();
            double memory = metrics.memoryPercent();
            long procCpuNs = metrics.processCpuTimeNs();
            double heapBytes = metrics.heapUsedBytes();
            int threads = metrics.threadCount();

            send(Message.of(MessageTypes.METRIC)
                .with("workerId", workerId)
                .with("cpu", String.valueOf(cpu))
                .with("memory", String.valueOf(memory))
                .with("procCpuNs", String.valueOf(procCpuNs))
                .with("heapUsed", String.valueOf(heapBytes))
                .with("threads", String.valueOf(threads))
                .with("taskType", currentTaskType)
                .with("taskId", currentTaskId)
                .with("progress", String.valueOf(progress))
                .with("credits", String.valueOf(credits.get()))
                .with("budget", String.valueOf(budget)));

            emit(new WorkerEvent.MetricSampled(cpu, memory, heapBytes / 1024.0 / 1024.0,
                threads, procCpuNs / 1_000_000.0));
            try {
                Thread.sleep(metricIntervalMs);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void handle(Message message) {
        switch (message.type()) {
            case MessageTypes.TASK         -> receiveTask(message);
            case MessageTypes.PING         -> send(Message.of(MessageTypes.PONG).with("workerId", workerId));
            case MessageTypes.NOTE         -> receiveNote(message);
            case MessageTypes.QUOTA_GRANT  -> receiveQuotaGrant(message);
            case MessageTypes.KICK -> {
                emit(new WorkerEvent.Disconnected("manager kicked: " + message.fields().getOrDefault("reason", "")));
                shutdown();
            }
            case MessageTypes.AUTH_FAILED -> {
                emit(new WorkerEvent.AuthFailed(message.fields().getOrDefault("reason", "auth failed")));
                shutdown();
            }
            default -> emit(new WorkerEvent.Raw(message));
        }
    }

    private void receiveQuotaGrant(Message message) {
        int amount = parseInt(message.fields().getOrDefault("amount", "0"));
        if (amount <= 0) return;
        int now = credits.addAndGet(amount);
        emit(new WorkerEvent.QuotaGranted(amount, now));
        emit(new WorkerEvent.QuotaChanged(now, budget));
    }

    private void receiveNote(Message message) {
        String idText = message.fields().getOrDefault("noteId", "");
        long id = -1;
        try { id = Long.parseLong(idText); } catch (NumberFormatException ignored) {}
        emit(new WorkerEvent.NoteReceived(
            id,
            message.fields().getOrDefault("from", ""),
            message.fields().getOrDefault("body", ""),
            message.fields().getOrDefault("ts", "")));
        if (id > 0) {
            send(Message.of(MessageTypes.NOTE_ACK)
                .with("workerId", workerId)
                .with("noteId", String.valueOf(id)));
        }
    }

    public void replyNote(String body) {
        if (body == null || body.isBlank()) return;
        send(Message.of(MessageTypes.NOTE)
            .with("workerId", workerId)
            .with("body", body));
    }

    private void receiveTask(Message message) {
        String taskId = message.fields().getOrDefault("taskId", UUID.randomUUID().toString());
        String taskType = message.fields().getOrDefault("taskType", "UNKNOWN");
        String payload = message.fields().getOrDefault("payload", "");
        int cost = TaskCost.costOf(taskType, payload);

        if (!taskLock.compareAndSet(false, true)) {
            send(Message.of(MessageTypes.TASK_FAILED)
                .with("workerId", workerId)
                .with("taskId", taskId)
                .with("taskType", taskType)
                .with("error", "worker is busy"));
            return;
        }

        int have = credits.get();
        if (have < cost) {
            taskLock.set(false);
            String error = String.format("QUOTA_EXCEEDED need=%d have=%d", cost, have);
            send(Message.of(MessageTypes.TASK_FAILED)
                .with("workerId", workerId)
                .with("taskId", taskId)
                .with("taskType", taskType)
                .with("error", error));
            send(Message.of(MessageTypes.QUOTA_REQUEST)
                .with("workerId", workerId)
                .with("taskId", taskId)
                .with("taskType", taskType)
                .with("payload", payload == null ? "" : payload)
                .with("requested", String.valueOf(cost))
                .with("have", String.valueOf(have)));
            emit(new WorkerEvent.QuotaExhausted(taskId, taskType, cost, have));
            return;
        }

        // Reserve credits up front; refund if the task fails outright.
        int afterReserve = credits.addAndGet(-cost);
        emit(new WorkerEvent.QuotaChanged(afterReserve, budget));

        currentTaskId = taskId;
        currentTaskType = taskType;
        currentTaskCost = cost;
        emit(new WorkerEvent.TaskStarted(currentTaskId, currentTaskType, payload));

        send(Message.of(MessageTypes.TASK_ACCEPTED)
            .with("workerId", workerId)
            .with("taskId", currentTaskId)
            .with("taskType", currentTaskType)
            .with("details", "accepted by worker, cost=" + cost));

        executor.submit(() -> executeTask(payload));
    }

    private void executeTask(String payload) {
        boolean success = false;
        String resultText = "";
        try {
            String result = WorkerTaskRunner.run(currentTaskType, payload, value -> {
                progress = value;
                emit(new WorkerEvent.TaskProgress(currentTaskId, currentTaskType, value));
                send(Message.of(MessageTypes.TASK_PROGRESS)
                    .with("workerId", workerId)
                    .with("taskId", currentTaskId)
                    .with("taskType", currentTaskType)
                    .with("progress", String.valueOf(value)));
            });
            progress = 100;
            resultText = result;
            success = true;
            send(Message.of(MessageTypes.TASK_DONE)
                .with("workerId", workerId)
                .with("taskId", currentTaskId)
                .with("taskType", currentTaskType)
                .with("result", result));
        } catch (Exception exception) {
            resultText = exception.getMessage();
            int after = credits.addAndGet(currentTaskCost);
            emit(new WorkerEvent.QuotaChanged(after, budget));
            send(Message.of(MessageTypes.TASK_FAILED)
                .with("workerId", workerId)
                .with("taskId", currentTaskId)
                .with("taskType", currentTaskType)
                .with("error", exception.getMessage()));
        } finally {
            emit(new WorkerEvent.TaskFinished(currentTaskId, currentTaskType, resultText, success));
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {}
            currentTaskId = "";
            currentTaskType = "IDLE";
            currentTaskCost = 0;
            progress = 0;
            taskLock.set(false);
        }
    }

    private synchronized void send(Message message) {
        try {
            if (writer == null) {
                return;
            }
            writer.write(MessageCodec.encode(message));
            writer.newLine();
            writer.flush();
        } catch (IOException ignored) {
        }
    }

    private static int parseInt(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return 0; }
    }
}
