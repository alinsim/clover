package org.openclover.idea.editor

import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

/**
 * Programmatic gutter icons for coverage status.
 * Using painted icons instead of PNGs for crisp rendering at all scale factors
 * and proper dark/light theme support.
 *
 * These will be replaced with proper SVG icons in task .19.
 */
object CoverageIcons {

    private val GREEN = JBColor(Color(0x59, 0xA8, 0x69), Color(0x49, 0x98, 0x59))
    private val RED = JBColor(Color(0xDB, 0x56, 0x60), Color(0xCB, 0x46, 0x50))
    private val YELLOW = JBColor(Color(0xE0, 0xA3, 0x30), Color(0xD0, 0x93, 0x20))

    val COVERED: Icon = CircleIcon(GREEN)
    val UNCOVERED: Icon = CircleIcon(RED)
    val PARTIAL: Icon = CircleIcon(YELLOW)

    private class CircleIcon(private val color: JBColor) : Icon {
        private val size = JBUIScale.scale(8)

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = color
                g2.fillOval(x, y, size, size)
            } finally {
                g2.dispose()
            }
        }

        override fun getIconWidth(): Int = size
        override fun getIconHeight(): Int = size
    }
}
