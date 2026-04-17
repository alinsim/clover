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
        private val SEARCH_PATHS = listOf(
            "target/clover",
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
        for (relativePath in SEARCH_PATHS) {
            val candidate = File(projectBaseDir, "$relativePath/$DB_FILENAME")
            if (candidate.exists() && candidate.isFile) {
                return candidate.absolutePath
            }
        }
        return null
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
                return explicitFile.absolutePath
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
