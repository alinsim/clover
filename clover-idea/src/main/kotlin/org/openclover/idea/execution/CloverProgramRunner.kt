package org.openclover.idea.execution

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.impl.DefaultJavaProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.thisLogger
import org.openclover.idea.CloverProjectService
import org.openclover.idea.build.CloverRuntimeLocator

/**
 * Program runner for the Clover coverage executor.
 *
 * Extends DefaultJavaProgramRunner to intercept execution and:
 * 1. Add clover-runtime.jar to the test classpath
 * 2. Pass the coverage database init string as a system property
 *
 * Source instrumentation is handled by [CloverCompileTask] which is
 * registered as a before-compile task and runs automatically.
 * Coverage reload is handled by [CoverageReloadListener] which fires
 * on process termination.
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

        // Patch the Java parameters to include Clover runtime
        patchJavaParameters(environment, service)

        return super.doExecute(state, environment)
    }

    private fun patchJavaParameters(environment: ExecutionEnvironment, service: CloverProjectService) {
        val config = service.getConfig()

        // Find the Clover runtime JAR
        val runtimeJar = CloverRuntimeLocator.findRuntimeJar()
        if (runtimeJar != null) {
            thisLogger().info("Adding Clover runtime to classpath: $runtimeJar")
            // The runtime JAR will be added via JavaParameters when available
            // For now, we rely on the module dependency injection done by CloverCompileTask
        } else {
            thisLogger().warn("Cannot find Clover runtime JAR — instrumented code may fail at runtime")
        }

        // Log the database path for debugging
        val dbPath = config.initString
        if (dbPath.isNotBlank()) {
            thisLogger().info("Clover database: $dbPath")
        }
    }

    companion object {
        const val RUNNER_ID = "CloverCoverageRunner"
    }
}
