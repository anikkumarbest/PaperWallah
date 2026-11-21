package com.paperwallah.service;
import com.paperwallah.config.DatabaseManager;
import com.paperwallah.dao.*;
import com.paperwallah.model.Question;
import java.sql.SQLException;
import java.util.*;
public class QuestionService {
    private final DatabaseManager db; private final QuestionDAO qd; private final TopicDAO td;
    public QuestionService(DatabaseManager db) { this.db = db; qd = new QuestionDAO(db); td = new TopicDAO(db); }
    public List<Question> loadAll() throws SQLException { return qd.findAll(); }
    /** Makes the database match the in-memory bank in ONE transaction (insert new, update changed, delete removed, create missing topics). */
    public void sync(List<Question> bank) throws SQLException {
        db.tx(c -> {
            Map<String, Integer> tid = td.idMap(); Set<Integer> existing = qd.ids(), keep = new HashSet<>();
            for (Question q : bank) {
                String t = q.getTopic(); Integer id = (t == null || t.isBlank()) ? null : tid.get(t);
                if (id == null && t != null && !t.isBlank()) { td.insert(t); tid = td.idMap(); id = tid.get(t); }
                qd.upsert(q, id); keep.add(q.getQuestionId());
            }
            for (Integer id : existing) if (!keep.contains(id)) qd.delete(id);
            return null;
        });
    }
}
