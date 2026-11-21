package com.paperwallah.dao;
import com.paperwallah.config.DatabaseManager;
import com.paperwallah.model.*;
import java.sql.*;
import java.util.*;
public class TagRuleDAO {
    private final DatabaseManager db;
    public TagRuleDAO(DatabaseManager db) { this.db = db; }
    public List<TagRule> all() throws SQLException {
        List<TagRule> out = new ArrayList<>();
        try (Statement s = db.conn().createStatement(); ResultSet r = s.executeQuery("SELECT id,keyword_or_phrase,normalized_phrase,tag_id,match_mode,is_enabled,priority FROM tag_rules ORDER BY id")) {
            while (r.next()) out.add(new TagRule(r.getInt(1), r.getString(2), r.getString(3), r.getInt(4), MatchMode.valueOf(r.getString(5)), r.getInt(6) == 1, r.getInt(7)));
        }
        return out;
    }
    public int insert(int tagId, String phrase, String normalized, MatchMode mode, int priority, boolean enabled) throws SQLException {
        try (PreparedStatement p = db.conn().prepareStatement("INSERT INTO tag_rules(keyword_or_phrase,normalized_phrase,tag_id,match_mode,is_enabled,priority) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, phrase); p.setString(2, normalized); p.setInt(3, tagId); p.setString(4, mode.name()); p.setInt(5, enabled ? 1 : 0); p.setInt(6, priority); p.executeUpdate();
            try (ResultSet k = p.getGeneratedKeys()) { k.next(); return k.getInt(1); }
        }
    }
    public void update(TagRule r) throws SQLException {
        try (PreparedStatement p = db.conn().prepareStatement("UPDATE tag_rules SET keyword_or_phrase=?,normalized_phrase=?,match_mode=?,is_enabled=?,priority=? WHERE id=?")) {
            p.setString(1, r.phrase()); p.setString(2, r.normalized()); p.setString(3, r.mode().name()); p.setInt(4, r.enabled() ? 1 : 0); p.setInt(5, r.priority()); p.setInt(6, r.id()); p.executeUpdate();
        }
    }
    public void delete(int id) throws SQLException { try (PreparedStatement p = db.conn().prepareStatement("DELETE FROM tag_rules WHERE id=?")) { p.setInt(1, id); p.executeUpdate(); } }
}
