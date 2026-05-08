package com.finalproject.manager;

import com.finalproject.net.Message;
import com.finalproject.net.MessageCodec;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class WorkerSession implements Runnable {
    private final Socket socket;
    private final ManagerController controller;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private volatile String workerId;
    private volatile String username = "";
    private volatile boolean running = true;
    private volatile long lastPongAt = System.currentTimeMillis();

    public WorkerSession(Socket socket, ManagerController controller) throws IOException {
        this.socket = socket;
        this.controller = controller;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    WorkerSession(Socket socket, ManagerController controller,
                  BufferedReader reader, BufferedWriter writer, String workerId) {
        this.socket = socket;
        this.controller = controller;
        this.reader = reader;
        this.writer = writer;
        this.workerId = workerId;
    }

    @Override
    public void run() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                controller.handleMessage(this, MessageCodec.decode(line));
            }
        } catch (IOException exception) {
            controller.handleDisconnect(this, exception.getMessage());
        } finally {
            closeQuietly();
        }
    }

    public synchronized void send(Message message) {
        try {
            writer.write(MessageCodec.encode(message));
            writer.newLine();
            writer.flush();
        } catch (IOException exception) {
            running = false;
            controller.handleDisconnect(this, exception.getMessage());
        }
    }

    public void ping() {
        send(Message.of("PING").with("workerId", workerId == null ? "" : workerId));
    }

    public void recordPong() {
        lastPongAt = System.currentTimeMillis();
    }

    public boolean isStale(long thresholdMs) {
        return System.currentTimeMillis() - lastPongAt > thresholdMs;
    }

    public synchronized void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public String workerId() {
        return workerId;
    }

    public synchronized void setUsername(String username) {
        this.username = username == null ? "" : username;
    }

    public String username() {
        return username;
    }

    public boolean matches(String id) {
        return Objects.equals(workerId, id);
    }

    public void closeQuietly() {
        running = false;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
