package org.openclover.idea.coverage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class CoverageDbLocatorCanonicalPathTest {

    @Test
    fun findDatabaseReturnsCanonicalPath(@TempDir tempDir: File) {
        // Create a database file in .clover directory
        val cloverDir = File(tempDir, ".clover")
        cloverDir.mkdirs()
        val dbFile = File(cloverDir, "clover.db")
        dbFile.writeText("test")

        val locator = CoverageDbLocator(tempDir)
        val result = locator.findDatabase()

        assertNotNull(result)
        // Verify it returns the canonical path
        assertEquals(dbFile.canonicalPath, result)
    }

    @Test
    fun findDatabaseResolvesSymlinksInProjectPath(@TempDir tempDir: File) {
        // Create actual directory
        val realDir = File(tempDir, "real")
        realDir.mkdirs()
        val cloverDir = File(realDir, ".clover")
        cloverDir.mkdirs()
        val dbFile = File(cloverDir, "clover.db")
        dbFile.writeText("test")

        // Create symlink to real directory
        val symlinkDir = File(tempDir, "symlink")
        try {
            Files.createSymbolicLink(symlinkDir.toPath(), realDir.toPath())
        } catch (e: UnsupportedOperationException) {
            // Skip test if symlinks not supported on this system
            return
        }

        // Use the symlink path for the locator
        val locator = CoverageDbLocator(symlinkDir)
        val result = locator.findDatabase()

        assertNotNull(result)
        // The result should be the canonical path (with symlink resolved)
        assertEquals(dbFile.canonicalPath, result)
    }
}
