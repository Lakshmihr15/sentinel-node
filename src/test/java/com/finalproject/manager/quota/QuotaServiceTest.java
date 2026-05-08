package com.finalproject.manager.quota;

import com.finalproject.db.AppDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuotaServiceTest {
    private QuotaService service;

    @BeforeEach
    void setup() throws IOException {
        Path tempDb = Files.createTempFile("sentinel-quota-", ".db");
        tempDb.toFile().deleteOnExit();
        AppDatabase database = new AppDatabase("jdbc:sqlite:" + tempDb);
        database.initialize();
        service = new QuotaService(database);
    }

    @Test
    void recordCreatesOpenRequest() {
        long id = service.record("worker-1", "task-abc", 5, 2);
        assertTrue(id > 0);
        QuotaRequest request = service.find(id).orElseThrow();
        assertTrue(request.isOpen());
        assertEquals(5, request.requested());
        assertEquals(2, request.have());
        assertEquals("worker-1", request.workerId());
        assertEquals("task-abc", request.taskId());
    }

    @Test
    void grantClosesRequestAndStoresAmount() {
        long id = service.record("worker-2", "task-x", 4, 1);
        assertTrue(service.grant(id, 6, "manager"));
        QuotaRequest after = service.find(id).orElseThrow();
        assertFalse(after.isOpen());
        assertEquals(6, after.granted());
        assertEquals("manager", after.grantedBy());
        assertNotNull(after.grantedAt());
    }

    @Test
    void doubleGrantIsNoOp() {
        long id = service.record("worker-3", "task-y", 3, 0);
        assertTrue(service.grant(id, 3, "manager"));
        assertFalse(service.grant(id, 5, "manager"), "second grant should fail");

        QuotaRequest request = service.find(id).orElseThrow();
        assertEquals(3, request.granted());
    }

    @Test
    void openListsOnlyUngranted() {
        long openId = service.record("w1", "t1", 4, 0);
        long closedId = service.record("w2", "t2", 2, 0);
        service.grant(closedId, 2, "manager");

        List<QuotaRequest> open = service.open();
        assertEquals(1, open.size());
        assertEquals(openId, open.get(0).id());
    }

    @Test
    void recentReturnsNewestFirst() {
        long first = service.record("w1", "ta", 1, 0);
        long second = service.record("w2", "tb", 1, 0);
        long third = service.record("w3", "tc", 1, 0);

        List<QuotaRequest> recent = service.recent(10);
        assertEquals(3, recent.size());
        assertEquals(third,  recent.get(0).id());
        assertEquals(second, recent.get(1).id());
        assertEquals(first,  recent.get(2).id());
    }

    @Test
    void grantUnknownReturnsFalse() {
        assertFalse(service.grant(999_999, 5, "manager"));
    }
}
