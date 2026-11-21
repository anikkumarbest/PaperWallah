package com.paperwallah.dao;
import com.paperwallah.config.DatabaseManager;
import com.paperwallah.model.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
public class QuestionDAO {
    private final DatabaseManager db;
    public QuestionDAO(DatabaseManager db) { this.db = db; }
    public List<Question> findAll() throws SQLException {
        List<Question> out = new ArrayList<>();
        try (Statement s = db.conn().createStatement(); ResultSet r = s.executeQuery(
            "SELECT q.id,q.question_text,q.answer_text,q.marks,q.difficulty,t.name,q.image_path FROM questions q LEFT JOIN topics t ON t.id=q.topic_id WHERE q.is_archived=0 ORDER BY q.id")) {
            while (r.next()) {
                Question q = new Question(r.getInt(1), r.getString(2), r.getString(3) == null ? "" : r.getString(3), (int) Math.round(r.getDouble(4)), Difficulty.valueOf(r.getString(5)), r.getString(6) == null ? "" : r.getString(6));
                String im = r.getString(7); if (im != null) for (String p : im.split("\\|")) if (!p.isBlank()) q.getImagePaths().add(p);
                out.add(q);
            }
        }
        return out;
    }
    public Set<Integer> ids() throws SQLException {
        Set<Integer> out = new HashSet<>();
        try (Statement s = db.conn().createStatement(); ResultSet r = s.executeQuery("SELECT id FROM questions WHERE is_archived=0")) { while (r.next()) out.add(r.getInt(1)); }
        return out;
    }
    /** Inserts, or updates in place (so paper links to this question id are never broken). */
    public void upsert(Question q, Integer topicId) throws SQLException {
        String now = Instant.now().toString();
        try (PreparedStatement p = db.conn().prepareStatement("INSERT INTO questions(id,question_text,answer_text,marks,difficulty,topic_id,image_path,is_archived,created_at,updated_at) VALUES(?,?,?,?,?,?,?,0,?,?) "
            + "ON CONFLICT(id) DO UPDATE SET question_text=excluded.question_text,answer_text=excluded.answer_text,marks=excluded.marks,difficulty=excluded.difficulty,topic_id=excluded.topic_id,image_path=excluded.image_path,updated_at=excluded.updated_at")) {
            p.setInt(1, q.getQuestionId()); p.setString(2, q.getQuestionContent()); p.setString(3, q.getAnswerContent()); p.setDouble(4, q.getMarks()); p.setString(5, q.getDifficulty().name());
            if (topicId == null) p.setNull(6, Types.INTEGER); else p.setInt(6, topicId);
            p.setString(7, String.join("|", q.getImagePaths())); p.setString(8, now); p.setString(9, now); p.executeUpdate();
        }
    }
    public void delete(int id) throws SQLException { try (PreparedStatement p = db.conn().prepareStatement("DELETE FROM questions WHERE id=?")) { p.setInt(1, id); p.executeUpdate(); } }
    public int count() throws SQLException { try (Statement s = db.conn().createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM questions WHERE is_archived=0")) { return r.getInt(1); } }
}
