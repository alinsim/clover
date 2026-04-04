package org.openclover.core.reporters.agent;

import org.openclover.core.CloverDatabase;

import java.io.File;

/**
 * Provides real-time coverage queries without requiring a full report generation cycle.
 * Wraps CloverDatabase with automatic reload when the database file changes.
 *
 * Usage: create once, query repeatedly. The database is reloaded transparently
 * when the underlying file is updated (after a new test run).
 */
public class LiveCoverageDatabase {

    private final String dbPath;
    private CloverDatabase database;
    private long lastLoadedTimestamp;
    private AgentCoverageQuery query;
    private AgentJsonReporter reporter;

    public LiveCoverageDatabase(String dbPath) {
        this.dbPath = dbPath;
    }

    /**
     * Get the query API, reloading the database if it has changed.
     *
     * @return query API, or null if database doesn't exist
     */
    public AgentCoverageQuery getQuery() {
        reloadIfNeeded();
        return query;
    }

    /**
     * Get the JSON reporter, reloading the database if it has changed.
     *
     * @return reporter API, or null if database doesn't exist
     */
    public AgentJsonReporter getReporter() {
        reloadIfNeeded();
        return reporter;
    }

    /**
     * Get the underlying database instance.
     *
     * @return database, or null if not loaded
     */
    public CloverDatabase getDatabase() {
        reloadIfNeeded();
        return database;
    }

    /**
     * Force a reload of the database regardless of file timestamp.
     *
     * @return true if reload succeeded
     */
    public boolean forceReload() {
        return reload();
    }

    /**
     * Check if the database file exists and has been loaded.
     *
     * @return true if database is available for queries
     */
    public boolean isAvailable() {
        reloadIfNeeded();
        return database != null;
    }

    private void reloadIfNeeded() {
        File dbFile = new File(dbPath);
        if (!dbFile.exists()) {
            database = null;
            query = null;
            reporter = null;
            return;
        }

        long currentTimestamp = dbFile.lastModified();
        if (currentTimestamp != lastLoadedTimestamp) {
            reload();
        }
    }

    private boolean reload() {
        File dbFile = new File(dbPath);
        if (!dbFile.exists()) {
            return false;
        }

        try {
            CloverDatabase db = new CloverDatabase(dbPath);
            db.loadCoverageData();
            this.database = db;
            this.query = new AgentCoverageQuery(db);
            this.reporter = new AgentJsonReporter(db, dbPath);
            this.lastLoadedTimestamp = dbFile.lastModified();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
