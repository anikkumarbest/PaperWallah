package com.paperwallah.service;
import com.paperwallah.config.DatabaseManager;
import com.paperwallah.dao.TopicDAO;
import java.sql.SQLException;
import java.util.*;
public class TopicService {
    private final DatabaseManager db; private final TopicDAO td;
    public TopicService(DatabaseManager db) { this.db = db; td = new TopicDAO(db); }
    public List<String> load() throws SQLException { return td.names(); }
    public void sync(List<String> topics) throws SQLException {
        db.tx(c -> {
            Set<String> have = new HashSet<>(td.names()), want = new HashSet<>(topics);
            for (String t : topics) if (!have.contains(t)) td.insert(t);
            for (String t : have) if (!want.contains(t)) td.delete(t); // fails (and rolls back) if a question still uses the topic
            return null;
        });
    }
}
