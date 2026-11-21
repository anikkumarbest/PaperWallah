package com.paperwallah.service;
import com.paperwallah.config.DatabaseManager;
import com.paperwallah.dao.*;
import com.paperwallah.model.Paper;
import java.sql.SQLException;
import java.util.*;
public class PaperService {
    private final DatabaseManager db; private final PaperDAO pd; private final QuestionDAO qd;
    public PaperService(DatabaseManager db) { this.db = db; pd = new PaperDAO(db); qd = new QuestionDAO(db); }
    public List<Paper> loadAll() throws SQLException { return pd.findAll(); }
    public void sync(List<Paper> papers) throws SQLException {
        db.tx(c -> {
            Set<Integer> valid = qd.ids(), existing = pd.ids(), keep = new HashSet<>();
            for (Paper p : papers) { pd.save(p, valid); keep.add(p.getPaperId()); }
            for (Integer id : existing) if (!keep.contains(id)) pd.delete(id);
            return null;
        });
    }
}
