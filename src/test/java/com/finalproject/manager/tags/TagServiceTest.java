package com.finalproject.manager.tags;

import com.finalproject.db.AppDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TagServiceTest {
    private TagService service;

    @BeforeEach
    void setup() throws IOException {
        Path tempDb = Files.createTempFile("sentinel-tags-", ".db");
        tempDb.toFile().deleteOnExit();
        AppDatabase database = new AppDatabase("jdbc:sqlite:" + tempDb);
        database.initialize();
        service = new TagService(database);
    }

    @Test
    void assignAndList() {
        assertTrue(service.assign("alice", "GPU"));
        assertTrue(service.assign("alice", "east"));
        List<String> tags = service.tagsFor("alice");
        assertEquals(2, tags.size());
        assertTrue(tags.contains("gpu"), "tags are lowercased");
        assertTrue(tags.contains("east"));
    }

    @Test
    void assignIsIdempotent() {
        assertTrue(service.assign("alice", "gpu"));
        assertFalse(service.assign("alice", "gpu"));
        assertEquals(1, service.tagsFor("alice").size());
    }

    @Test
    void usersByTag() {
        service.assign("alice", "gpu");
        service.assign("bob",   "gpu");
        service.assign("carol", "cpu");

        List<String> gpu = service.usersByTag("gpu");
        assertEquals(2, gpu.size());
        assertTrue(gpu.containsAll(List.of("alice", "bob")));
    }

    @Test
    void removeWorks() {
        service.assign("alice", "gpu");
        assertTrue(service.remove("alice", "gpu"));
        assertEquals(0, service.tagsFor("alice").size());
    }

    @Test
    void allTagsReturnsDistinct() {
        service.assign("alice", "gpu");
        service.assign("bob",   "gpu");
        service.assign("carol", "cpu");
        Set<String> tags = service.allTags();
        assertEquals(2, tags.size());
        assertTrue(tags.contains("gpu"));
        assertTrue(tags.contains("cpu"));
    }

    @Test
    void rejectsBlankInput() {
        assertFalse(service.assign("", "gpu"));
        assertFalse(service.assign("alice", " "));
    }
}
