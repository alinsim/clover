package org.openclover.idea.config

/**
 * Project-level configuration for OpenClover.
 * Persisted via [PersistentStateComponent] in the workspace settings.
 *
 * Maps to the old [IdeaCloverConfig] which stored per-project instrumentation
 * and reporting settings.
 */
data class CloverProjectConfig(
    /** Whether Clover instrumentation is enabled for this project. */
    var enabled: Boolean = true,

    /** Whether to build with Clover instrumentation. */
    var buildWithClover: Boolean = true,

    /** Whether to show coverage data in the editor. */
    var showCoverage: Boolean = true,

    /** Whether to show coverage gutter icons. */
    var showGutter: Boolean = true,

    /** Whether to show inline coverage highlights. */
    var showInline: Boolean = true,

    /** Whether to show coverage tooltips on hover. */
    var showTooltips: Boolean = true,

    /** Whether to show error marks in the scrollbar. */
    var showErrorMarks: Boolean = true,

    /** Whether to show coverage overlay icons in the project view. */
    var showProjectViewAnnotation: Boolean = true,

    /** Whether to auto-refresh coverage data periodically. */
    var autoRefresh: Boolean = true,

    /** Auto-refresh interval in milliseconds. */
    var autoRefreshInterval: Long = 2000L,

    /** Coverage database init string (path pattern). */
    var initString: String = "",

    /** Coverage span (time window for filtering coverage data). */
    var span: String = "0s",

    /** Context filter specification (e.g., exclude try/catch blocks). */
    var contextFilterSpec: String = "",

    /** Include passed tests only. */
    var includePassedTestCoverageOnly: Boolean = true,

    /** Include failed tests. */
    var includeFailedTestCoverage: Boolean = false,
)
