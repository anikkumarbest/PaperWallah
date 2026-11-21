package com.paperwallah.dao;
import com.paperwallah.config.DatabaseManager;
import com.paperwallah.model.Tag;
import java.sql.*;
import java.time.Instant;
import java.util.*;
public class TagDAO {
    private final DatabaseManager db;
    public TagDAO(DatabaseManager db) { this.db = db; }
    public static String nn(String s) { return s.trim().toLowerCase().replaceAll("\\s+", " "); }
    public List<Tag> all() throws SQLException {
        List<Tag> out = new ArrayList<>();
        try (Statement s = db.conn().createStatement(); ResultSet r = s.executeQuery("SELECT id,name,tag_group FROM tags ORDER BY name COLLATE NOCASE")) { while (r.next()) out.add(new Tag(r.getInt(1), r.getString(2), r.getString(3))); }
        return out;
    }
    public Integer findId(String name) throws SQLException {
        try (PreparedStatement p = db.conn().prepareStatement("SELECT id FROM tags WHERE normalized_name=?")) { p.setString(1, nn(name)); try (ResultSet r = p.executeQuery()) { return r.next() ? r.getInt(1) : null; } }
    }
    public int insert(String name, String group) throws SQLException {
        try (PreparedStatement p = db.conn().prepareStatement("INSERT INTO tags(name,normalized_name,tag_group,created_at) VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, name.trim()); p.setString(2, nn(name)); p.setString(3, group == null || group.isBlank() ? null : group.trim()); p.setString(4, Instant.now().toString()); p.executeUpdate();
            try (ResultSet k = p.getGeneratedKeys()) { k.next(); return k.getInt(1); }
        }
    }
    public void update(int id, String name, String group) throws SQLException {
        try (PreparedStatement p = db.conn().prepareStatement("UPDATE tags SET name=?,normalized_name=?,tag_group=? WHERE id=?")) { p.setString(1, name.trim()); p.setString(2, nn(name)); p.setString(3, group == null || group.isBlank() ? null : group.trim()); p.setInt(4, id); p.executeUpdate(); }
    }
    public void delete(int id) throws SQLException { exec("DELETE FROM tags WHERE id=?", id); }
    /** Moves rules and question tags from one tag to another (manual wins over auto), then deletes the old tag. Call inside a transaction. */
    public void merge(int from, int to) throws SQLException {
        exec2("UPDATE tag_rules SET tag_id=? WHERE tag_id=?", to, from);
        exec2("UPDATE question_tags SET source='MANUAL' WHERE tag_id=? AND question_id IN (SELECT question_id FROM question_tags WHERE tag_id=? AND source='MANUAL')", to, from);
        exec2("INSERT OR IGNORE INTO question_tags(question_id,tag_id,source,confidence) SELECT question_id,?,source,confidence FROM question_tags WHERE tag_id=?", to, from);
        exec2("INSERT OR IGNORE INTO question_tag_exclusions(question_id,tag_id) SELECT question_id,? FROM question_tag_exclusions WHERE tag_id=?", to, from);
        delete(from);
    }
    private void exec(String sql, int a) throws SQLException { try (PreparedStatement p = db.conn().prepareStatement(sql)) { p.setInt(1, a); p.executeUpdate(); } }
    private void exec2(String sql, int a, int b) throws SQLException { try (PreparedStatement p = db.conn().prepareStatement(sql)) { p.setInt(1, a); p.setInt(2, b); p.executeUpdate(); } }
}
