package com.finalproject.repository;

import com.finalproject.db.DatabaseManager;
import com.finalproject.model.Role;
import com.finalproject.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserRepository {
    private final DatabaseManager databaseManager;

    public UserRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public boolean createUser(String username, Role role, String passwordHash) {
        String sql = "INSERT INTO users(username, role, password_hash) VALUES(?, ?, ?)";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, role.name());
            statement.setString(3, passwordHash);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT id, username, role, password_hash, revoked_at FROM users WHERE username = ?";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                if (rs.getString("revoked_at") != null) {
                    return Optional.empty();
                }
                User user = new User(
                    rs.getLong("id"),
                    rs.getString("username"),
                    Role.valueOf(rs.getString("role")),
                    rs.getString("password_hash")
                );
                return Optional.of(user);
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public List<User> listUsers() {
        String sql = "SELECT id, username, role, password_hash FROM users WHERE revoked_at IS NULL ORDER BY username";
        List<User> out = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                out.add(new User(
                    rs.getLong("id"),
                    rs.getString("username"),
                    Role.valueOf(rs.getString("role")),
                    rs.getString("password_hash")
                ));
            }
        } catch (SQLException ignored) {
        }
        return out;
    }

    public boolean deleteByUsername(String username) {
        String sql = "DELETE FROM users WHERE username = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean revokeByUsername(String username) {
        String sql = "UPDATE users SET revoked_at = ? WHERE username = ? AND revoked_at IS NULL";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, Instant.now().toString());
            statement.setString(2, username);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean restoreByUsername(String username) {
        String sql = "UPDATE users SET revoked_at = NULL WHERE username = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean createTokenForUser(String username, String token) {
        String sql = "UPDATE users SET token = ? WHERE username = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, token);
            statement.setString(2, username);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    public Optional<String> findTokenForUser(String username) {
        String sql = "SELECT token FROM users WHERE username = ? AND revoked_at IS NULL";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String token = rs.getString("token");
                return token == null || token.isBlank() ? Optional.empty() : Optional.of(token);
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public Optional<User> findByToken(String token) {
        String sql = "SELECT id, username, role, password_hash FROM users WHERE token = ? AND revoked_at IS NULL";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, token);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                User user = new User(
                    rs.getLong("id"),
                    rs.getString("username"),
                    Role.valueOf(rs.getString("role")),
                    rs.getString("password_hash")
                );
                return Optional.of(user);
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }
}
