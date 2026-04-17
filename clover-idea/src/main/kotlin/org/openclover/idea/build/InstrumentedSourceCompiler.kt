package org.openclover.idea.build

import com.intellij.openapi.diagnostic.thisLogger
import java.io.File

/**
 * Compiles instrumented Java source files by invoking javac as an external process.
 *
 * Uses the JDK's javac binary directly rather than javax.tools.JavaCompiler,
 * because the IntelliJ runtime (JBR) doesn't include the compiler API.
 * The javac path is resolved from JAVA_HOME or the configured project SDK.
 */
object InstrumentedSourceCompiler {

    /**
     * Compile all `.java` files under [sourceDir] into [outputDir].
     *
     * @param sourceDir directory containing instrumented `.java` files
     * @param outputDir directory to write `.class` files into
     * @param classpath list of directories/JARs for compilation dependencies
     * @param javacPath path to javac binary (null = auto-detect from JAVA_HOME)
     * @return compilation result with success flag, error messages, and file count
     */
    fun compile(
        sourceDir: File,
        outputDir: File,
        classpath: List<File>,
        javacPath: String? = null,
    ): CompilationResult {
        // Collect all .java files
        val sourceFiles = sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .toList()

        if (sourceFiles.isEmpty()) {
            return CompilationResult(success = true, errors = emptyList(), compiledCount = 0)
        }

        // Resolve javac
        val javac = resolveJavac(javacPath)
            ?: return CompilationResult(
                success = false,
                errors = listOf("Cannot find javac. Set JAVA_HOME to a JDK installation."),
                compiledCount = 0,
            )

        outputDir.mkdirs()

        // Build classpath string
        val classpathString = classpath
            .filter { it.exists() }
            .joinToString(File.pathSeparator) { it.absolutePath }

        // Write file list to @argfile (avoids command line length limits)
        val argFile = File(outputDir.parentFile, "javac-sources.txt")
        argFile.writeText(sourceFiles.joinToString("\n") { it.absolutePath })

        // Build javac command
        val command = mutableListOf(javac)
        command.addAll(listOf("-d", outputDir.absolutePath))
        command.addAll(listOf("-sourcepath", sourceDir.absolutePath))
        if (classpathString.isNotBlank()) {
            command.addAll(listOf("-classpath", classpathString))
        }
        command.add("-nowarn")
        command.add("@${argFile.absolutePath}")

        thisLogger().info("Compiling ${sourceFiles.size} instrumented files with: $javac")

        val process = ProcessBuilder(command)
            .directory(sourceDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        argFile.delete()

        val errors = if (exitCode != 0) {
            output.lines().filter { it.contains("error:") || it.contains("Error:") }
        } else {
            emptyList()
        }

        if (exitCode != 0) {
            thisLogger().warn("javac failed (exit $exitCode): ${errors.take(5).joinToString("; ")}")
        } else {
            thisLogger().info("Compiled ${sourceFiles.size} instrumented files successfully")
        }

        return CompilationResult(
            success = exitCode == 0,
            errors = errors,
            compiledCount = sourceFiles.size,
        )
    }

    private fun resolveJavac(explicitPath: String?): String? {
        // 1. Explicit path
        if (explicitPath != null) {
            val file = File(explicitPath)
            if (file.exists() && file.canExecute()) return file.absolutePath
        }

        // 2. JAVA_HOME
        val javaHome = System.getenv("JAVA_HOME")
        if (javaHome != null) {
            val javac = File(javaHome, "bin/javac")
            if (javac.exists()) return javac.absolutePath
        }

        // 3. System path (hope for the best)
        return try {
            val process = ProcessBuilder("which", "javac").start()
            val path = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (path.isNotBlank() && File(path).exists()) path else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Result of compiling instrumented sources.
     */
    data class CompilationResult(
        val success: Boolean,
        val errors: List<String>,
        val compiledCount: Int,
    )
}
