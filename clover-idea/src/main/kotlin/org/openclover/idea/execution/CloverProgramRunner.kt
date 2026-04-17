package org.openclover.idea.execution

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.JavaCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
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
 * 1. Add clover-runtime.jar to the test classpath via JavaParameters
 * 2. Pass the coverage database init string as a JVM system property
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

        // Patch classpath and VM options if the run profile supports Java parameters
        if (state is JavaCommandLine) {
            patchJavaParameters(state, service)
        } else {
            thisLogger().warn("Run profile state is not JavaCommandLine — cannot inject Clover runtime")
        }

        return super.doExecute(state, environment)
    }

    private fun patchJavaParameters(commandLine: JavaCommandLine, service: CloverProjectService) {
        val javaParameters = commandLine.javaParameters
        val config = service.getConfig()

        // Add clover-runtime.jar to the classpath
        val runtimeJar = CloverRuntimeLocator.findRuntimeJar()
        if (runtimeJar != null) {
            javaParameters.classPath.add(runtimeJar)
            thisLogger().info("Added Clover runtime to classpath: $runtimeJar")
        } else {
            thisLogger().warn("Cannot find Clover runtime JAR — instrumented code may fail at runtime")
        }

        // Pass the database init string so the runtime knows where to write coverage data
        val dbPath = config.initString
        if (dbPath.isNotBlank()) {
            javaParameters.vmParametersList.defineProperty("clover.initstring", dbPath)
            thisLogger().info("Set clover.initstring=$dbPath")
        }
    }

    companion object {
        const val RUNNER_ID = "CloverCoverageRunner"
    }
}
