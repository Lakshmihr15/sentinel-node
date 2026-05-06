package com.finalproject.repository;

import com.finalproject.db.DatabaseManager;
import com.finalproject.model.Role;
import com.finalproject.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        String sql = "SELECT id, username, role, password_hash FROM users WHERE username = ?";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);

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

    public java.util.List<User> listUsers() {
        String sql = "SELECT id, username, role, password_hash FROM users ORDER BY username";
        java.util.List<User> out = new java.util.ArrayList<>();
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
        } catch (SQLException e) {
            // return what we have
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

    public Optional<User> findByToken(String token) {
        String sql = "SELECT id, username, role, password_hash FROM users WHERE token = ?";
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
