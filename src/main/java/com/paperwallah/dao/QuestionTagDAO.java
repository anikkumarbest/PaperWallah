package com.paperwallah.dao;
import com.paperwallah.config.DatabaseManager;
import com.paperwallah.model.QuestionTagInfo;
import java.sql.*;
import java.util.*;
/** AUTO rows are rebuilt freely; MANUAL rows and exclusions are only changed by the teacher. */
public class QuestionTagDAO {
    private final DatabaseManager db;
    public QuestionTagDAO(DatabaseManager db) { this.db = db; }
    public List<QuestionTagInfo> tagsOf(int qid) throws SQLException {
        List<QuestionTagInfo> out = new ArrayList<>();
        try (PreparedStatement p = db.conn().prepareStatement("SELECT t.id,t.name,qt.source,COALESCE(qt.confidence,1) FROM question_tags qt JOIN tags t ON t.id=qt.tag_id WHERE qt.question_id=? ORDER BY qt.source DESC, t.name COLLATE NOCASE")) {
            p.setInt(1, qid); try (ResultSet r = p.executeQuery()) { while (r.next()) out.add(new QuestionTagInfo(r.getInt(1), r.getString(2), r.getString(3), r.getDouble(4))); }
        }
        return out;
    }
    private Set<Integer> ids(String sql, int qid) throws SQLException {
        Set<Integer> out = new HashSet<>(); try (PreparedStatement p = db.conn().prepareStatement(sql)) { p.setInt(1, qid); try (ResultSet r = p.executeQuery()) { while (r.next()) out.add(r.getInt(1)); } } return out;
    }
    public Set<Integer> manualIds(int qid) throws SQLException { return ids("SELECT tag_id FROM question_tags WHERE question_id=? AND source='MANUAL'", qid); }
    public Set<Integer> excludedIds(int qid) throws SQLException { return ids("SELECT tag_id FROM question_tag_exclusions WHERE question_id=?", qid); }
    public void replaceAuto(int qid, Map<Integer, Double> want) throws SQLException {
        try (PreparedStatement d = db.conn().prepareStatement("DELETE FROM question_tags WHERE question_id=? AND source='AUTO'")) { d.setInt(1, qid); d.executeUpdate(); }
        try (PreparedStatement i = db.conn().prepareStatement("INSERT OR IGNORE INTO question_tags(question_id,tag_id,source,confidence) VALUES(?,?,'AUTO',?)")) {
            for (Map.Entry<Integer, Double> e : want.entrySet()) { i.setInt(1, qid); i.setInt(2, e.getKey()); i.setDouble(3, e.getValue()); i.addBatch(); } i.executeBatch();
        }
    }
    public void setManual(int qid, Set<Integer> tagIds) throws SQLException {
        try (PreparedStatement d = db.conn().prepareStatement("DELETE FROM question_tags WHERE question_id=? AND source='MANUAL'")) { d.setInt(1, qid); d.executeUpdate(); }
        try (PreparedStatement i = db.conn().prepareStatement("INSERT INTO question_tags(question_id,tag_id,source,confidence) VALUES(?,?,'MANUAL',1) ON CONFLICT(question_id,tag_id) DO UPDATE SET source='MANUAL',confidence=1")) {
            for (int t : tagIds) { i.setInt(1, qid); i.setInt(2, t); i.addBatch(); } i.executeBatch();
        }
    }
    public void setExcluded(int qid, Set<Integer> tagIds) throws SQLException {
        try (PreparedStatement d = db.conn().prepareStatement("DELETE FROM question_tag_exclusions WHERE question_id=?")) { d.setInt(1, qid); d.executeUpdate(); }
        try (PreparedStatement i = db.conn().prepareStatement("INSERT OR IGNORE INTO question_tag_exclusions(question_id,tag_id) VALUES(?,?)")) { for (int t : tagIds) { i.setInt(1, qid); i.setInt(2, t); i.addBatch(); } i.executeBatch(); }
    }
    public Map<Integer, Integer> questionCounts() throws SQLException {
        Map<Integer, Integer> m = new HashMap<>();
        try (Statement s = db.conn().createStatement(); ResultSet r = s.executeQuery("SELECT tag_id,COUNT(*) FROM question_tags GROUP BY tag_id")) { while (r.next()) m.put(r.getInt(1), r.getInt(2)); }
        return m;
    }
}
