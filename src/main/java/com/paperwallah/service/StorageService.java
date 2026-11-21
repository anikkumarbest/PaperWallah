package com.paperwallah.service;
import java.io.*;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;
/** Image files only. Questions, topics and papers live in SQLite. */
public class StorageService {
    private static final Set<String> EXT = Set.of("png", "jpg", "jpeg", "gif", "bmp");
    private final Path images;
    public StorageService(Path images) { this.images = images; try { Files.createDirectories(images); } catch (IOException e) { System.err.println(e); } }
    /** Copies an image into the images folder under a unique name and returns the stored path. */
    public String importImage(File src) throws IOException {
        String n = src.getName(); int dot = n.lastIndexOf('.'); String ext = dot >= 0 ? n.substring(dot + 1).toLowerCase() : "";
        if (!EXT.contains(ext)) throw new IOException("Unsupported image type. Use PNG, JPG, GIF or BMP.");
        String base = (dot > 0 ? n.substring(0, dot) : "image").replaceAll("[^A-Za-z0-9_-]", "_"); if (base.length() > 30) base = base.substring(0, 30);
        for (int i = 0; i < 5; i++) {
            Path dest = images.resolve(base + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + ext);
            if (!Files.exists(dest)) { Files.copy(src.toPath(), dest); return dest.toAbsolutePath().toString(); }
        }
        throw new IOException("Could not create a unique image name.");
    }
    public void deleteImage(String path) { try { Files.deleteIfExists(Paths.get(path)); } catch (IOException e) { System.err.println(e); } }
    public boolean imageExists(String path) { return Files.exists(Paths.get(path)); }
}
