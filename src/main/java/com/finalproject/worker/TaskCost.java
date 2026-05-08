package com.finalproject.worker;

import java.util.Locale;

/**
 * Computes how many "credits" a task costs against the worker's budget.
 * Heavier work costs more — when a worker doesn't have enough credits, it
 * rejects the task and asks the manager to top up the quota.
 */
public final class TaskCost {
    public static final int DEFAULT_BUDGET = 10;

    private TaskCost() {}

    public static int costOf(String taskType, String payload) {
        if (taskType == null) return 1;
        return switch (taskType.toUpperCase(Locale.ROOT)) {
            case "CALC"   -> calcCost(payload);
            case "SLEEP"  -> sleepCost(payload);
            case "SEARCH" -> 1;
            case "HASH"   -> 1;
            case "PRINT"  -> 1;
            default        -> 1;
        };
    }

    private static int calcCost(String payload) {
        long limit = parseLong(payload, 5_000_000L);
        long cost = Math.max(1L, limit / 1_000_000L);
        return (int) Math.min(cost, 100L);
    }

    private static int sleepCost(String payload) {
        long seconds = parseLong(payload, 1L);
        long cost = Math.max(1L, (seconds + 4) / 5);
        return (int) Math.min(cost, 100L);
    }

    private static long parseLong(String value, long fallback) {
        if (value == null) return fallback;
        try {
            return Math.max(0L, Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
