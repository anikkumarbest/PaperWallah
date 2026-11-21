package com.paperwallah.service;
import com.paperwallah.config.DatabaseManager;
import java.nio.file.*;
import java.sql.*;
/** Uses SQLite VACUUM INTO, which produces a consistent copy even while WAL mode is on. */
public final class BackupService {
    private BackupService() {}
    public static void backup(DatabaseManager db, Path target) throws SQLException {
        if (Files.exists(target)) throw new SQLException("Backup file already exists: " + target);
        try (Statement s = db.conn().createStatement()) { s.execute("VACUUM INTO '" + target.toAbsolutePath().toString().replace("'", "''") + "'"); }
    }
}
