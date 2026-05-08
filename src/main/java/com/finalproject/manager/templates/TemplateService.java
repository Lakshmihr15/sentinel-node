package com.finalproject.manager.templates;

import com.finalproject.db.AppDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TemplateService {
    private final AppDatabase database;

    public TemplateService(AppDatabase database) {
        this.database = database;
    }

    public List<TaskTemplate> list() {
        String sql = "SELECT id, name, task_type, payload, created_by FROM task_templates ORDER BY name";
        List<TaskTemplate> out = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                out.add(new TaskTemplate(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("task_type"),
                    rs.getString("payload"),
                    rs.getString("created_by")
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list templates", e);
        }
        return out;
    }

    public Optional<TaskTemplate> findByName(String name) {
        String sql = "SELECT id, name, task_type, payload, created_by FROM task_templates WHERE name = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new TaskTemplate(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("task_type"),
                    rs.getString("payload"),
                    rs.getString("created_by")
                ));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public boolean create(String name, String taskType, String payload, String createdBy) {
        String sql = "INSERT INTO task_templates(name, task_type, payload, created_at, created_by) VALUES(?, ?, ?, ?, ?)";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, taskType);
            statement.setString(3, payload == null ? "" : payload);
            statement.setString(4, Instant.now().toString());
            statement.setString(5, createdBy == null ? "" : createdBy);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean update(long id, String name, String taskType, String payload) {
        String sql = "UPDATE task_templates SET name = ?, task_type = ?, payload = ? WHERE id = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, taskType);
            statement.setString(3, payload == null ? "" : payload);
            statement.setLong(4, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean delete(long id) {
        String sql = "DELETE FROM task_templates WHERE id = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    public void seedDefaultsIfEmpty(String createdBy) {
        if (!list().isEmpty()) return;
        create("Stress CPU",  "CALC",   "5000000", createdBy);
        create("Quick Sleep", "SLEEP",  "3",       createdBy);
        create("File Search", "SEARCH", "java",    createdBy);
    }
}
