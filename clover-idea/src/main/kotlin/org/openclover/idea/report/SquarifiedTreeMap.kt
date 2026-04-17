package org.openclover.idea.report

import java.awt.Rectangle
import kotlin.math.sqrt

/**
 * Data class for treemap items with a label and size.
 */
data class TreeMapItem(
    val label: String,
    val size: Double
)

/**
 * Result of treemap layout: a rectangle with its corresponding item.
 */
data class TreeMapRect(
    val rect: Rectangle,
    val item: TreeMapItem
)

/**
 * Simple treemap layout algorithm.
 *
 * Uses a basic subdivision approach that's easier to implement correctly
 * than full squarified treemap, but still produces decent aspect ratios.
 */
object SquarifiedTreeMap {

    /**
     * Compute treemap layout for given items within bounds.
     *
     * @param items List of items to layout (must have positive sizes)
     * @param bounds Rectangle to fill
     * @return List of rectangles, one per item
     */
    fun layout(items: List<TreeMapItem>, bounds: Rectangle): List<TreeMapRect> {
        if (items.isEmpty() || bounds.width <= 0 || bounds.height <= 0) {
            return emptyList()
        }

        val totalSize = items.sumOf { it.size }
        if (totalSize <= 0.0) {
            return emptyList()
        }

        // Sort by size descending for better layout
        val sorted = items.sortedByDescending { it.size }

        val result = mutableListOf<TreeMapRect>()
        layoutRecursive(sorted, bounds, totalSize, result)
        return result
    }

    /**
     * Recursively subdivide the area for items.
     */
    private fun layoutRecursive(
        items: List<TreeMapItem>,
        bounds: Rectangle,
        totalSize: Double,
        result: MutableList<TreeMapRect>
    ) {
        if (items.isEmpty() || bounds.width <= 0 || bounds.height <= 0) {
            return
        }

        if (items.size == 1) {
            // Single item fills entire bounds
            result.add(TreeMapRect(bounds, items.first()))
            return
        }

        if (items.size == 2) {
            // Split area between two items
            val fraction = items.first().size / totalSize
            val horizontal = bounds.width >= bounds.height

            if (horizontal) {
                val splitX = (bounds.x + bounds.width * fraction).toInt()
                val rect1 = Rectangle(bounds.x, bounds.y, splitX - bounds.x, bounds.height)
                val rect2 = Rectangle(splitX, bounds.y, bounds.width - rect1.width, bounds.height)
                result.add(TreeMapRect(rect1, items[0]))
                result.add(TreeMapRect(rect2, items[1]))
            } else {
                val splitY = (bounds.y + bounds.height * fraction).toInt()
                val rect1 = Rectangle(bounds.x, bounds.y, bounds.width, splitY - bounds.y)
                val rect2 = Rectangle(bounds.x, splitY, bounds.width, bounds.height - rect1.height)
                result.add(TreeMapRect(rect1, items[0]))
                result.add(TreeMapRect(rect2, items[1]))
            }
            return
        }

        // For 3+ items, split into two groups and recursively layout
        val midpoint = items.size / 2
        val firstGroup = items.subList(0, midpoint)
        val secondGroup = items.subList(midpoint, items.size)

        val firstSize = firstGroup.sumOf { it.size }
        val fraction = firstSize / totalSize
        val horizontal = bounds.width >= bounds.height

        if (horizontal) {
            val splitX = (bounds.x + bounds.width * fraction).toInt().coerceAtMost(bounds.x + bounds.width - 1)
            val rect1 = Rectangle(bounds.x, bounds.y, (splitX - bounds.x).coerceAtLeast(1), bounds.height)
            val rect2 = Rectangle(splitX, bounds.y, (bounds.width - rect1.width).coerceAtLeast(1), bounds.height)
            layoutRecursive(firstGroup, rect1, firstSize, result)
            layoutRecursive(secondGroup, rect2, totalSize - firstSize, result)
        } else {
            val splitY = (bounds.y + bounds.height * fraction).toInt().coerceAtMost(bounds.y + bounds.height - 1)
            val rect1 = Rectangle(bounds.x, bounds.y, bounds.width, (splitY - bounds.y).coerceAtLeast(1))
            val rect2 = Rectangle(bounds.x, splitY, bounds.width, (bounds.height - rect1.height).coerceAtLeast(1))
            layoutRecursive(firstGroup, rect1, firstSize, result)
            layoutRecursive(secondGroup, rect2, totalSize - firstSize, result)
        }
    }
}
