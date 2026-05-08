package com.finalproject.manager.templates;

import com.finalproject.db.AppDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateServiceTest {
    private TemplateService service;

    @BeforeEach
    void setup() throws IOException {
        Path tempDb = Files.createTempFile("sentinel-templates-", ".db");
        tempDb.toFile().deleteOnExit();
        AppDatabase database = new AppDatabase("jdbc:sqlite:" + tempDb);
        database.initialize();
        service = new TemplateService(database);
    }

    @Test
    void createAndList() {
        assertTrue(service.create("CPU stress", "CALC", "5000000", "manager"));
        assertTrue(service.create("Sleep",      "SLEEP", "3", "manager"));

        List<TaskTemplate> all = service.list();
        assertEquals(2, all.size());
    }

    @Test
    void duplicateNameIsRejected() {
        assertTrue(service.create("Same", "CALC", "1", "manager"));
        assertFalse(service.create("Same", "SLEEP", "2", "manager"));
    }

    @Test
    void updateChangesFields() {
        assertTrue(service.create("Original", "CALC", "1", "manager"));
        TaskTemplate template = service.findByName("Original").orElseThrow();
        assertTrue(service.update(template.id(), "Renamed", "SLEEP", "5"));

        TaskTemplate updated = service.findByName("Renamed").orElseThrow();
        assertEquals("SLEEP", updated.taskType());
        assertEquals("5",     updated.payload());
        assertTrue(service.findByName("Original").isEmpty());
    }

    @Test
    void deleteRemoves() {
        assertTrue(service.create("Removable", "SLEEP", "1", "manager"));
        TaskTemplate template = service.findByName("Removable").orElseThrow();
        assertTrue(service.delete(template.id()));
        assertTrue(service.findByName("Removable").isEmpty());
    }

    @Test
    void seedDefaultsOnlyOnEmpty() {
        service.seedDefaultsIfEmpty("system");
        int afterFirst = service.list().size();
        assertTrue(afterFirst >= 3);

        service.seedDefaultsIfEmpty("system");
        assertEquals(afterFirst, service.list().size(), "seeding twice should not duplicate");
    }
}
