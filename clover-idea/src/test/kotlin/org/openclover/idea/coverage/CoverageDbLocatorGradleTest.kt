package org.openclover.idea.coverage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CoverageDbLocatorGradleTest {

    @Test
    fun findDatabaseIncludesGradleReportsPath(@TempDir tempDir: File) {
        // Create database in Gradle Clover plugin location
        val gradleDir = File(tempDir, "build/reports/clover")
        gradleDir.mkdirs()
        val dbFile = File(gradleDir, "clover.db")
        dbFile.writeText("test")

        val locator = CoverageDbLocator(tempDir)
        val result = locator.findDatabase()

        assertEquals(dbFile.canonicalPath, result)
    }

    @Test
    fun gradleProjectSearchesGradlePathsFirst(@TempDir tempDir: File) {
        // Create build.gradle to indicate Gradle project
        File(tempDir, "build.gradle").writeText("// gradle project")

        // Create database in both Maven and Gradle locations
        val mavenDir = File(tempDir, "target/clover")
        mavenDir.mkdirs()
        val mavenDb = File(mavenDir, "clover.db")
        mavenDb.writeText("maven")

        val gradleDir = File(tempDir, "build/reports/clover")
        gradleDir.mkdirs()
        val gradleDb = File(gradleDir, "clover.db")
        gradleDb.writeText("gradle")

        val locator = CoverageDbLocator(tempDir)
        val result = locator.findDatabase()

        // Should find Gradle path first
        assertEquals(gradleDb.canonicalPath, result)
    }

    @Test
    fun gradleKotlinProjectSearchesGradlePathsFirst(@TempDir tempDir: File) {
        // Create build.gradle.kts to indicate Gradle Kotlin project
        File(tempDir, "build.gradle.kts").writeText("// gradle kotlin project")

        // Create database in both Maven and Gradle locations
        val mavenDir = File(tempDir, "target/clover")
        mavenDir.mkdirs()
        val mavenDb = File(mavenDir, "clover.db")
        mavenDb.writeText("maven")

        val gradleDir = File(tempDir, "build/clover")
        gradleDir.mkdirs()
        val gradleDb = File(gradleDir, "clover.db")
        gradleDb.writeText("gradle")

        val locator = CoverageDbLocator(tempDir)
        val result = locator.findDatabase()

        // Should find Gradle path first
        assertEquals(gradleDb.canonicalPath, result)
    }

    @Test
    fun mavenProjectSearchesMavenPathsFirst(@TempDir tempDir: File) {
        // Create pom.xml to indicate Maven project
        File(tempDir, "pom.xml").writeText("<project></project>")

        // Create database in both Maven and Gradle locations
        val mavenDir = File(tempDir, "target/clover")
        mavenDir.mkdirs()
        val mavenDb = File(mavenDir, "clover.db")
        mavenDb.writeText("maven")

        val gradleDir = File(tempDir, "build/clover")
        gradleDir.mkdirs()
        val gradleDb = File(gradleDir, "clover.db")
        gradleDb.writeText("gradle")

        val locator = CoverageDbLocator(tempDir)
        val result = locator.findDatabase()

        // Should find Maven path first
        assertEquals(mavenDb.canonicalPath, result)
    }
}
