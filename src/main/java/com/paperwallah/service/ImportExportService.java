package com.paperwallah.service;
import com.paperwallah.model.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/** CSV is now an interchange format. Required header columns: marks, difficulty, topic, question, answer (id and images optional). */
public final class ImportExportService {
    private ImportExportService() {}
    public record Row(int line, int marks, Difficulty difficulty, String topic, String question, String answer, List<String> images, String error) {}
    public static String norm(String s) { return s == null ? "" : s.toLowerCase().replaceAll("\\s+", " ").trim(); }
    public static List<Row> parse(Path f) throws IOException {
        List<List<String>> recs = CsvUtil.parse(Files.readString(f, StandardCharsets.UTF_8));
        if (recs.isEmpty()) throw new IOException("The file is empty.");
        Map<String, Integer> col = new HashMap<>(); List<String> h = recs.get(0);
        for (int i = 0; i < h.size(); i++) col.put(h.get(i).trim().toLowerCase(), i);
        for (String need : List.of("marks", "difficulty", "topic", "question", "answer")) if (!col.containsKey(need)) throw new IOException("Missing required column: " + need);
        List<Row> out = new ArrayList<>();
        for (int i = 1; i < recs.size(); i++) {
            List<String> r = recs.get(i); int line = i + 1;
            try {
                int marks = Integer.parseInt(get(r, col, "marks").trim()); if (marks < 1 || marks > 6) throw new IllegalArgumentException("marks must be 1 to 6");
                Difficulty d = Difficulty.valueOf(get(r, col, "difficulty").trim().toUpperCase());
                String topic = get(r, col, "topic").trim(), q = get(r, col, "question").trim(), a = get(r, col, "answer").trim();
                if (topic.isEmpty() || q.isEmpty() || a.isEmpty()) throw new IllegalArgumentException("topic, question and answer are required");
                List<String> imgs = new ArrayList<>(); if (col.containsKey("images")) for (String p : get(r, col, "images").split("\\|")) if (!p.isBlank()) imgs.add(p.trim());
                out.add(new Row(line, marks, d, topic, q, a, imgs, null));
            } catch (RuntimeException e) { out.add(new Row(line, 0, null, "", "", "", List.of(), e instanceof NumberFormatException ? "marks is not a whole number" : e.getMessage())); }
        }
        return out;
    }
    private static String get(List<String> r, Map<String, Integer> col, String name) { int i = col.get(name); return i < r.size() ? r.get(i) : ""; }
    public static void exportQuestions(List<Question> list, Path target) throws IOException {
        StringBuilder sb = new StringBuilder("id,marks,difficulty,topic,question,answer,images\n");
        for (Question q : list) sb.append(q.getQuestionId()).append(',').append(q.getMarks()).append(',').append(q.getDifficulty()).append(',').append(CsvUtil.cell(q.getTopic())).append(',')
            .append(CsvUtil.cell(q.getQuestionContent())).append(',').append(CsvUtil.cell(q.getAnswerContent())).append(',').append(CsvUtil.cell(String.join("|", q.getImagePaths()))).append('\n');
        Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);
    }
    public static void exportTopics(List<String> topics, Path target) throws IOException { Files.write(target, topics, StandardCharsets.UTF_8); }
}
