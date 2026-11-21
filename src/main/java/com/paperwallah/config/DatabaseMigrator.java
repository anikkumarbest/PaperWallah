package com.paperwallah.config;
import java.sql.*;
import java.util.List;
/** Applies numbered schema migrations once each, inside a transaction. Add future changes as new list entries. */
class DatabaseMigrator {
    private static final List<List<String>> MIGRATIONS = List.of(List.of(
        "CREATE TABLE topics(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, description TEXT, created_at TEXT, updated_at TEXT)",
        "CREATE TABLE questions(id INTEGER PRIMARY KEY AUTOINCREMENT, question_text TEXT NOT NULL, answer_text TEXT, marks REAL NOT NULL, difficulty TEXT NOT NULL, topic_id INTEGER, image_path TEXT, estimated_minutes REAL, is_archived INTEGER NOT NULL DEFAULT 0, created_at TEXT, updated_at TEXT, FOREIGN KEY(topic_id) REFERENCES topics(id))",
        "CREATE TABLE papers(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, subject TEXT, college TEXT, instructions TEXT, generation_mode TEXT, duration_text TEXT, duration_minutes INTEGER, total_marks REAL, created_at TEXT, updated_at TEXT, status TEXT)",
        "CREATE TABLE paper_questions(id INTEGER PRIMARY KEY AUTOINCREMENT, paper_id INTEGER NOT NULL, question_id INTEGER, question_text_snapshot TEXT NOT NULL, answer_snapshot TEXT, marks REAL NOT NULL, difficulty_snapshot TEXT, topic_name_snapshot TEXT, images_snapshot TEXT, position INTEGER NOT NULL, section_name TEXT, FOREIGN KEY(paper_id) REFERENCES papers(id) ON DELETE CASCADE, FOREIGN KEY(question_id) REFERENCES questions(id) ON DELETE SET NULL)",
        "CREATE TABLE app_settings(setting_key TEXT PRIMARY KEY, setting_value TEXT)",
        "CREATE INDEX idx_questions_topic ON questions(topic_id)",
        "CREATE INDEX idx_questions_difficulty ON questions(difficulty)",
        "CREATE INDEX idx_pq_paper_pos ON paper_questions(paper_id, position)"),
      List.of( // version 2: tag intelligence
        "CREATE TABLE tags(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, normalized_name TEXT NOT NULL UNIQUE, tag_type TEXT NOT NULL DEFAULT 'KEYWORD', tag_group TEXT, created_at TEXT)",
        "CREATE TABLE question_tags(question_id INTEGER NOT NULL, tag_id INTEGER NOT NULL, source TEXT NOT NULL, confidence REAL, PRIMARY KEY(question_id, tag_id), FOREIGN KEY(question_id) REFERENCES questions(id) ON DELETE CASCADE, FOREIGN KEY(tag_id) REFERENCES tags(id) ON DELETE CASCADE)",
        "CREATE TABLE tag_rules(id INTEGER PRIMARY KEY AUTOINCREMENT, keyword_or_phrase TEXT NOT NULL, normalized_phrase TEXT NOT NULL UNIQUE, tag_id INTEGER NOT NULL, match_mode TEXT NOT NULL, is_enabled INTEGER NOT NULL DEFAULT 1, priority INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(tag_id) REFERENCES tags(id) ON DELETE CASCADE)",
        "CREATE TABLE question_tag_exclusions(question_id INTEGER NOT NULL, tag_id INTEGER NOT NULL, PRIMARY KEY(question_id, tag_id), FOREIGN KEY(question_id) REFERENCES questions(id) ON DELETE CASCADE, FOREIGN KEY(tag_id) REFERENCES tags(id) ON DELETE CASCADE)",
        "CREATE INDEX idx_qt_tag ON question_tags(tag_id)", "CREATE INDEX idx_qt_question ON question_tags(question_id)",
        "CREATE INDEX idx_rules_phrase ON tag_rules(normalized_phrase, is_enabled)"),
      List.of( // version 3: related-question feedback
        "CREATE TABLE related_feedback(question_id INTEGER NOT NULL, other_question_id INTEGER NOT NULL, feedback TEXT NOT NULL, created_at TEXT, PRIMARY KEY(question_id, other_question_id), FOREIGN KEY(question_id) REFERENCES questions(id) ON DELETE CASCADE, FOREIGN KEY(other_question_id) REFERENCES questions(id) ON DELETE CASCADE)"));
    private final DatabaseManager db;
    DatabaseMigrator(DatabaseManager db) { this.db = db; }
    void migrate() throws SQLException {
        try (Statement s = db.conn().createStatement()) { s.execute("CREATE TABLE IF NOT EXISTS schema_version(version INTEGER NOT NULL)"); }
        int cur = db.schemaVersion();
        if (cur > MIGRATIONS.size()) throw new SQLException("This database was created by a newer PaperWallah (schema " + cur + "). Update the application.");
        for (int v = cur; v < MIGRATIONS.size(); v++) {
            final int idx = v;
            db.tx(c -> {
                try (Statement s = c.createStatement()) { for (String sql : MIGRATIONS.get(idx)) s.execute(sql); }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO schema_version(version) VALUES(?)")) { p.setInt(1, idx + 1); p.executeUpdate(); }
                return null;
            });
        }
    }
}
