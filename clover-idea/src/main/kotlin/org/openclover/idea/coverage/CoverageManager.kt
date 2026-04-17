package org.openclover.idea.coverage

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openclover.core.CloverDatabase
import org.openclover.core.CoverageData
import org.openclover.core.api.registry.FileInfo
import org.openclover.core.api.registry.HasMetricsFilter
import org.openclover.core.api.registry.ProjectInfo
import org.openclover.core.registry.metrics.BlockMetrics
import org.openclover.idea.config.CloverProjectConfig
import java.io.File

/**
 * Manages coverage data for a project.
 *
 * Loads the CloverDatabase from disk, provides per-file coverage data,
 * and supports periodic auto-refresh via coroutines.
 *
 * This replaces the old [DefaultCoverageManager] which used raw threads
 * and Swing invokeLater for async operations.
 */
class CoverageManager(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
    private val config: CloverProjectConfig,
) : Disposable {

    private val _state = MutableStateFlow<CoverageState>(CoverageState.Empty)
    val state: StateFlow<CoverageState> = _state.asStateFlow()

    private var autoRefreshJob: Job? = null
    private val dbLocator: CoverageDbLocator? = project.basePath?.let { CoverageDbLocator(File(it)) }

    /**
     * Load or reload coverage data from the configured database path.
     * Runs on the IO dispatcher to avoid blocking the EDT.
     */
    fun reload() {
        coroutineScope.launch {
            loadDatabase()
        }
    }

    /**
     * Start periodic auto-refresh of coverage data.
     */
    fun startAutoRefresh() {
        stopAutoRefresh()
        if (!config.autoRefresh || config.autoRefreshInterval <= 0) return

        autoRefreshJob = coroutineScope.launch {
            while (isActive) {
                delay(config.autoRefreshInterval)
                loadDatabase()
            }
        }
        thisLogger().info("Coverage auto-refresh started (interval: ${config.autoRefreshInterval}ms)")
    }

    /**
     * Stop periodic auto-refresh.
     */
    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    /**
     * Get coverage data for a specific file path.
     * Returns null if no coverage data is loaded or the file is not found.
     */
    fun getFileCoverage(filePath: String): FileCoverageInfo? {
        val loaded = _state.value as? CoverageState.Loaded ?: return null
        return loaded.fileCoverage[filePath]
    }

    /**
     * Get the full project model (if loaded).
     */
    fun getProjectInfo(): ProjectInfo? {
        val loaded = _state.value as? CoverageState.Loaded ?: return null
        return loaded.projectInfo
    }

    private suspend fun loadDatabase() {
        // Auto-detect database location if not explicitly configured
        val resolvedPath = dbLocator?.resolveInitString(config.initString) ?: config.initString

        if (resolvedPath.isBlank()) {
            _state.value = CoverageState.Empty
            return
        }

        val dbFile = File(resolvedPath)
        if (!dbFile.exists()) {
            thisLogger().debug("Coverage database not found: $resolvedPath")
            _state.value = CoverageState.Empty
            return
        }

        if (config.initString.isBlank() && resolvedPath.isNotBlank()) {
            thisLogger().info("Auto-detected coverage database: $resolvedPath")
        }

        val initString = resolvedPath

        _state.value = CoverageState.Loading

        try {
            val (db, projectInfo, fileCoverage) = withContext(Dispatchers.IO) {
                val database = CloverDatabase(initString)
                database.loadCoverageData()

                val model = database.fullModel
                val coverageData = database.coverageData
                val files = buildFileCoverageMap(model, coverageData)

                Triple(database, model, files)
            }

            _state.value = CoverageState.Loaded(
                database = db,
                projectInfo = projectInfo,
                fileCoverage = fileCoverage,
            )

            thisLogger().info(
                "Coverage loaded: ${fileCoverage.size} files, " +
                    "${projectInfo.metrics.numStatements} statements, " +
                    "${formatPercent(projectInfo.metrics.pcCoveredElements)}% covered",
            )
        } catch (e: Exception) {
            thisLogger().warn("Failed to load coverage database: $initString", e)
            _state.value = CoverageState.Error(e.message ?: "Unknown error")
        }
    }

    private fun buildFileCoverageMap(
        projectInfo: ProjectInfo,
        coverageData: CoverageData?,
    ): Map<String, FileCoverageInfo> {
        val map = mutableMapOf<String, FileCoverageInfo>()

        for (pkg in projectInfo.allPackages) {
            for (fileInfo in pkg.files) {
                val path = fileInfo.physicalFile?.absolutePath ?: continue
                map[path] = buildFileCoverageInfo(fileInfo, coverageData)
            }
        }

        return map
    }

    private fun buildFileCoverageInfo(
        fileInfo: FileInfo,
        coverageData: CoverageData?,
    ): FileCoverageInfo {
        val lineStatuses = mutableMapOf<Int, LineCoverageStatus>()
        val lineDetails = mutableMapOf<Int, LineCoverageDetail>()

        if (coverageData != null) {
            for (classInfo in fileInfo.classes) {
                for (method in classInfo.methods) {
                    // Record method entry on the method's start line
                    val methodHits = coverageData.getHitCount(method.dataIndex)
                    val methodLine = method.startLine
                    if (methodLine > 0) {
                        lineDetails[methodLine] = LineCoverageDetail(
                            status = if (methodHits > 0) LineCoverageStatus.COVERED else LineCoverageStatus.UNCOVERED,
                            methodHits = methodHits,
                        )
                        lineStatuses[methodLine] = if (methodHits > 0) LineCoverageStatus.COVERED else LineCoverageStatus.UNCOVERED
                    }

                    for (stmt in method.statements) {
                        val hits = coverageData.getHitCount(stmt.dataIndex)
                        val status = if (hits > 0) LineCoverageStatus.COVERED else LineCoverageStatus.UNCOVERED
                        for (line in stmt.startLine..stmt.endLine) {
                            val existing = lineStatuses[line]
                            if (existing == null || status == LineCoverageStatus.COVERED) {
                                lineStatuses[line] = status
                            }
                            // Update detail with statement hits (preserve method hits if present)
                            val existingDetail = lineDetails[line]
                            lineDetails[line] = LineCoverageDetail(
                                status = lineStatuses[line] ?: status,
                                statementHits = hits,
                                methodHits = existingDetail?.methodHits ?: -1,
                            )
                        }
                    }
                    for (branch in method.branches) {
                        val trueHits = branch.trueHitCount
                        val falseHits = branch.falseHitCount
                        val status = when {
                            trueHits > 0 && falseHits > 0 -> LineCoverageStatus.COVERED
                            trueHits > 0 || falseHits > 0 -> LineCoverageStatus.PARTIAL
                            else -> LineCoverageStatus.UNCOVERED
                        }
                        for (line in branch.startLine..branch.endLine) {
                            val existing = lineStatuses[line]
                            if (existing == null || status.ordinal < (existing.ordinal)) {
                                lineStatuses[line] = status
                            }
                            // Update detail with branch info
                            val existingDetail = lineDetails[line]
                            lineDetails[line] = LineCoverageDetail(
                                status = lineStatuses[line] ?: status,
                                statementHits = existingDetail?.statementHits ?: 0,
                                branchTrueHits = trueHits,
                                branchFalseHits = falseHits,
                                methodHits = existingDetail?.methodHits ?: -1,
                            )
                        }
                    }
                }
            }
        }

        val metrics = fileInfo.metrics as? BlockMetrics
        return FileCoverageInfo(
            filePath = fileInfo.physicalFile?.absolutePath ?: "",
            lineStatuses = lineStatuses,
            lineDetails = lineDetails,
            numStatements = metrics?.numStatements ?: 0,
            numCoveredStatements = metrics?.numCoveredStatements ?: 0,
            numBranches = metrics?.numBranches ?: 0,
            numCoveredBranches = metrics?.numCoveredBranches ?: 0,
            percentCovered = metrics?.pcCoveredElements ?: 0f,
        )
    }

    private fun formatPercent(value: Float): String =
        String.format("%.1f", value * 100)

    override fun dispose() {
        stopAutoRefresh()
    }
}

/**
 * Represents the current state of coverage data loading.
 */
sealed class CoverageState {
    data object Empty : CoverageState()
    data object Loading : CoverageState()
    data class Loaded(
        val database: CloverDatabase,
        val projectInfo: ProjectInfo,
        val fileCoverage: Map<String, FileCoverageInfo>,
    ) : CoverageState()
    data class Error(val message: String) : CoverageState()
}

/**
 * Coverage status for a single source line.
 * Ordered by severity: PARTIAL < UNCOVERED < COVERED
 * (lower ordinal = "worse" coverage, takes precedence in display).
 */
enum class LineCoverageStatus {
    PARTIAL,
    UNCOVERED,
    COVERED,
}

/**
 * Per-file coverage information for editor display.
 */
data class FileCoverageInfo(
    val filePath: String,
    val lineStatuses: Map<Int, LineCoverageStatus>,
    val lineDetails: Map<Int, LineCoverageDetail>,
    val numStatements: Int,
    val numCoveredStatements: Int,
    val numBranches: Int,
    val numCoveredBranches: Int,
    val percentCovered: Float,
)
