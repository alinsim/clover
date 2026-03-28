package org.openclover.idea.editor

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * SVG icons for coverage status in the editor gutter and elsewhere.
 * Uses IntelliJ's IconLoader which automatically resolves _dark.svg variants
 * based on the current theme.
 */
object CoverageIcons {
    val COVERED: Icon = IconLoader.getIcon("/icons/svg/covered.svg", CoverageIcons::class.java)
    val UNCOVERED: Icon = IconLoader.getIcon("/icons/svg/uncovered.svg", CoverageIcons::class.java)
    val PARTIAL: Icon = IconLoader.getIcon("/icons/svg/partial.svg", CoverageIcons::class.java)

    val CLOVER: Icon = IconLoader.getIcon("/icons/svg/clover.svg", CoverageIcons::class.java)
    val REFRESH: Icon = IconLoader.getIcon("/icons/svg/refresh.svg", CoverageIcons::class.java)
    val TEST: Icon = IconLoader.getIcon("/icons/svg/test.svg", CoverageIcons::class.java)
    val TREEMAP: Icon = IconLoader.getIcon("/icons/svg/treemap.svg", CoverageIcons::class.java)
    val CLOUD: Icon = IconLoader.getIcon("/icons/svg/cloud.svg", CoverageIcons::class.java)
}
