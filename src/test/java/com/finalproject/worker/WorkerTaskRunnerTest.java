package com.finalproject.worker;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkerTaskRunnerTest {

    @Test
    void calcTaskProducesResult() throws Exception {
        String result = WorkerTaskRunner.run("CALC", "50", value -> {});
        assertTrue(result.contains("sumOfPrimesUpTo=50"));
    }

    @Test
    void calcTaskProgressCallbackFired() throws Exception {
        List<Integer> progressValues = new ArrayList<>();
        WorkerTaskRunner.run("CALC", "100", progressValues::add);
        assertFalse(progressValues.isEmpty(), "Progress callback should have been called");
        assertEquals(100, progressValues.get(progressValues.size() - 1),
            "Last progress value should be 100");
    }

    @Test
    void calcTaskWithDefaultPayloadOnInvalidInput() throws Exception {
        // Non-numeric payload falls back to default limit of 5000
        String result = WorkerTaskRunner.run("CALC", "notanumber", value -> {});
        assertTrue(result.contains("sumOfPrimesUpTo=5000"));
    }

    @Test
    void sleepTaskCompletesAndReturnsSeconds() throws Exception {
        long start = System.currentTimeMillis();
        String result = WorkerTaskRunner.run("SLEEP", "2", value -> {});
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(result.contains("sleptForSeconds=2"));
        assertTrue(elapsed >= 1800, "SLEEP 2 should take at least ~2s, took " + elapsed + "ms");
    }

    @Test
    void sleepTaskProgressReaches100() throws Exception {
        List<Integer> progressValues = new ArrayList<>();
        WorkerTaskRunner.run("SLEEP", "1", progressValues::add);
        assertFalse(progressValues.isEmpty());
        assertEquals(100, progressValues.get(progressValues.size() - 1));
    }

    @Test
    void searchTaskReturnsMatchCount() throws Exception {
        // Search for a pattern that should exist in this Maven project
        String result = WorkerTaskRunner.run("SEARCH", "pom", value -> {});
        assertTrue(result.startsWith("filesMatching='pom'"));
    }

    @Test
    void unknownTaskTypeThrowsException() {
        assertThrows(Exception.class, () ->
            WorkerTaskRunner.run("UNKNOWN_TASK", "", value -> {}));
    }

    @Test
    void taskTypeIsCaseInsensitive() throws Exception {
        // "calc" lowercase should work the same as "CALC"
        String result = WorkerTaskRunner.run("calc", "20", value -> {});
        assertTrue(result.contains("sumOfPrimesUpTo=20"));
    }
}