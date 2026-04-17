package org.openclover.idea.execution

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.JavaCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.impl.DefaultJavaProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import org.openclover.core.cfg.instr.java.JavaInstrumentationConfig
import org.openclover.core.instr.java.Instrumenter
import org.openclover.idea.CloverProjectService
import org.openclover.idea.build.CloverRuntimeLocator
import org.openclover.idea.util.CloverNotifications
import java.io.File

/**
 * Program runner for the "Run with Clover Coverage" executor.
 *
 * This is the ONLY code path that instruments sources. Normal Run/Debug
 * is completely untouched — no instrumentation, no coverage.
 *
 * Flow:
 * 1. Instrument sources to a temp directory
 * 2. Add clover-runtime.jar to the test classpath
 * 3. Set clover.initstring JVM property
 * 4. Delegate to the standard Java runner
 * 5. CoverageReloadListener picks up process termination and loads results
 */
class CloverProgramRunner : DefaultJavaProgramRunner() {

    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == CloverCoverageExecutor.EXECUTOR_ID
    }

    @Throws(ExecutionException::class)
    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val project = environment.project
        val service = CloverProjectService.getInstance(project)

        if (!service.isEnabled) {
            thisLogger().info("OpenClover is disabled — running without coverage")
            return super.doExecute(state, environment)
        }

        thisLogger().info("Running with OpenClover coverage for: ${environment.runProfile.name}")

        // Step 1: Instrument sources
        val projectBasePath = project.basePath
            ?: throw ExecutionException("Cannot determine project base path")

        val dbPath = resolveDbPath(service, projectBasePath)
        val instrumentedCount = instrumentSources(project, projectBasePath, dbPath)
        thisLogger().info("Instrumented $instrumentedCount source files")

        // Step 2: Patch classpath and VM options
        if (state is JavaCommandLine) {
            val javaParameters = state.javaParameters

            // Add clover-runtime to classpath
            val runtimeJar = CloverRuntimeLocator.findRuntimeJar()
            if (runtimeJar != null) {
                javaParameters.classPath.add(runtimeJar)
                thisLogger().info("Added Clover runtime to classpath: $runtimeJar")
            } else {
                CloverNotifications.notifyWarning(project,
                    "Cannot find Clover runtime JAR — coverage may not work")
            }

            // Set database path
            javaParameters.vmParametersList.defineProperty("clover.initstring", dbPath)
        } else {
            thisLogger().warn("Run profile is not JavaCommandLine — cannot inject Clover runtime")
        }

        // Step 3: Execute (CoverageReloadListener will handle post-run reload)
        return super.doExecute(state, environment)
    }

    private fun resolveDbPath(service: CloverProjectService, projectBasePath: String): String {
        val configured = service.getConfig().initString
        return configured.ifBlank {
            File(projectBasePath, ".clover/clover.db").absolutePath
        }
    }

    private fun instrumentSources(
        project: com.intellij.openapi.project.Project,
        projectBasePath: String,
        dbPath: String,
    ): Int {
        val config = JavaInstrumentationConfig()
        config.setInitstring(dbPath)
        config.projectName = project.name
        config.encoding = "UTF-8"

        // Collect source files under read action
        val sourceFiles = ReadAction.compute<List<File>, RuntimeException> {
            collectSourceFiles(project)
        }
        if (sourceFiles.isEmpty()) return 0

        // Clean and create instrumented output directory
        val destDir = File(projectBasePath, ".clover/instrumented")
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        destDir.mkdirs()

        // Instrument
        val instrumenter = Instrumenter(config)
        instrumenter.startInstrumentation()

        var count = 0
        try {
            for (srcFile in sourceFiles) {
                try {
                    instrumenter.instrument(srcFile, destDir, "UTF-8")
                    count++
                } catch (e: Exception) {
                    thisLogger().warn("Failed to instrument: ${srcFile.name}", e)
                }
            }
        } finally {
            instrumenter.endInstrumentation()
        }

        return count
    }

    private fun collectSourceFiles(project: com.intellij.openapi.project.Project): List<File> {
        val files = mutableListOf<File>()
        for (module in ModuleManager.getInstance(project).modules) {
            val roots = ModuleRootManager.getInstance(module).sourceRoots
            for (root in roots) {
                VfsUtilCore.iterateChildrenRecursively(root, null) { vf ->
                    if (!vf.isDirectory && vf.extension == "java") {
                        val ioFile = File(vf.path)
                        if (ioFile.exists()) {
                            files.add(ioFile)
                        }
                    }
                    true
                }
            }
        }
        return files
    }

    companion object {
        const val RUNNER_ID = "CloverCoverageRunner"
    }
}
