package org.openclover.idea.execution

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.JavaCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import org.openclover.core.cfg.instr.java.JavaInstrumentationConfig
import org.openclover.core.instr.java.Instrumenter
import org.openclover.idea.build.InstrumentedSourceCompiler
import org.openclover.idea.CloverProjectService
import org.openclover.idea.build.CloverRuntimeLocator
import org.openclover.idea.util.CloverNotifications
import java.io.File

/**
 * Program runner for the "Run with Clover Coverage" executor.
 *
 * Extends GenericProgramRunner (not DefaultJavaProgramRunner) to ensure
 * IntelliJ dispatches to us instead of the default runner.
 *
 * Flow:
 * 1. Instrument sources to a temp directory
 * 2. Patch JavaParameters: add clover-runtime.jar, set clover.initstring
 * 3. Execute the run profile state
 * 4. CoverageReloadListener picks up process termination and loads results
 */
class CloverProgramRunner : GenericProgramRunner<com.intellij.execution.configurations.RunnerSettings>() {

    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == CloverCoverageExecutor.EXECUTOR_ID
    }

    @Throws(ExecutionException::class)
    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val project = environment.project
        val service = CloverProjectService.getInstance(project)

        thisLogger().info("CloverProgramRunner.doExecute() called for: ${environment.runProfile.name}")

        if (!service.isEnabled) {
            thisLogger().info("OpenClover is disabled — running without coverage")
            return executeNormally(state, environment)
        }

        // Step 1: Instrument sources (modal progress to keep EDT responsive)
        val projectBasePath = project.basePath
            ?: throw ExecutionException("Cannot determine project base path")

        val dbPath = resolveDbPath(service, projectBasePath)
        var instrumentedCount = 0

        ProgressManager.getInstance().run(object : Task.Modal(project, "Instrumenting Sources with Clover", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = "Collecting source files..."
                indicator.fraction = 0.0

                try {
                    instrumentedCount = instrumentSources(project, projectBasePath, dbPath, indicator)
                    thisLogger().info("OpenClover: instrumented $instrumentedCount source files")
                } catch (e: Exception) {
                    thisLogger().error("OpenClover instrumentation failed", e)
                    CloverNotifications.notifyError(project,
                        "Instrumentation failed: ${e.message}. Running tests without coverage.")
                }
            }
        })

        // Step 2: Compile instrumented sources to shadow classes
        val instrumentedDir = File(projectBasePath, ".clover/instrumented")
        val classesDir = File(projectBasePath, ".clover/classes")
        var compilationSuccess = false

        if (instrumentedCount > 0) {
            ProgressManager.getInstance().run(object : Task.Modal(project, "Compiling Instrumented Sources", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "Compiling $instrumentedCount instrumented files..."

                    // Build compilation classpath: project classpath + clover-runtime
                    val projectClasspath = if (state is JavaCommandLine) {
                        state.javaParameters.classPath.pathList.map { File(it) }
                    } else {
                        emptyList()
                    }
                    val runtimeJar = CloverRuntimeLocator.findRuntimeJar()
                    val fullClasspath = if (runtimeJar != null) {
                        projectClasspath + File(runtimeJar)
                    } else {
                        projectClasspath
                    }

                    val result = InstrumentedSourceCompiler.compile(
                        sourceDir = instrumentedDir,
                        outputDir = classesDir,
                        classpath = fullClasspath,
                    )

                    compilationSuccess = result.success
                    if (!result.success) {
                        thisLogger().error("Instrumented compilation failed: ${result.errors.take(3)}")
                        CloverNotifications.notifyError(project,
                            "Compilation of instrumented sources failed. Running without coverage.")
                    } else {
                        thisLogger().info("Compiled ${result.compiledCount} instrumented files")
                    }
                }
            })

            if (compilationSuccess) {
                CloverNotifications.notifyInfo(project,
                    "Instrumented $instrumentedCount files. Running tests with coverage...")
            }
        }

        // Step 3: Patch classpath and VM options
        if (state is JavaCommandLine) {
            val javaParameters = state.javaParameters

            // CRITICAL: Prepend instrumented classes BEFORE original classes
            // so JVM loads the instrumented version
            if (compilationSuccess && classesDir.exists()) {
                javaParameters.classPath.addFirst(classesDir.absolutePath)
                thisLogger().info("Prepended instrumented classes to classpath: ${classesDir.absolutePath}")
            }

            val runtimeJar = CloverRuntimeLocator.findRuntimeJar()
            if (runtimeJar != null) {
                javaParameters.classPath.add(runtimeJar)
                thisLogger().info("Added Clover runtime to classpath: $runtimeJar")
            } else {
                thisLogger().warn("Cannot find Clover runtime JAR")
                CloverNotifications.notifyWarning(project,
                    "Cannot find Clover runtime JAR — coverage may not work")
            }

            javaParameters.vmParametersList.defineProperty("clover.initstring", dbPath)
            thisLogger().info("Set clover.initstring=$dbPath")
        } else {
            thisLogger().warn("Run profile is not JavaCommandLine — cannot inject Clover runtime")
        }

        // Step 3: Execute
        return executeNormally(state, environment)
    }

    private fun executeNormally(
        state: RunProfileState,
        environment: ExecutionEnvironment,
    ): RunContentDescriptor? {
        val executionResult = state.execute(environment.executor, this)
            ?: return null
        return RunContentBuilder(executionResult, environment).showRunContent(environment.contentToReuse)
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
        indicator: ProgressIndicator? = null,
    ): Int {
        val config = JavaInstrumentationConfig()
        config.setInitstring(dbPath)
        config.projectName = project.name
        config.encoding = "UTF-8"

        val sourceFiles = ReadAction.compute<List<File>, RuntimeException> {
            collectSourceFiles(project)
        }
        if (sourceFiles.isEmpty()) return 0

        val destDir = File(projectBasePath, ".clover/instrumented")
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        destDir.mkdirs()

        val instrumenter = Instrumenter(config)
        instrumenter.startInstrumentation()

        var count = 0
        val total = sourceFiles.size
        indicator?.text = "Instrumenting $total source files..."
        try {
            for ((index, srcFile) in sourceFiles.withIndex()) {
                indicator?.fraction = index.toDouble() / total
                indicator?.text2 = srcFile.name
                if (indicator?.isCanceled == true) break
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
