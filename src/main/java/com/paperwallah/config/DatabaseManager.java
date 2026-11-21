package com.paperwallah.config;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
/** Owns the single SQLite connection. Never recreates or overwrites a database that fails to open. */
public class DatabaseManager implements AutoCloseable {
    @FunctionalInterface public interface SqlWork<T> { T run(Connection c) throws SQLException; }
    private final Path file; private Connection conn;
    public DatabaseManager(Path file) { this.file = file; }
    /** OS-specific application data folder (not the working directory, not the JAR). */
    public static Path defaultDataDir() {
        String os = System.getProperty("os.name", "").toLowerCase(), home = System.getProperty("user.home"); Path base;
        if (os.contains("win")) { String a = System.getenv("APPDATA"); base = a != null ? Paths.get(a) : Paths.get(home, "AppData", "Roaming"); }
        else if (os.contains("mac")) base = Paths.get(home, "Library", "Application Support");
        else { String x = System.getenv("XDG_DATA_HOME"); base = x != null && !x.isBlank() ? Paths.get(x) : Paths.get(home, ".local", "share"); }
        return base.resolve("PaperWallah");
    }
    public void open() throws SQLException {
        try { Files.createDirectories(file.toAbsolutePath().getParent()); } catch (IOException e) { throw new SQLException("Cannot create data folder: " + e.getMessage(), e); }
        conn = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement s = conn.createStatement()) { s.execute("PRAGMA foreign_keys = ON"); s.execute("PRAGMA busy_timeout = 5000"); s.execute("PRAGMA journal_mode = WAL"); }
        new DatabaseMigrator(this).migrate();
    }
    public Path file() { return file; }
    public Connection conn() { return conn; }
    public int schemaVersion() throws SQLException {
        try (Statement s = conn.createStatement(); ResultSet r = s.executeQuery("SELECT COALESCE(MAX(version),0) FROM schema_version")) { return r.getInt(1); }
    }
    /** Runs the work in one transaction: commit on success, rollback on any failure. */
    public <T> T tx(SqlWork<T> w) throws SQLException {
        boolean old = conn.getAutoCommit(); conn.setAutoCommit(false);
        try { T r = w.run(conn); conn.commit(); return r; }
        catch (SQLException | RuntimeException e) { conn.rollback(); throw e; }
        finally { conn.setAutoCommit(old); }
    }
    public String setting(String key, String def) throws SQLException {
        try (PreparedStatement p = conn.prepareStatement("SELECT setting_value FROM app_settings WHERE setting_key=?")) {
            p.setString(1, key); try (ResultSet r = p.executeQuery()) { return r.next() ? r.getString(1) : def; }
        }
    }
    public void setSetting(String key, String value) throws SQLException {
        try (PreparedStatement p = conn.prepareStatement("INSERT INTO app_settings(setting_key,setting_value) VALUES(?,?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value")) {
            p.setString(1, key); p.setString(2, value); p.executeUpdate();
        }
    }
    @Override public void close() { try { if (conn != null) conn.close(); } catch (SQLException e) { System.err.println(e); } }
}
