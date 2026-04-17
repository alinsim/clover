package org.openclover.idea.build

import com.intellij.openapi.diagnostic.thisLogger
import java.io.File
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Locates the Clover runtime JAR from the plugin's own classpath.
 *
 * The runtime JAR contains `org_openclover_runtime.CoverageRecorder` which
 * instrumented code references at runtime. It must be added to the module's
 * compilation/test classpath for instrumented code to compile and run.
 *
 * In the plugin distribution, the runtime is inside the shaded `clover-*.jar`
 * which is bundled in the plugin's `lib/` directory.
 */
object CloverRuntimeLocator {

    private const val RUNTIME_CLASS_RESOURCE = "org_openclover_runtime/CoverageRecorder.class"

    /**
     * Find the JAR file containing the Clover runtime classes.
     *
     * @return absolute path to the JAR file, or null if not found
     *         (e.g., when running from exploded classes directory in tests)
     */
    fun findRuntimeJar(): String? {
        val classLoader = CloverRuntimeLocator::class.java.classLoader
        val url: URL? = classLoader.getResource(RUNTIME_CLASS_RESOURCE)
        if (url == null) {
            thisLogger().warn("Cannot find CoverageRecorder class on plugin classpath")
            return null
        }

        // URL format for JAR: jar:file:/path/to/clover.jar!/org_openclover_runtime/CoverageRecorder.class
        val path = url.path
        if (!path.startsWith("file:") || !path.contains("!")) {
            thisLogger().debug("CoverageRecorder found but not in a JAR (likely classes directory): $path")
            return null
        }

        val rawJarPath = path.substringBefore("!").removePrefix("file:")
        // URL-decode to handle spaces in paths (e.g., "Application%20Support" → "Application Support")
        val jarPath = URLDecoder.decode(rawJarPath, StandardCharsets.UTF_8)
        val file = File(jarPath)
        if (!file.exists()) {
            thisLogger().warn("Extracted JAR path does not exist: $jarPath")
            return null
        }

        return file.absolutePath
    }
}
