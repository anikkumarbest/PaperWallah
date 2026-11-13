package com.paperwallah.service;
import com.paperwallah.model.*;
import java.io.*;
import java.nio.file.*;
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
    public List<Question> loadQuestions() { return read("questions.dat"); }
    public List<Paper> loadPapers() { return read("papers.dat"); }
    public void saveQuestions(List<Question> q) throws IOException { write("questions.dat", q); }
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
