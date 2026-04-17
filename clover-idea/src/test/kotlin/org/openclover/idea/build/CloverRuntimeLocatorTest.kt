package org.openclover.idea.build

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for CloverRuntimeLocator — finds the clover-runtime JAR
 * from the plugin's own classpath.
 */
class CloverRuntimeLocatorTest {

    @Test
    fun coverageRecorderClassIsLoadable() {
        val clazz = try {
            Class.forName("org_openclover_runtime.CoverageRecorder")
        } catch (e: ClassNotFoundException) {
            null
        }
        assertNotNull(clazz, "CoverageRecorder must be on the classpath")
    }

    @Test
    fun findRuntimeJarReturnsNullOrValidPath() {
        val jarPath = CloverRuntimeLocator.findRuntimeJar()
        // In unit test context, classes may be on classpath as directories (not JARs)
        // So null is acceptable. But if non-null, it must be a real .jar file.
        if (jarPath != null) {
            assertTrue(jarPath.endsWith(".jar"), "Runtime path should end in .jar: $jarPath")
            assertTrue(java.io.File(jarPath).exists(), "JAR file must exist: $jarPath")
        }
    }

    @Test
    fun findRuntimeJarIsIdempotent() {
        val first = CloverRuntimeLocator.findRuntimeJar()
        val second = CloverRuntimeLocator.findRuntimeJar()
        assertTrue(first == second, "Multiple calls must return same result")
    }
}
