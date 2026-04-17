package org.openclover.idea.coverage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * TDD tests for CoverageDbLocator.
 * Verifies automatic detection of Clover coverage database files.
 */
class CoverageDbLocatorTest {

    @TempDir
    lateinit var projectDir: Path

    private lateinit var locator: CoverageDbLocator

    @BeforeEach
    fun setUp() {
        locator = CoverageDbLocator(projectDir.toFile())
    }

    @Test
    fun returnsNullWhenNoDbExists() {
        val result = locator.findDatabase()
        assertNull(result, "Should return null when no .db file exists")
    }

    @Test
    fun findsMavenTargetCloverDb() {
        val dbFile = projectDir.resolve("target/clover/clover.db").toFile()
        dbFile.parentFile.mkdirs()
        dbFile.createNewFile()

        val result = locator.findDatabase()
        assertNotNull(result, "Should find target/clover/clover.db")
        assertEquals(dbFile.absolutePath, result)
    }

    @Test
    fun findsGradleBuildCloverDb() {
        val dbFile = projectDir.resolve("build/clover/clover.db").toFile()
        dbFile.parentFile.mkdirs()
        dbFile.createNewFile()

        val result = locator.findDatabase()
        assertNotNull(result, "Should find build/clover/clover.db")
        assertEquals(dbFile.absolutePath, result)
    }

    @Test
    fun findsDotCloverDb() {
        val dbFile = projectDir.resolve(".clover/clover.db").toFile()
        dbFile.parentFile.mkdirs()
        dbFile.createNewFile()

        val result = locator.findDatabase()
        assertNotNull(result, "Should find .clover/clover.db")
        assertEquals(dbFile.absolutePath, result)
    }

    @Test
    fun prefersMavenOverGradle() {
        val mavenDb = projectDir.resolve("target/clover/clover.db").toFile()
        mavenDb.parentFile.mkdirs()
        mavenDb.createNewFile()

        val gradleDb = projectDir.resolve("build/clover/clover.db").toFile()
        gradleDb.parentFile.mkdirs()
        gradleDb.createNewFile()

        val result = locator.findDatabase()
        assertEquals(mavenDb.absolutePath, result, "Should prefer Maven over Gradle")
    }

    @Test
    fun respectsExplicitInitString() {
        val explicitDb = projectDir.resolve("custom/my-coverage.db").toFile()
        explicitDb.parentFile.mkdirs()
        explicitDb.createNewFile()

        val result = locator.resolveInitString(explicitDb.absolutePath)
        assertEquals(explicitDb.absolutePath, result, "Should use explicit path when provided")
    }

    @Test
    fun explicitInitStringFallsBackToAutoDetectWhenFileMissing() {
        val mavenDb = projectDir.resolve("target/clover/clover.db").toFile()
        mavenDb.parentFile.mkdirs()
        mavenDb.createNewFile()

        val result = locator.resolveInitString("/nonexistent/path/clover.db")
        assertEquals(mavenDb.absolutePath, result, "Should fall back to auto-detect when explicit path is missing")
    }

    @Test
    fun blankInitStringTriggersAutoDetect() {
        val mavenDb = projectDir.resolve("target/clover/clover.db").toFile()
        mavenDb.parentFile.mkdirs()
        mavenDb.createNewFile()

        val result = locator.resolveInitString("")
        assertEquals(mavenDb.absolutePath, result, "Blank init string should trigger auto-detect")
    }

    @Test
    fun blankInitStringReturnsNullWhenNoDbFound() {
        val result = locator.resolveInitString("")
        assertNull(result, "Blank init string with no DB should return null")
    }
}
