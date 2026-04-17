package org.openclover.idea.coverage

import com.intellij.openapi.diagnostic.thisLogger
import java.io.File

/**
 * Locates the Clover coverage database file in standard project locations.
 *
 * Search order (first match wins):
 * 1. target/clover/clover.db (Maven default)
 * 2. build/clover/clover.db (Gradle default)
 * 3. .clover/clover.db (manual/CLI default)
 *
 * This eliminates the need for users to manually configure the init string
 * for standard Maven/Gradle project layouts.
 */
class CoverageDbLocator(private val projectBaseDir: File) {

    companion object {
        private const val DB_FILENAME = "clover.db"
        private val MAVEN_SEARCH_PATHS = listOf(
            "target/clover",
            ".clover",
        )
        private val GRADLE_SEARCH_PATHS = listOf(
            "build/reports/clover",
            "build/clover",
            ".clover",
        )
    }

    /**
     * Search standard locations for a coverage database file.
     *
     * @return absolute path to the database file, or null if not found
     */
    fun findDatabase(): String? {
        val searchPaths = getSearchPaths()
        for (relativePath in searchPaths) {
            val candidate = File(projectBaseDir, "$relativePath/$DB_FILENAME")
            if (candidate.exists() && candidate.isFile) {
                return candidate.canonicalPath
            }
        }
        return null
    }

    /**
     * Determine search paths based on project type.
     * Gradle projects search Gradle-specific paths first, Maven projects search Maven paths first.
     */
    private fun getSearchPaths(): List<String> {
        return when {
            isGradleProject() -> GRADLE_SEARCH_PATHS
            isMavenProject() -> MAVEN_SEARCH_PATHS
            else -> GRADLE_SEARCH_PATHS + MAVEN_SEARCH_PATHS
        }
    }

    private fun isGradleProject(): Boolean {
        return File(projectBaseDir, "build.gradle").exists() ||
            File(projectBaseDir, "build.gradle.kts").exists()
    }

    private fun isMavenProject(): Boolean {
        return File(projectBaseDir, "pom.xml").exists()
    }

    /**
     * Resolve the init string: use explicit path if valid, otherwise auto-detect.
     *
     * @param explicitInitString the user-configured init string (may be blank)
     * @return resolved absolute path, or null if no database can be found
     */
    fun resolveInitString(explicitInitString: String): String? {
        // If explicit path is provided and exists, use it
        if (explicitInitString.isNotBlank()) {
            val explicitFile = File(explicitInitString)
            if (explicitFile.exists() && explicitFile.isFile) {
                return explicitFile.canonicalPath
            }
            // Explicit path was configured but file is missing (e.g., after mvn clean)
            thisLogger().warn(
                "Configured coverage database not found: $explicitInitString — falling back to auto-detection"
            )
        }

        // Fall back to auto-detection
        return findDatabase()
    }
}
