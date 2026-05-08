package com.finalproject.notes;

import com.finalproject.db.AppDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NotesService {
    private final AppDatabase database;

    public NotesService(AppDatabase database) {
        this.database = database;
    }

    public long send(String senderUsername, String recipientWorkerId, String recipientTag, String body) {
        if (body == null || body.isBlank()) {
            return -1;
        }
        String sql = "INSERT INTO notes(ts, sender_username, recipient_worker_id, recipient_tag, body) VALUES(?, ?, ?, ?, ?)";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, Instant.now().toString());
            statement.setString(2, senderUsername == null ? "" : senderUsername);
            statement.setString(3, blankToNull(recipientWorkerId));
            statement.setString(4, blankToNull(recipientTag));
            statement.setString(5, body);
            int rows = statement.executeUpdate();
            if (rows != 1) return -1;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert note", e);
        }
    }

    public List<Note> pendingFor(String workerId) {
        String sql = """
            SELECT id, ts, sender_username, recipient_worker_id, recipient_tag, body, delivered_at, ack_at
              FROM notes
             WHERE delivered_at IS NULL
               AND (recipient_worker_id = ? OR recipient_worker_id IS NULL)
             ORDER BY id ASC
            """;
        return query(sql, workerId);
    }

    public List<Note> recent(int limit) {
        String sql = """
            SELECT id, ts, sender_username, recipient_worker_id, recipient_tag, body, delivered_at, ack_at
              FROM notes
             ORDER BY id DESC
             LIMIT ?
            """;
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                List<Note> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(rowToNote(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load notes", e);
        }
    }

    public List<Note> inboxFor(String workerId, int limit) {
        String sql = """
            SELECT id, ts, sender_username, recipient_worker_id, recipient_tag, body, delivered_at, ack_at
              FROM notes
             WHERE recipient_worker_id = ? OR recipient_worker_id IS NULL
             ORDER BY id DESC
             LIMIT ?
            """;
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, workerId);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                List<Note> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(rowToNote(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load inbox", e);
        }
    }

    public void markDelivered(long noteId) {
        update("UPDATE notes SET delivered_at = ? WHERE id = ? AND delivered_at IS NULL",
            Instant.now().toString(), noteId);
    }

    public void markAcked(long noteId) {
        update("UPDATE notes SET ack_at = ? WHERE id = ? AND ack_at IS NULL",
            Instant.now().toString(), noteId);
    }

    public Optional<Note> find(long noteId) {
        String sql = """
            SELECT id, ts, sender_username, recipient_worker_id, recipient_tag, body, delivered_at, ack_at
              FROM notes WHERE id = ?
            """;
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, noteId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(rowToNote(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find note", e);
        }
    }

    private List<Note> query(String sql, String workerId) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, workerId);
            try (ResultSet rs = statement.executeQuery()) {
                List<Note> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(rowToNote(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query notes", e);
        }
    }

    private void update(String sql, Object... values) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update note", e);
        }
    }

    private static Note rowToNote(ResultSet rs) throws SQLException {
        return new Note(
            rs.getLong("id"),
            Instant.parse(rs.getString("ts")),
            rs.getString("sender_username"),
            rs.getString("recipient_worker_id"),
            rs.getString("recipient_tag"),
            rs.getString("body"),
            parseInstant(rs.getString("delivered_at")),
            parseInstant(rs.getString("ack_at"))
        );
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
