package com.finalproject.manager.bans;

import com.finalproject.db.AppDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BanService {
    public record BanRecord(String username, Instant bannedAt, String bannedBy, String reason) {}

    private final AppDatabase database;

    public BanService(AppDatabase database) {
        this.database = database;
    }

    public boolean ban(String username, String bannedBy, String reason) {
        if (username == null || username.isBlank()) return false;
        String sql = "INSERT OR REPLACE INTO banned_workers(username, banned_at, banned_by, reason) VALUES(?, ?, ?, ?)";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, Instant.now().toString());
            statement.setString(3, bannedBy == null ? "" : bannedBy);
            statement.setString(4, reason == null ? "" : reason);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean unban(String username) {
        String sql = "DELETE FROM banned_workers WHERE username = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean isBanned(String username) {
        if (username == null || username.isBlank()) return false;
        String sql = "SELECT 1 FROM banned_workers WHERE username = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public List<BanRecord> list() {
        String sql = "SELECT username, banned_at, banned_by, reason FROM banned_workers ORDER BY banned_at DESC";
        List<BanRecord> out = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                out.add(new BanRecord(
                    rs.getString("username"),
                    Instant.parse(rs.getString("banned_at")),
                    rs.getString("banned_by"),
                    rs.getString("reason")
                ));
            }
        } catch (SQLException ignored) {
        }
        return out;
    }
}
