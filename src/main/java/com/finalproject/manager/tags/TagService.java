package com.finalproject.manager.tags;

import com.finalproject.db.AppDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class TagService {
    private final AppDatabase database;

    public TagService(AppDatabase database) {
        this.database = database;
    }

    public boolean assign(String username, String tag) {
        if (username == null || username.isBlank() || tag == null || tag.isBlank()) {
            return false;
        }
        String sql = "INSERT OR IGNORE INTO worker_tags(username, tag) VALUES(?, ?)";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, tag.trim().toLowerCase(Locale.ROOT));
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean remove(String username, String tag) {
        String sql = "DELETE FROM worker_tags WHERE username = ? AND tag = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, tag.trim().toLowerCase(Locale.ROOT));
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    public List<String> tagsFor(String username) {
        String sql = "SELECT tag FROM worker_tags WHERE username = ? ORDER BY tag";
        List<String> out = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        } catch (SQLException ignored) {
        }
        return out;
    }

    public List<String> usersByTag(String tag) {
        String sql = "SELECT username FROM worker_tags WHERE tag = ? ORDER BY username";
        List<String> out = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tag.trim().toLowerCase(Locale.ROOT));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        } catch (SQLException ignored) {
        }
        return out;
    }

    public Set<String> allTags() {
        String sql = "SELECT DISTINCT tag FROM worker_tags ORDER BY tag";
        Set<String> out = new TreeSet<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        } catch (SQLException ignored) {
        }
        return out;
    }
}
