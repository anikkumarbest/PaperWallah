package com.paperwallah;
import com.paperwallah.config.DatabaseManager;
import com.paperwallah.model.*;
import com.paperwallah.service.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
/** Every test uses a temporary database; the user's real data is never touched. */
class DatabaseTest {
    @TempDir Path tmp; DatabaseManager db;
    @BeforeEach void open() throws Exception { db = new DatabaseManager(tmp.resolve("t.db")); db.open(); }
    @AfterEach void close() { db.close(); }
    private Question q(int id, String text, String topic) { return new Question(id, text, "ans " + id, 3, Difficulty.MEDIUM, topic); }

    @Test void schemaCreatedAndForeignKeysOn() throws Exception {
        assertEquals(2, db.schemaVersion());
        try (Statement s = db.conn().createStatement(); ResultSet r = s.executeQuery("PRAGMA foreign_keys")) { assertEquals(1, r.getInt(1)); }
        assertThrows(SQLException.class, () -> { try (Statement s = db.conn().createStatement()) { s.executeUpdate("INSERT INTO questions(question_text,marks,difficulty,topic_id) VALUES('x',1,'EASY',999)"); } });
    }
    @Test void reopeningDoesNotReapplyMigrations() throws Exception {
        db.close(); db = new DatabaseManager(tmp.resolve("t.db")); db.open(); assertEquals(2, db.schemaVersion());
    }
    @Test void questionCrudThroughServices() throws Exception {
        TopicService ts = new TopicService(db); QuestionService qs = new QuestionService(db);
        ts.sync(new ArrayList<>(List.of("Alpha", "Beta")));
        List<Question> bank = new ArrayList<>(List.of(q(1, "one", "Alpha"), q(2, "two", "Beta")));
        qs.sync(bank); assertEquals(2, qs.loadAll().size());
        bank.get(0).setQuestionContent("one edited"); bank.remove(1); qs.sync(bank);
        List<Question> back = qs.loadAll(); assertEquals(1, back.size()); assertEquals("one edited", back.get(0).getQuestionContent()); assertEquals("Alpha", back.get(0).getTopic());
    }
    @Test void transactionRollsBack() throws Exception {
        assertThrows(SQLException.class, () -> db.tx(c -> { try (Statement s = c.createStatement()) { s.executeUpdate("INSERT INTO topics(name) VALUES('Temp')"); } throw new SQLException("boom"); }));
        assertTrue(new TopicService(db).load().isEmpty());
    }
    @Test void topicInUseCannotBeRemoved() throws Exception {
        TopicService ts = new TopicService(db); QuestionService qs = new QuestionService(db);
        ts.sync(new ArrayList<>(List.of("Alpha"))); qs.sync(new ArrayList<>(List.of(q(1, "one", "Alpha"))));
        assertThrows(SQLException.class, () -> ts.sync(new ArrayList<>()));
        assertEquals(List.of("Alpha"), ts.load());
    }
    @Test void paperSnapshotSurvivesQuestionDeletion() throws Exception {
        QuestionService qs = new QuestionService(db); PaperService ps = new PaperService(db);
        List<Question> bank = new ArrayList<>(List.of(q(1, "original text", "Alpha"))); qs.sync(bank);
        Paper p = new Paper(1, "Mid", "Java", "Marks-based", List.of(new PaperQuestion(bank.get(0), "Section A"))); p.setCollege("ABC");
        ps.sync(new ArrayList<>(List.of(p)));
        bank.clear(); qs.sync(bank);
        Paper back = ps.loadAll().get(0);
        assertEquals("original text", back.getQuestions().get(0).getQuestionContent());
        assertNull(back.getQuestions().get(0).getOriginalQuestionId());
        assertEquals("ABC", back.getCollege()); assertEquals(3, back.getTotalMarks());
    }
    @Test void legacyFilesAreMigratedAndLeftUntouched() throws Exception {
        Path legacy = Files.createDirectories(tmp.resolve("old")), data = Files.createDirectories(tmp.resolve("data"));
        String csv = "id,marks,difficulty,topic,question,answer,images\n\"5\",\"2\",\"EASY\",\"Alpha\",\"Line one\nline two, with comma\",\"A\",\"\"\n";
        Files.writeString(legacy.resolve("questions.csv"), csv); Files.writeString(legacy.resolve("topics.txt"), "Alpha\nBeta\n");
        String report = new LegacyMigrator(db, legacy, data).runIfNeeded();
        assertNotNull(report);
        List<Question> qs = new QuestionService(db).loadAll();
        assertEquals(1, qs.size()); assertEquals(5, qs.get(0).getQuestionId()); assertTrue(qs.get(0).getQuestionContent().contains("line two, with comma"));
        assertEquals(2, new TopicService(db).load().size());
        assertEquals(csv, Files.readString(legacy.resolve("questions.csv")));
        assertTrue(Files.list(data).anyMatch(f -> f.getFileName().toString().startsWith("legacy_backup_")));
        assertNull(new LegacyMigrator(db, legacy, data).runIfNeeded());
    }
    @Test void freshInstallGetsStarterData() throws Exception {
        new LegacyMigrator(db, tmp.resolve("none"), Files.createDirectories(tmp.resolve("d2"))).runIfNeeded();
        assertEquals(60, new TopicService(db).load().size()); assertTrue(new QuestionService(db).loadAll().size() > 300);
    }
    @Test void csvImportParsesValidatesAndFindsDuplicates() throws Exception {
        Path f = tmp.resolve("in.csv");
        Files.writeString(f, "marks,difficulty,topic,question,answer\n2,easy,Alpha,\"What is X?\",Y\n9,easy,Alpha,Bad marks,Y\n3,hard,Alpha,  what   is x? ,Z\n");
        List<ImportExportService.Row> rows = ImportExportService.parse(f);
        assertEquals(3, rows.size()); assertNull(rows.get(0).error()); assertNotNull(rows.get(1).error());
        assertEquals(ImportExportService.norm(rows.get(0).question()), ImportExportService.norm(rows.get(2).question()));
        Files.writeString(f, "marks,difficulty,topic,question\n1,easy,A,Q\n");
        assertThrows(java.io.IOException.class, () -> ImportExportService.parse(f));
    }
    @Test void backupCanBeOpenedAndHasSameData() throws Exception {
        new TopicService(db).sync(new ArrayList<>(List.of("Alpha")));
        Path b = tmp.resolve("backup.db"); BackupService.backup(db, b);
        try (DatabaseManager copy = new DatabaseManager(b)) { copy.open(); assertEquals(List.of("Alpha"), new TopicService(copy).load()); }
        assertThrows(SQLException.class, () -> BackupService.backup(db, b));
    }
}
