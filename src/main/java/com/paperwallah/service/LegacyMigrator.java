package com.paperwallah.service;
import com.paperwallah.config.DatabaseManager;
import com.paperwallah.dao.*;
import com.paperwallah.model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
/** One-time import of the old questions.csv / topics.txt / papers.dat into SQLite. Old files are copied to a backup and never changed or deleted. */
public class LegacyMigrator {
    public static final String KEY = "legacy_migration_done";
    private final DatabaseManager db; private final Path legacyDir, dataDir;
    public LegacyMigrator(DatabaseManager db, Path legacyDir, Path dataDir) { this.db = db; this.legacyDir = legacyDir; this.dataDir = dataDir; }

    /** Returns the migration report, or null if migration was already done earlier. */
    public String runIfNeeded() throws Exception {
        if (db.setting(KEY, null) != null) return null;
        Path csv = legacyDir.resolve("questions.csv"), txt = legacyDir.resolve("topics.txt"), dat = legacyDir.resolve("papers.dat");
        boolean legacy = Files.exists(csv) || Files.exists(txt) || Files.exists(dat);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        StringBuilder rep = new StringBuilder("PaperWallah data migration report (" + stamp + ")\n\n"); List<String> skipped = new ArrayList<>();
        if (legacy) {
            Path bk = dataDir.resolve("legacy_backup_" + stamp); Files.createDirectories(bk);
            for (Path f : List.of(csv, txt, dat)) if (Files.exists(f)) Files.copy(f, bk.resolve(f.getFileName()));
            rep.append("Old files backed up to: ").append(bk).append("\nThe original files were NOT changed or deleted. Delete them yourself once you are happy with the new database.\n\n");
        } else rep.append("No old files found. Started with the built-in starter topics and questions.\n\n");
        // read everything first, so a file problem never leaves a half-written database
        List<String> topicNames = Files.exists(txt) ? new ArrayList<>(Files.readAllLines(txt, StandardCharsets.UTF_8).stream().map(String::trim).filter(s -> !s.isEmpty()).toList()) : new ArrayList<>(Topics.DEFAULT);
        String csvText = null;
        if (Files.exists(csv)) csvText = Files.readString(csv, StandardCharsets.UTF_8);
        else if (!legacy) try (InputStream in = LegacyMigrator.class.getResourceAsStream("/initial_questions.csv")) { if (in != null) csvText = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        List<Question> qs = csvText == null ? new ArrayList<>() : parseQuestions(csvText, skipped);
        List<Paper> papers = new ArrayList<>();
        if (Files.exists(dat)) {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(dat.toFile()))) {
                for (Object o : (List<?>) in.readObject()) papers.add((Paper) o);
            } catch (Exception e) { skipped.add("papers.dat could not be read (" + e + "). The file was left untouched; keep it and ask for help recovering it."); }
        }
        int missingImg = 0, linked = 0;
        for (Question q : qs) for (String p : q.getImagePaths()) { if (Files.exists(Paths.get(p))) linked++; else missingImg++; }
        final String reportSoFar = rep.toString();
        db.tx(c -> {
            TopicDAO td = new TopicDAO(db); QuestionDAO qd = new QuestionDAO(db); PaperDAO pd = new PaperDAO(db);
            for (String t : topicNames) td.insert(t);
            Map<String, Integer> ids = td.idMap();
            for (Question q : qs) {
                Integer id = ids.get(q.getTopic());
                if (id == null && !q.getTopic().isBlank()) { td.insert(q.getTopic()); ids = td.idMap(); id = ids.get(q.getTopic()); }
                qd.upsert(q, id);
            }
            Set<Integer> valid = qd.ids();
            for (Paper p : papers) pd.save(p, valid);
            db.setSetting(KEY, stamp); db.setSetting("migration_report", reportSoFar);
            return null;
        });
        rep.append("Topics imported: ").append(new TopicDAO(db).count()).append("\nQuestions imported: ").append(qs.size()).append("\nPapers imported: ").append(papers.size())
           .append("\nImage references found on disk: ").append(linked).append("\nImage references pointing to missing files: ").append(missingImg).append("\n");
        rep.append("Verified in database: ").append(new TopicDAO(db).count()).append(" topics, ").append(new QuestionDAO(db).count()).append(" questions, ").append(new PaperDAO(db).count()).append(" papers.\n");
        if (!skipped.isEmpty()) { rep.append("\nSkipped or problem records (").append(skipped.size()).append("):\n"); skipped.forEach(s -> rep.append(" - ").append(s).append("\n")); }
        String report = rep.toString(); db.setSetting("migration_report", report);
        Files.writeString(dataDir.resolve("migration_report.txt"), report, StandardCharsets.UTF_8);
        return report;
    }

    private List<Question> parseQuestions(String text, List<String> skipped) {
        List<Question> out = new ArrayList<>(); Set<Integer> seen = new HashSet<>(); int row = 0; boolean first = true; int max = 0;
        List<Object[]> raw = new ArrayList<>();
        for (List<String> r : CsvUtil.parse(text)) {
            row++; if (first) { first = false; if (r.get(0).trim().equalsIgnoreCase("id")) continue; }
            try { raw.add(new Object[]{Integer.parseInt(r.get(0).trim()), r}); max = Math.max(max, Integer.parseInt(r.get(0).trim())); }
            catch (RuntimeException e) { skipped.add("questions.csv row " + row + ": unreadable id"); }
        }
        for (Object[] o : raw) {
            @SuppressWarnings("unchecked") List<String> r = (List<String>) o[1]; int id = (Integer) o[0];
            try {
                if (!seen.add(id)) { id = ++max; skipped.add("duplicate id " + o[0] + " was given the new id " + id); }
                Question q = new Question(id, r.get(4), r.get(5), Integer.parseInt(r.get(1).trim()), Difficulty.valueOf(r.get(2).trim().toUpperCase()), r.get(3).trim());
                if (r.size() > 6) for (String p : r.get(6).split("\\|")) if (!p.isBlank()) q.getImagePaths().add(p.trim());
                out.add(q);
            } catch (RuntimeException e) { skipped.add("question id " + o[0] + " skipped: " + e.getMessage()); }
        }
        return out;
    }
}
