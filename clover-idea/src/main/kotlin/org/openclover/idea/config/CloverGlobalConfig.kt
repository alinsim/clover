package org.openclover.idea.config

/**
 * Application-level (global) configuration for OpenClover.
 * Persisted via [PersistentStateComponent] in the application settings.
 */
data class CloverGlobalConfig(
    var autoScrollToSource: Boolean = false,
    var autoScrollFromSource: Boolean = false,
    var flattenPackages: Boolean = false,
)
