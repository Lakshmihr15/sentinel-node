package com.finalproject.manager;

import com.finalproject.model.WorkerSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkerRegistryTest {

    private WorkerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WorkerRegistry();
    }

    @Test
    void snapshotForCreatesNewSnapshotOnFirstCall() {
        WorkerSnapshot snapshot = registry.snapshotFor("worker-1");
        assertNotNull(snapshot);
        assertEquals("worker-1", snapshot.workerId());
    }

    @Test
    void snapshotForReturnsSameInstanceOnSubsequentCalls() {
        WorkerSnapshot first = registry.snapshotFor("worker-1");
        WorkerSnapshot second = registry.snapshotFor("worker-1");
        assertSame(first, second);
    }

    @Test
    void registerSetsConnectedAndUsername() {
        // Use a mock-like stub session
        StubWorkerSession session = new StubWorkerSession();
        registry.register("worker-2", session, "10.0.0.1", "alice");

        WorkerSnapshot snapshot = registry.snapshotFor("worker-2");
        assertTrue(snapshot.connected());
        assertEquals("alice", snapshot.username());
        assertEquals("10.0.0.1", snapshot.host());
    }

    @Test
    void snapshotsReturnsAllRegisteredWorkers() {
        registry.snapshotFor("worker-A");
        registry.snapshotFor("worker-B");
        registry.snapshotFor("worker-C");

        List<WorkerSnapshot> snapshots = registry.snapshots();
        assertEquals(3, snapshots.size());
    }

    @Test
    void disconnectMarksWorkerOffline() {
        StubWorkerSession session = new StubWorkerSession();
        registry.register("worker-3", session, "127.0.0.1", "bob");
        assertTrue(registry.snapshotFor("worker-3").connected());

        registry.disconnect(session);
        assertFalse(registry.snapshotFor("worker-3").connected());
    }

    @Test
    void sendTaskReturnsFalseForUnknownWorker() {
        boolean result = registry.sendTask("nonexistent", "task-id",
            com.finalproject.model.TaskType.SLEEP, "2");
        assertFalse(result);
    }

    @Test
    void sendTaskReturnsFalseWhenWorkerBusy() {
        StubWorkerSession session = new StubWorkerSession();
        registry.register("worker-4", session, "127.0.0.1", "carol");

        // Mark worker as busy
        registry.snapshotFor("worker-4").updateTaskState("CALC", "old-task", 50, true);

        boolean result = registry.sendTask("worker-4", "new-task",
            com.finalproject.model.TaskType.CALC, "100");
        assertFalse(result, "sendTask should return false when worker is busy");
    }

    @Test
    void disconnectByIdMarksWorkerOffline() {
        StubWorkerSession session = new StubWorkerSession();
        registry.register("worker-5", session, "127.0.0.1", "dave");
        assertTrue(registry.snapshotFor("worker-5").connected());

        boolean ok = registry.disconnectById("worker-5");
        assertTrue(ok);
        assertFalse(registry.snapshotFor("worker-5").connected());
    }

    @Test
    void disconnectByIdReturnsFalseForUnknownWorker() {
        assertFalse(registry.disconnectById("no-such-worker"));
    }

    /**
     * Minimal stub that satisfies WorkerSession's interface requirements for tests.
     * Uses a null socket — only tests that don't invoke send() will use this.
     */
    static class StubWorkerSession extends WorkerSession {
        StubWorkerSession() {
            super(null, null, null, null, null);
        }

        @Override
        public synchronized void send(com.finalproject.net.Message message) {
            // no-op for testing — no real socket
        }

        @Override
        public void closeQuietly() {
            // no-op
        }
    }
}
