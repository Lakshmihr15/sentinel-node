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
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * One-shot helper: opens a socket to the manager, sends a REGISTER message
 * (username + password), waits for the reply, and returns the issued token.
 *
 * Used by the worker login dialog so an operator can self-provision a worker
 * account from the worker app without going through the manager UI.
 */
public final class WorkerRegistrar {
    public record Result(String username, String token) {}
    public static class RegisterFailedException extends Exception {
        public RegisterFailedException(String reason) { super(reason); }
    }

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 8_000;

    private WorkerRegistrar() {}

    public static Result register(String host, int port, String username, String password)
            throws IOException, RegisterFailedException {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            writer.write(MessageCodec.encode(Message.of(MessageTypes.REGISTER)
                .with("username", username)
                .with("password", password)));
            writer.newLine();
            writer.flush();

            String line;
            try {
                line = reader.readLine();
            } catch (SocketTimeoutException e) {
                throw new RegisterFailedException("manager did not reply in time");
            }
            if (line == null) {
                throw new RegisterFailedException("manager closed the connection");
            }
            Message reply = MessageCodec.decode(line);
            if (MessageTypes.REGISTER_OK.equals(reply.type())) {
                return new Result(
                    reply.fields().getOrDefault("username", username),
                    reply.fields().getOrDefault("token", ""));
            }
            if (MessageTypes.REGISTER_FAILED.equals(reply.type())) {
                throw new RegisterFailedException(
                    reply.fields().getOrDefault("reason", "unknown error"));
            }
            throw new RegisterFailedException("unexpected reply: " + reply.type());
        }
    }
}
