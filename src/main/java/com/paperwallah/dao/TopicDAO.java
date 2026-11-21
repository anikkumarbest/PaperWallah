package com.paperwallah.dao;
import com.paperwallah.config.DatabaseManager;
import java.sql.*;
import java.time.Instant;
import java.util.*;
public class TopicDAO {
    private final DatabaseManager db;
    public TopicDAO(DatabaseManager db) { this.db = db; }
    public List<String> names() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement s = db.conn().createStatement(); ResultSet r = s.executeQuery("SELECT name FROM topics ORDER BY id")) { while (r.next()) out.add(r.getString(1)); }
        return out;
    }
    public Map<String, Integer> idMap() throws SQLException {
        Map<String, Integer> m = new HashMap<>();
        try (Statement s = db.conn().createStatement(); ResultSet r = s.executeQuery("SELECT id,name FROM topics")) { while (r.next()) m.put(r.getString(2), r.getInt(1)); }
        return m;
    }
    public void insert(String name) throws SQLException {
        String now = Instant.now().toString();
        try (PreparedStatement p = db.conn().prepareStatement("INSERT OR IGNORE INTO topics(name,created_at,updated_at) VALUES(?,?,?)")) { p.setString(1, name); p.setString(2, now); p.setString(3, now); p.executeUpdate(); }
    }
    public void delete(String name) throws SQLException {
        try (PreparedStatement p = db.conn().prepareStatement("DELETE FROM topics WHERE name=?")) { p.setString(1, name); p.executeUpdate(); }
    }
    public int count() throws SQLException { try (Statement s = db.conn().createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM topics")) { return r.getInt(1); } }
}
