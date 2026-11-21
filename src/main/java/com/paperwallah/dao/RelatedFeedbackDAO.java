package com.paperwallah.dao;
import com.paperwallah.config.DatabaseManager;
import java.sql.*;
import java.time.Instant;
import java.util.*;
public class RelatedFeedbackDAO {
    private final DatabaseManager db;
    public RelatedFeedbackDAO(DatabaseManager db) { this.db = db; }
    /** other question id -> EXCLUDE or UNRELATED, in either direction. */
    public Map<Integer, String> forQuestion(int qid) throws SQLException {
        Map<Integer, String> m = new HashMap<>();
        try (PreparedStatement p = db.conn().prepareStatement("SELECT CASE WHEN question_id=? THEN other_question_id ELSE question_id END, feedback FROM related_feedback WHERE question_id=? OR other_question_id=?")) {
            p.setInt(1, qid); p.setInt(2, qid); p.setInt(3, qid); try (ResultSet r = p.executeQuery()) { while (r.next()) m.put(r.getInt(1), r.getString(2)); }
        }
        return m;
    }
    public void set(int qid, int other, String kind) throws SQLException {
        try (PreparedStatement p = db.conn().prepareStatement("INSERT INTO related_feedback(question_id,other_question_id,feedback,created_at) VALUES(?,?,?,?) ON CONFLICT(question_id,other_question_id) DO UPDATE SET feedback=excluded.feedback,created_at=excluded.created_at")) {
            p.setInt(1, qid); p.setInt(2, other); p.setString(3, kind); p.setString(4, Instant.now().toString()); p.executeUpdate();
        }
    }
    public void clear(int qid) throws SQLException { try (PreparedStatement p = db.conn().prepareStatement("DELETE FROM related_feedback WHERE question_id=? OR other_question_id=?")) { p.setInt(1, qid); p.setInt(2, qid); p.executeUpdate(); } }
}
