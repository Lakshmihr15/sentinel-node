package com.finalproject.worker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

public final class WorkerTaskRunner {
    private WorkerTaskRunner() {
    }

    public static String run(String taskType, String payload, IntConsumer progress) throws Exception {
        return switch (taskType.toUpperCase(Locale.ROOT)) {
            case "CALC"   -> runCalc(payload, progress);
            case "SEARCH" -> runSearch(payload, progress);
            case "SLEEP"  -> runSleep(payload, progress);
            case "HASH"   -> runHash(payload, progress);
            case "PRINT"  -> runPrint(payload, progress);
            default        -> throw new IllegalArgumentException("Unsupported task type: " + taskType);
        };
    }

    private static String runCalc(String payload, IntConsumer progress) {
        int limit;
        try {
            limit = Math.max(1, Integer.parseInt(payload.trim()));
        } catch (Exception exception) {
            limit = 5000;
        }
        long sum = 0;
        int checkpoint = Math.max(1, limit / 20);
        for (int i = 1; i <= limit; i++) {
            sum += isPrime(i) ? i : 0;
            if (i % checkpoint == 0 || i == limit) {
                progress.accept((int) ((i * 100.0) / limit));
            }
        }
        return "sumOfPrimesUpTo=" + limit + " => " + sum;
    }

    private static String runSearch(String payload, IntConsumer progress) throws IOException {
        String query = payload == null || payload.isBlank() ? "java" : payload.trim().toLowerCase(Locale.ROOT);
        progress.accept(10);
        long matches = Files.walk(Path.of("."))
            .filter(path -> path.getFileName() != null)
            .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains(query))
            .limit(500)
            .count();
        progress.accept(100);
        return "filesMatching='" + query + "' => " + matches;
    }

    private static String runSleep(String payload, IntConsumer progress) throws InterruptedException {
        int seconds;
        try {
            seconds = Math.max(1, Integer.parseInt(payload.trim()));
        } catch (Exception exception) {
            seconds = 5;
        }
        for (int i = 1; i <= seconds; i++) {
            TimeUnit.SECONDS.sleep(1);
            progress.accept((int) ((i * 100.0) / seconds));
        }
        return "sleptForSeconds=" + seconds;
    }

    private static String runHash(String payload, IntConsumer progress) throws NoSuchAlgorithmException {
        progress.accept(20);
        byte[] input = payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashed = digest.digest(input);
        progress.accept(100);
        return "sha256(" + (input.length) + "B) => " + HexFormat.of().formatHex(hashed);
    }

    private static String runPrint(String payload, IntConsumer progress) throws InterruptedException {
        String text = payload == null ? "" : payload;
        for (int i = 1; i <= 5; i++) {
            Thread.sleep(120);
            progress.accept(i * 20);
        }
        return "echoed=\"" + text + "\"";
    }

    private static boolean isPrime(int value) {
        if (value < 2) return false;
        for (int divisor = 2; divisor * divisor <= value; divisor++) {
            if (value % divisor == 0) return false;
        }
        return true;
    }
}
