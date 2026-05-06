package com.finalproject.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkerSnapshotTest {

    @Test
    void initialStateIsDisconnectedAndIdle() {
        WorkerSnapshot snapshot = new WorkerSnapshot("worker-1");
        assertFalse(snapshot.connected());
        assertFalse(snapshot.busy());
        assertEquals("IDLE", snapshot.taskType());
        assertEquals("", snapshot.taskId());
        assertEquals(0, snapshot.progress());
        assertTrue(snapshot.history().isEmpty());
    }

    @Test
    void markConnectedSetsConnectedAndHost() {
        WorkerSnapshot snapshot = new WorkerSnapshot("worker-1");
        snapshot.markConnected("192.168.1.10");

        assertTrue(snapshot.connected());
        assertEquals("192.168.1.10", snapshot.host());
    }

    @Test
    void markDisconnectedClearsState() {
        WorkerSnapshot snapshot = new WorkerSnapshot("worker-1");
        snapshot.markConnected("10.0.0.1");
        snapshot.updateTaskState("CALC", "task-abc", 50, true);

        snapshot.markDisconnected();

        assertFalse(snapshot.connected());
        assertFalse(snapshot.busy());
        assertEquals("IDLE", snapshot.taskType());
        assertEquals("", snapshot.taskId());
        assertEquals(0, snapshot.progress());
    }

    @Test
    void applyMetricUpdatesFields() {
        WorkerSnapshot snapshot = new WorkerSnapshot("worker-1");
        snapshot.markConnected("127.0.0.1");
        snapshot.applyMetric(42.5, 67.3, 128_000_000, 8, 200.0, "CALC", "task-1", 55);

        assertEquals(42.5, snapshot.cpu(), 0.001);
        assertEquals(67.3, snapshot.memory(), 0.001);
        assertEquals(55, snapshot.progress());
        assertEquals("CALC", snapshot.taskType());
        assertTrue(snapshot.busy(), "Worker with CALC at 55% should be busy");
    }

    @Test
    void applyMetricAtProgress100NotBusy() {
        WorkerSnapshot snapshot = new WorkerSnapshot("worker-1");
        snapshot.markConnected("127.0.0.1");
        snapshot.applyMetric(10.0, 20.0, 0, 4, 0.0, "CALC", "task-1", 100);

        assertFalse(snapshot.busy(), "Progress=100 should not be busy");
    }

    @Test
    void idleTaskTypeNotBusy() {
        WorkerSnapshot snapshot = new WorkerSnapshot("worker-1");
        snapshot.markConnected("127.0.0.1");
        snapshot.applyMetric(5.0, 15.0, 0, 4, 0.0, "IDLE", "", 0);

        assertFalse(snapshot.busy(), "IDLE task type should not be busy");
    }

    @Test
    void historyCapAt60Samples() {
        WorkerSnapshot snapshot = new WorkerSnapshot("worker-1");
        snapshot.markConnected("127.0.0.1");

        // Add 80 metric samples — history should cap at 60
        for (int i = 0; i < 80; i++) {
            snapshot.applyMetric(i, i, 0, 1, 0.0, "IDLE", "", 0);
        }

        List<MetricSample> history = snapshot.history();
        assertEquals(60, history.size(), "History should be capped at 60 samples");
    }

    @Test
    void updateTaskStateChangesTaskFields() {
        WorkerSnapshot snapshot = new WorkerSnapshot("worker-1");
        snapshot.markConnected("127.0.0.1");
        snapshot.updateTaskState("SEARCH", "task-xyz", 30, true);

        assertEquals("SEARCH", snapshot.taskType());
        assertEquals("task-xyz", snapshot.taskId());
        assertEquals(30, snapshot.progress());
        assertTrue(snapshot.busy());
    }

    @Test
    void usernameDefaultsToWorkerIdWhenBlank() {
        WorkerSnapshot snapshot = new WorkerSnapshot("worker-99");
        // No username set
        assertEquals("worker-99", snapshot.username());

        snapshot.setUsername("   ");
        assertEquals("worker-99", snapshot.username());
    }

    @Test
    void usernameUsesSetValueWhenPresent() {
        WorkerSnapshot snapshot = new WorkerSnapshot("worker-99");
        snapshot.setUsername("alice");
        assertEquals("alice", snapshot.username());
    }

    @Test
    void uptimeSecondsNonNegativeAfterConnect() throws InterruptedException {
        WorkerSnapshot snapshot = new WorkerSnapshot("worker-1");
        snapshot.markConnected("127.0.0.1");
        Thread.sleep(50);
        long uptime = snapshot.uptimeSeconds();
        assertTrue(uptime >= 0, "Uptime should be non-negative");
    }
}
