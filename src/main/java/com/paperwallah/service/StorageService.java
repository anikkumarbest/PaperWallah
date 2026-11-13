package com.paperwallah.service;
import com.paperwallah.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
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
    /** Copies an image into PaperWallah/images and returns the relative path to store. */
    public String importImage(File src) throws IOException {
        Path dest = images.resolve(System.currentTimeMillis() + "_" + src.getName());
        Files.copy(src.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
        return dest.toString();
    }
    public boolean imageExists(String rel) { return Files.exists(Paths.get(rel)); }
}
