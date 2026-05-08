package com.finalproject.notes;

import com.finalproject.db.AppDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotesServiceTest {
    private NotesService notesService;

    @BeforeEach
    void setup() throws IOException {
        Path tempDb = Files.createTempFile("sentinel-notes-", ".db");
        tempDb.toFile().deleteOnExit();
        AppDatabase database = new AppDatabase("jdbc:sqlite:" + tempDb);
        database.initialize();
        notesService = new NotesService(database);
    }

    @Test
    void blankBodyIsRejected() {
        assertEquals(-1, notesService.send("alice", null, null, ""));
        assertEquals(-1, notesService.send("alice", null, null, null));
    }

    @Test
    void directNoteIsPendingForRecipient() {
        long id = notesService.send("manager", "worker-1", null, "hello");
        assertTrue(id > 0);

        List<Note> pending = notesService.pendingFor("worker-1");
        assertEquals(1, pending.size());
        assertEquals("hello", pending.get(0).body());
        assertNull(pending.get(0).deliveredAt());

        // unrelated worker sees no pending
        assertEquals(0, notesService.pendingFor("worker-2").size());
    }

    @Test
    void broadcastNoteIsPendingForEveryone() {
        long id = notesService.send("manager", null, null, "all hands");
        assertTrue(id > 0);

        assertEquals(1, notesService.pendingFor("worker-1").size());
        assertEquals(1, notesService.pendingFor("worker-2").size());
    }

    @Test
    void deliveryRemovesFromPending() {
        long id = notesService.send("manager", "worker-1", null, "stress test");
        assertEquals(1, notesService.pendingFor("worker-1").size());

        notesService.markDelivered(id);
        assertEquals(0, notesService.pendingFor("worker-1").size());
    }

    @Test
    void ackUpdatesNote() {
        long id = notesService.send("manager", "worker-1", null, "ping");
        notesService.markDelivered(id);
        notesService.markAcked(id);

        Note note = notesService.find(id).orElseThrow();
        assertNotNull(note.deliveredAt());
        assertNotNull(note.ackAt());
        assertTrue(note.isAcked());
    }

    @Test
    void recentReturnsNewestFirst() {
        notesService.send("manager", "w1", null, "first");
        notesService.send("manager", "w1", null, "second");
        notesService.send("manager", "w1", null, "third");

        List<Note> recent = notesService.recent(10);
        assertEquals(3, recent.size());
        assertEquals("third",  recent.get(0).body());
        assertEquals("second", recent.get(1).body());
        assertEquals("first",  recent.get(2).body());
    }

    @Test
    void inboxIncludesDirectAndBroadcast() {
        notesService.send("manager", "w1", null, "direct-1");
        notesService.send("manager", null, null, "broadcast");
        notesService.send("manager", "w2", null, "other-direct");

        List<Note> inbox = notesService.inboxFor("w1", 10);
        assertEquals(2, inbox.size());
        assertTrue(inbox.stream().anyMatch(n -> n.body().equals("direct-1")));
        assertTrue(inbox.stream().anyMatch(n -> n.body().equals("broadcast")));
    }
}
