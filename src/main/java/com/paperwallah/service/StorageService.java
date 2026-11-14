package com.paperwallah.service;
import com.paperwallah.model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
/** All file I/O lives here: object serialization + image copying. */
public class StorageService {
    private final Path data = Paths.get("PaperWallah", "data"), images = Paths.get("PaperWallah", "images");
    public StorageService() { try { Files.createDirectories(data); Files.createDirectories(images); } catch (IOException e) { System.err.println(e); } }
    @SuppressWarnings("unchecked")
    private <T> List<T> read(String f) {
        File file = data.resolve(f).toFile(); if (!file.exists()) return new ArrayList<>();
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) { return (List<T>) in.readObject(); }
        catch (IOException | ClassNotFoundException | ClassCastException e) { System.err.println("Could not read " + f + ": " + e); return new ArrayList<>(); }
    }
    private void write(String f, List<?> list) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(data.resolve(f).toFile()))) { out.writeObject(new ArrayList<>(list)); }
    }
    public List<Paper> loadPapers() { return read("papers.dat"); }
    /* ---- Question bank lives in PaperWallah/data/questions.csv (seeded from the bundled initial_questions.csv on first run) ---- */
    public List<Question> loadQuestions() {
        Path f = data.resolve("questions.csv");
        try {
            if (!Files.exists(f)) try (InputStream in = StorageService.class.getResourceAsStream("/initial_questions.csv")) { if (in != null) Files.copy(in, f); }
            if (!Files.exists(f)) return new ArrayList<>();
            List<Question> out = new ArrayList<>(); boolean first = true;
            for (List<String> r : parseCsv(Files.readString(f, StandardCharsets.UTF_8))) {
                if (first) { first = false; if (r.get(0).trim().equalsIgnoreCase("id")) continue; }
                try {
                    Question q = new Question(Integer.parseInt(r.get(0).trim()), r.get(4), r.get(5), Integer.parseInt(r.get(1).trim()), Difficulty.valueOf(r.get(2).trim().toUpperCase()), r.get(3).trim());
                    if (r.size() > 6) for (String p : r.get(6).split("\\|")) if (!p.isBlank()) q.getImagePaths().add(p.trim());
                    out.add(q);
                } catch (RuntimeException e) { System.err.println("Skipped bad CSV row: " + r); }
            }
            return out;
        } catch (IOException | RuntimeException e) { System.err.println("Could not read questions.csv: " + e); return new ArrayList<>(); }
    }
    public void saveQuestions(List<Question> list) throws IOException {
        StringBuilder sb = new StringBuilder("id,marks,difficulty,topic,question,answer,images\n");
        for (Question q : list) sb.append(q.getQuestionId()).append(',').append(q.getMarks()).append(',').append(q.getDifficulty()).append(',')
            .append(cell(q.getTopic())).append(',').append(cell(q.getQuestionContent())).append(',').append(cell(q.getAnswerContent())).append(',').append(cell(String.join("|", q.getImagePaths()))).append('\n');
        Path tmp = data.resolve("questions.csv.tmp"); Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, data.resolve("questions.csv"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static String cell(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }
    private static List<List<String>> parseCsv(String t) {
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>(); StringBuilder sb = new StringBuilder(); boolean q = false, any = false;
        if (t.startsWith("\uFEFF")) t = t.substring(1);
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (q) { if (c == '"') { if (i + 1 < t.length() && t.charAt(i + 1) == '"') { sb.append('"'); i++; } else q = false; } else sb.append(c); }
            else if (c == '"') { q = true; any = true; }
            else if (c == ',') { row.add(sb.toString()); sb.setLength(0); any = true; }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < t.length() && t.charAt(i + 1) == '\n') i++;
                if (any || sb.length() > 0) { row.add(sb.toString()); rows.add(row); }
                row = new ArrayList<>(); sb.setLength(0); any = false;
            } else { sb.append(c); any = true; }
        }
        if (any || sb.length() > 0) { row.add(sb.toString()); rows.add(row); }
        return rows;
    }
    /* ---- Topic list: PaperWallah/data/topics.txt (defaults to the 60 syllabus topics) ---- */
    public List<String> loadTopics() {
        Path f = data.resolve("topics.txt");
        try { if (Files.exists(f)) { List<String> l = Files.readAllLines(f, StandardCharsets.UTF_8).stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()); if (!l.isEmpty()) return l; } }
        catch (IOException e) { System.err.println(e); }
        return new ArrayList<>(Topics.DEFAULT);
    }
    public void saveTopics(List<String> t) throws IOException { Files.write(data.resolve("topics.txt"), t, StandardCharsets.UTF_8); }
    public void savePapers(List<Paper> p) throws IOException { write("papers.dat", p); }
    private static final Set<String> EXT = Set.of("png", "jpg", "jpeg", "gif", "bmp");
    /** Copies an image into PaperWallah/images under a unique generated name, so equal file names never clash. */
    public String importImage(File src) throws IOException {
        String n = src.getName(); int dot = n.lastIndexOf('.'); String ext = dot >= 0 ? n.substring(dot + 1).toLowerCase() : "";
        if (!EXT.contains(ext)) throw new IOException("Unsupported image type. Use PNG, JPG, GIF or BMP.");
        String base = (dot > 0 ? n.substring(0, dot) : "image").replaceAll("[^A-Za-z0-9_-]", "_");
        if (base.length() > 30) base = base.substring(0, 30);
        for (int i = 0; i < 5; i++) {
            Path dest = images.resolve(base + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + ext);
            if (!Files.exists(dest)) { Files.copy(src.toPath(), dest); return dest.toString(); }
        }
        throw new IOException("Could not create a unique image name.");
    }
    public void deleteImage(String rel) { try { Files.deleteIfExists(Paths.get(rel)); } catch (IOException e) { System.err.println(e); } }
    public boolean imageExists(String rel) { return Files.exists(Paths.get(rel)); }
}
