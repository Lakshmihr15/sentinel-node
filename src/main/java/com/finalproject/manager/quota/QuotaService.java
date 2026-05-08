package com.finalproject.manager.quota;

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

public class QuotaService {
    private final AppDatabase database;

    public QuotaService(AppDatabase database) {
        this.database = database;
    }

    public long record(String workerId, String taskId, String taskType, String payload, int requested, int have) {
        String sql = "INSERT INTO quota_requests(ts, worker_id, task_id, task_type, payload, requested, have) VALUES(?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, Instant.now().toString());
            statement.setString(2, workerId);
            statement.setString(3, taskId == null ? "" : taskId);
            statement.setString(4, taskType == null ? "" : taskType);
            statement.setString(5, payload == null ? "" : payload);
            statement.setInt(6, requested);
            statement.setInt(7, have);
            if (statement.executeUpdate() != 1) return -1;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to record quota request", e);
        }
    }

    public long record(String workerId, String taskId, int requested, int have) {
        return record(workerId, taskId, null, null, requested, have);
    }

    public boolean grant(long requestId, int amount, String grantedBy) {
        String sql = "UPDATE quota_requests SET granted = ?, granted_at = ?, granted_by = ? WHERE id = ? AND granted IS NULL";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, amount);
            statement.setString(2, Instant.now().toString());
            statement.setString(3, grantedBy == null ? "" : grantedBy);
            statement.setLong(4, requestId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    public List<QuotaRequest> open() {
        return query("SELECT * FROM quota_requests WHERE granted IS NULL ORDER BY id DESC", 200);
    }

    public List<QuotaRequest> recent(int limit) {
        return query("SELECT * FROM quota_requests ORDER BY id DESC LIMIT " + Math.max(1, limit), limit);
    }

    public Optional<QuotaRequest> find(long id) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM quota_requests WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    private List<QuotaRequest> query(String sql, int limit) {
        List<QuotaRequest> out = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            int count = 0;
            while (rs.next() && count < limit) {
                out.add(map(rs));
                count++;
            }
        } catch (SQLException ignored) {
        }
        return out;
    }

    private static QuotaRequest map(ResultSet rs) throws SQLException {
        Integer granted = rs.getObject("granted") == null ? null : rs.getInt("granted");
        String grantedAtRaw = rs.getString("granted_at");
        Instant grantedAt = grantedAtRaw == null ? null : Instant.parse(grantedAtRaw);
        String taskType = safeColumn(rs, "task_type");
        String payload = safeColumn(rs, "payload");
        return new QuotaRequest(
            rs.getLong("id"),
            Instant.parse(rs.getString("ts")),
            rs.getString("worker_id"),
            rs.getString("task_id"),
            taskType,
            payload,
            rs.getInt("requested"),
            rs.getInt("have"),
            granted,
            grantedAt,
            rs.getString("granted_by")
        );
    }

    private static String safeColumn(ResultSet rs, String name) {
        try { return rs.getString(name); }
        catch (SQLException e) { return null; }
    }
}
