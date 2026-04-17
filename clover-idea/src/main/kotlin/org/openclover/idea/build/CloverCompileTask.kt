package org.openclover.idea.build

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompileTask
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import org.openclover.core.cfg.instr.java.JavaInstrumentationConfig
import org.openclover.core.instr.java.Instrumenter
import org.openclover.idea.CloverProjectService
import java.io.File

/**
 * Compiler task that instruments Java sources with Clover before compilation.
 *
 * Registered via [CompilerManager.addBeforeTask]. When Clover instrumentation
 * is enabled for the project, this task:
 * 1. Collects all Java source files from module source roots (via ReadAction)
 * 2. Instruments them using [Instrumenter] (the AstInstrumenter path)
 * 3. Writes instrumented files to a build-specific temp directory
 * 4. Sets up the coverage database init string
 */
class CloverCompileTask(private val project: Project) : CompileTask {

    override fun execute(context: CompileContext): Boolean {
        val service = CloverProjectService.getInstance(project)
        if (!service.isEnabled || !service.isBuildWithClover) {
            return true
        }

        return try {
            val count = instrumentProject(service)
            if (count > 0) {
                thisLogger().info("OpenClover: instrumented $count source files")
            }
            true
        } catch (e: Exception) {
            thisLogger().error("OpenClover instrumentation failed", e)
            context.addMessage(
                CompilerMessageCategory.ERROR,
                "OpenClover instrumentation failed: ${e.message}",
                null, -1, -1,
            )
            false
        }
    }

    private fun instrumentProject(service: CloverProjectService): Int {
        val config = service.getConfig()
        val projectBasePath = project.basePath ?: return 0

        val instrConfig = JavaInstrumentationConfig()
        val dbPath = config.initString.ifBlank {
            File(projectBasePath, ".clover/clover.db").absolutePath
        }
        instrConfig.setInitstring(dbPath)
        instrConfig.projectName = project.name
        instrConfig.encoding = config.encoding

        // Collect source files under read action (VFS requires it)
        val sourceFiles = ReadAction.compute<List<File>, RuntimeException> {
            collectSourceFiles()
        }
        if (sourceFiles.isEmpty()) {
            return 0
        }

        // Clean instrumented output directory to avoid stale files
        val destDir = File(projectBasePath, ".clover/instrumented")
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        destDir.mkdirs()

        val instrumenter = Instrumenter(instrConfig)
        instrumenter.startInstrumentation()

        var count = 0
        try {
            for (srcFile in sourceFiles) {
                try {
                    instrumenter.instrument(srcFile, destDir, config.encoding)
                    count++
                } catch (e: Exception) {
                    thisLogger().warn("Failed to instrument: ${srcFile.name}", e)
                }
            }
        } finally {
            // Always close the instrumentation session to avoid corrupt registry
            instrumenter.endInstrumentation()
        }
        return count
    }

    private fun collectSourceFiles(): List<File> {
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
        fun register(project: Project) {
            CompilerManager.getInstance(project).addBeforeTask(CloverCompileTask(project))
            thisLogger().info("OpenClover compile task registered for: ${project.name}")
        }
    }
}
