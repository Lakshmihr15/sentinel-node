package com.finalproject.manager.bans;

import com.finalproject.db.AppDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BanServiceTest {
    private BanService service;

    @BeforeEach
    void setup() throws IOException {
        Path tempDb = Files.createTempFile("sentinel-bans-", ".db");
        tempDb.toFile().deleteOnExit();
        AppDatabase database = new AppDatabase("jdbc:sqlite:" + tempDb);
        database.initialize();
        service = new BanService(database);
    }

    @Test
    void banAndUnban() {
        assertFalse(service.isBanned("alice"));
        assertTrue(service.ban("alice", "manager", "rate-limit abuse"));
        assertTrue(service.isBanned("alice"));

        assertTrue(service.unban("alice"));
        assertFalse(service.isBanned("alice"));
    }

    @Test
    void banReplacesPreviousReason() {
        assertTrue(service.ban("alice", "manager", "first reason"));
        assertTrue(service.ban("alice", "manager", "second reason"));

        List<BanService.BanRecord> bans = service.list();
        assertEquals(1, bans.size());
        assertEquals("second reason", bans.get(0).reason());
    }

    @Test
    void unbanUnknownReturnsFalse() {
        assertFalse(service.unban("nobody"));
    }

    @Test
    void blankUsernameIsNotBanned() {
        assertFalse(service.isBanned(""));
        assertFalse(service.isBanned(null));
    }

    @Test
    void listIsNewestFirst() throws InterruptedException {
        service.ban("alice", "manager", "a");
        Thread.sleep(20);
        service.ban("bob",   "manager", "b");

        List<BanService.BanRecord> bans = service.list();
        assertEquals(2, bans.size());
        assertEquals("bob", bans.get(0).username());
    }
}
