package org.openclover.core.reporters.agent;

import org.openclover.core.CloverDatabase;
import org.openclover.core.api.registry.FileInfo;
import org.openclover.core.api.registry.PackageInfo;
import org.openclover.core.api.registry.ProjectInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects whether coverage data is stale by comparing source file modification
 * times against the coverage database timestamp.
 *
 * A file is considered stale if its mtime is newer than the database file's mtime,
 * indicating the source was modified after the last instrumentation/test run.
 */
public class CoverageFreshnessDetector {

    private final CloverDatabase database;
    private final String dbPath;

    public CoverageFreshnessDetector(CloverDatabase database, String dbPath) {
        this.database = database;
        this.dbPath = dbPath;
    }

    /**
     * Check if any instrumented source file has been modified since the DB was written.
     *
     * @return true if coverage data is stale (source newer than DB)
     */
    public boolean isStale() {
        long dbTimestamp = getDbTimestamp();
        if (dbTimestamp == 0) {
            return true;
        }

        ProjectInfo model = database.getFullModel();
        if (model == null) {
            return true;
        }

        for (PackageInfo pkg : model.getAllPackages()) {
            for (FileInfo file : pkg.getFiles()) {
                File sourceFile = file.getPhysicalFile();
                if (sourceFile != null && sourceFile.exists() && sourceFile.lastModified() > dbTimestamp) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get all source files that have been modified since the DB was written.
     *
     * @return list of stale file paths (package paths)
     */
    public List<String> getStaleFiles() {
        List<String> staleFiles = new ArrayList<>();
        long dbTimestamp = getDbTimestamp();
        if (dbTimestamp == 0) {
            return staleFiles;
        }

        ProjectInfo model = database.getFullModel();
        if (model == null) {
            return staleFiles;
        }

        for (PackageInfo pkg : model.getAllPackages()) {
            for (FileInfo file : pkg.getFiles()) {
                File sourceFile = file.getPhysicalFile();
                if (sourceFile != null && sourceFile.exists() && sourceFile.lastModified() > dbTimestamp) {
                    staleFiles.add(sourceFile.getName());
                }
            }
        }
        return staleFiles;
    }

    /**
     * Get the database file's last modified timestamp.
     *
     * @return epoch millis, or 0 if DB file doesn't exist
     */
    public long getDbTimestamp() {
        File dbFile = new File(dbPath);
        return dbFile.exists() ? dbFile.lastModified() : 0;
    }
}
