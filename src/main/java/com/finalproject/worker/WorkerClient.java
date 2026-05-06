package com.finalproject.worker;

import com.finalproject.net.Message;
import com.finalproject.net.MessageCodec;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class WorkerClient implements Runnable {
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_SECONDS = 5;

    private final String workerId;
    private final String host;
    private final int port;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicBoolean taskLock = new AtomicBoolean(false);
    private final SystemMetrics metrics = new SystemMetrics();

    private volatile String currentTaskId = "";
    private volatile String currentTaskType = "IDLE";
    private volatile int progress;
    private volatile Socket socket;
    private volatile BufferedReader reader;
    private volatile BufferedWriter writer;

    public WorkerClient(String workerId, String host, int port) {
        this.workerId = workerId;
        this.host = host;
        this.port = port;
    }

    @Override
    public void run() {
        int attempts = 0;
        while (attempts <= MAX_RETRIES) {
            if (attempts > 0) {
                System.out.printf("Worker %s reconnecting (attempt %d/%d) in %ds...%n",
                    workerId, attempts, MAX_RETRIES, RETRY_DELAY_SECONDS);
                try {
                    TimeUnit.SECONDS.sleep(RETRY_DELAY_SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            try {
                runSession();
                // Clean disconnect (server shut down) — try to reconnect
                System.out.println("Worker " + workerId + " session ended, attempting to reconnect.");
            } catch (IOException exception) {
                System.err.println("Worker " + workerId + " connection error: " + exception.getMessage());
            }
            attempts++;
        }
        System.err.println("Worker " + workerId + " giving up after " + MAX_RETRIES + " retries.");
        executor.shutdownNow();
    }

    private void runSession() throws IOException {
        try (Socket connected = new Socket(host, port)) {
            this.socket = connected;
            this.reader = new BufferedReader(new InputStreamReader(connected.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new BufferedWriter(new OutputStreamWriter(connected.getOutputStream(), StandardCharsets.UTF_8));

            String username = System.getenv("WORKER_USERNAME");
            if (username == null || username.isBlank()) {
                username = System.getProperty("worker.username", "");
            }
            String token = System.getenv("WORKER_TOKEN");
            if (token == null || token.isBlank()) {
                token = System.getProperty("worker.token", "");
            }
            send(Message.of("HELLO")
                .with("workerId", workerId)
                .with("host", connected.getLocalAddress().getHostAddress())
                .with("username", username == null ? "" : username)
                .with("token", token == null ? "" : token));

            executor.submit(this::metricLoop);
            String line;
            while ((line = reader.readLine()) != null) {
                handle(MessageCodec.decode(line));
            }
        } finally {
            this.reader = null;
            this.writer = null;
        }
    }

    private void metricLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            send(Message.of("METRIC")
                .with("workerId", workerId)
                .with("cpu", String.valueOf(metrics.cpuPercent()))
                .with("memory", String.valueOf(metrics.memoryPercent()))
                .with("procCpuNs", String.valueOf(metrics.processCpuTimeNs()))
                .with("heapUsed", String.valueOf(metrics.heapUsedBytes()))
                .with("threads", String.valueOf(metrics.threadCount()))
                .with("taskType", currentTaskType)
                .with("taskId", currentTaskId)
                .with("progress", String.valueOf(progress)));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void handle(Message message) {
        switch (message.type()) {
            case "TASK" -> receiveTask(message);
            case "PING" -> send(Message.of("PONG").with("workerId", workerId));
            default -> { /* ignore unknown messages */ }
        }
    }

    private void receiveTask(Message message) {
        if (!taskLock.compareAndSet(false, true)) {
            send(Message.of("TASK_FAILED")
                .with("workerId", workerId)
                .with("taskId", message.fields().getOrDefault("taskId", UUID.randomUUID().toString()))
                .with("taskType", message.fields().getOrDefault("taskType", "UNKNOWN"))
                .with("error", "worker is busy"));
            return;
        }

        currentTaskId = message.fields().getOrDefault("taskId", UUID.randomUUID().toString());
        currentTaskType = message.fields().getOrDefault("taskType", "UNKNOWN");
        String payload = message.fields().getOrDefault("payload", "");

        send(Message.of("TASK_ACCEPTED")
            .with("workerId", workerId)
            .with("taskId", currentTaskId)
            .with("taskType", currentTaskType)
            .with("details", "accepted by worker"));

        executor.submit(() -> executeTask(payload));
    }

    private void executeTask(String payload) {
        try {
            String result = WorkerTaskRunner.run(currentTaskType, payload, value -> {
                progress = value;
                send(Message.of("TASK_PROGRESS")
                    .with("workerId", workerId)
                    .with("taskId", currentTaskId)
                    .with("taskType", currentTaskType)
                    .with("progress", String.valueOf(value)));
            });

            progress = 100;
            send(Message.of("TASK_DONE")
                .with("workerId", workerId)
                .with("taskId", currentTaskId)
                .with("taskType", currentTaskType)
                .with("result", result));
        } catch (Exception exception) {
            send(Message.of("TASK_FAILED")
                .with("workerId", workerId)
                .with("taskId", currentTaskId)
                .with("taskType", currentTaskType)
                .with("error", exception.getMessage()));
        } finally {
            currentTaskId = "";
            currentTaskType = "IDLE";
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
}