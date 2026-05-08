package com.finalproject.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private final String jdbcUrl;

    public DatabaseManager(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public void initializeSchema() {
        String createUsers = """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                role TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                token TEXT DEFAULT '',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                revoked_at TEXT
            );
            """;

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(createUsers);
            try {
                statement.execute("ALTER TABLE users ADD COLUMN revoked_at TEXT");
            } catch (SQLException ignored) {
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize schema", e);
        }
    }
}
