package com.paperwallah.dao;
import com.paperwallah.config.DatabaseManager;
import com.paperwallah.model.*;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
/** Papers and their paper_questions rows. Every paper question keeps its own text/answer snapshot. */
public class PaperDAO {
    private final DatabaseManager db;
    public PaperDAO(DatabaseManager db) { this.db = db; }
    public List<Paper> findAll() throws SQLException {
        List<Object[]> heads = new ArrayList<>();
        try (Statement s = db.conn().createStatement(); ResultSet r = s.executeQuery("SELECT id,title,subject,college,instructions,generation_mode,duration_text,created_at FROM papers ORDER BY id")) {
            while (r.next()) heads.add(new Object[]{r.getInt(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6), r.getString(7), r.getString(8)});
        }
        List<Paper> out = new ArrayList<>();
        try (PreparedStatement p = db.conn().prepareStatement("SELECT question_id,question_text_snapshot,answer_snapshot,marks,difficulty_snapshot,topic_name_snapshot,images_snapshot,section_name FROM paper_questions WHERE paper_id=? ORDER BY position")) {
            for (Object[] h : heads) {
                p.setInt(1, (Integer) h[0]); List<PaperQuestion> qs = new ArrayList<>();
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        PaperQuestion q = new PaperQuestion(r.getString(2), nz(r.getString(3)), (int) Math.round(r.getDouble(4)), nz(r.getString(6)),
                            r.getString(5) == null ? Difficulty.MEDIUM : Difficulty.valueOf(r.getString(5)), r.getString(8) == null ? "Section A" : r.getString(8));
                        int oid = r.getInt(1); if (!r.wasNull()) q.setOriginalQuestionId(oid);
                        String im = r.getString(7); if (im != null) for (String x : im.split("\\|")) if (!x.isBlank()) q.getImagePaths().add(x);
                        qs.add(q);
                    }
                }
                Paper pp = new Paper((Integer) h[0], nz((String) h[1]), nz((String) h[2]), nz((String) h[5]), qs);
                pp.setCollege(nz((String) h[3])); pp.setInstructions(nz((String) h[4])); pp.setDuration(nz((String) h[6]));
                try { pp.setCreationDate(LocalDate.parse(((String) h[7]).substring(0, 10))); } catch (RuntimeException e) { /* keep default */ }
                out.add(pp);
            }
        }
        return out;
    }
    private static String nz(String s) { return s == null ? "" : s; }
    public Set<Integer> ids() throws SQLException {
        Set<Integer> out = new HashSet<>();
        try (Statement s = db.conn().createStatement(); ResultSet r = s.executeQuery("SELECT id FROM papers")) { while (r.next()) out.add(r.getInt(1)); }
        return out;
    }
    /** Call inside a transaction. validQuestionIds protects the foreign key when an original question no longer exists. */
    public void save(Paper pp, Set<Integer> validQuestionIds) throws SQLException {
        Connection c = db.conn(); String now = Instant.now().toString();
        try (PreparedStatement p = c.prepareStatement("INSERT INTO papers(id,title,subject,college,instructions,generation_mode,duration_text,total_marks,created_at,updated_at,status) VALUES(?,?,?,?,?,?,?,?,?,?,'SAVED') "
            + "ON CONFLICT(id) DO UPDATE SET title=excluded.title,subject=excluded.subject,college=excluded.college,instructions=excluded.instructions,generation_mode=excluded.generation_mode,duration_text=excluded.duration_text,total_marks=excluded.total_marks,updated_at=excluded.updated_at")) {
            p.setInt(1, pp.getPaperId()); p.setString(2, pp.getTitle()); p.setString(3, pp.getSubject()); p.setString(4, pp.getCollege()); p.setString(5, pp.getInstructions());
            p.setString(6, pp.getGenerationMode()); p.setString(7, pp.getDuration()); p.setDouble(8, pp.getTotalMarks()); p.setString(9, pp.getCreationDate().toString()); p.setString(10, now); p.executeUpdate();
        }
        try (PreparedStatement d = c.prepareStatement("DELETE FROM paper_questions WHERE paper_id=?")) { d.setInt(1, pp.getPaperId()); d.executeUpdate(); }
        try (PreparedStatement ins = c.prepareStatement("INSERT INTO paper_questions(paper_id,question_id,question_text_snapshot,answer_snapshot,marks,difficulty_snapshot,topic_name_snapshot,images_snapshot,position,section_name) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            int pos = 0;
            for (PaperQuestion q : pp.getQuestions()) {
                ins.setInt(1, pp.getPaperId()); Integer oid = q.getOriginalQuestionId();
                if (oid != null && validQuestionIds.contains(oid)) ins.setInt(2, oid); else ins.setNull(2, Types.INTEGER);
                ins.setString(3, q.getQuestionContent()); ins.setString(4, q.getAnswerContent()); ins.setDouble(5, q.getMarks()); ins.setString(6, q.getDifficulty().name());
                ins.setString(7, q.getTopic()); ins.setString(8, String.join("|", q.getImagePaths())); ins.setInt(9, pos++); ins.setString(10, q.getSectionName()); ins.addBatch();
            }
            ins.executeBatch();
        }
    }
    public void delete(int id) throws SQLException { try (PreparedStatement p = db.conn().prepareStatement("DELETE FROM papers WHERE id=?")) { p.setInt(1, id); p.executeUpdate(); } }
    public int count() throws SQLException { try (Statement s = db.conn().createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM papers")) { return r.getInt(1); } }
}
