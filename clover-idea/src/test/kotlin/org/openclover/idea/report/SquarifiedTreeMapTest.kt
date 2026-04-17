package org.openclover.idea.report

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Rectangle

/**
 * Tests for the squarified treemap layout algorithm.
 */
class SquarifiedTreeMapTest {

    @Test
    fun layoutWithSingleItemFillsEntireArea() {
        val items = listOf(TreeMapItem("A", 100.0))
        val bounds = Rectangle(0, 0, 100, 100)

        val rects = SquarifiedTreeMap.layout(items, bounds)

        assertEquals(1, rects.size)
        assertEquals(Rectangle(0, 0, 100, 100), rects[0].rect)
    }

    @Test
    fun layoutWithTwoEqualItemsSplitsArea() {
        val items = listOf(
            TreeMapItem("A", 50.0),
            TreeMapItem("B", 50.0)
        )
        val bounds = Rectangle(0, 0, 100, 100)

        val rects = SquarifiedTreeMap.layout(items, bounds)

        assertEquals(2, rects.size)

        // Both items should have equal area
        val area1 = rects[0].rect.width * rects[0].rect.height
        val area2 = rects[1].rect.width * rects[1].rect.height
        assertTrue(Math.abs(area1 - area2) < 100, "Areas should be roughly equal")
    }

    @Test
    fun layoutWithMultipleItemsFillsCompletely() {
        val items = listOf(
            TreeMapItem("A", 60.0),
            TreeMapItem("B", 30.0),
            TreeMapItem("C", 10.0)
        )
        val bounds = Rectangle(0, 0, 200, 100)

        val rects = SquarifiedTreeMap.layout(items, bounds)

        assertEquals(3, rects.size)

        // Total area should be close to bounds area (allowing for rounding)
        val totalArea = rects.sumOf { it.rect.width * it.rect.height }
        val boundsArea = bounds.width * bounds.height
        val percentCovered = (totalArea.toDouble() / boundsArea) * 100
        assertTrue(percentCovered >= 95.0, "Total area should cover at least 95% of bounds, got $percentCovered%")
    }

    @Test
    fun layoutWithEmptyListReturnsEmpty() {
        val items = emptyList<TreeMapItem>()
        val bounds = Rectangle(0, 0, 100, 100)

        val rects = SquarifiedTreeMap.layout(items, bounds)

        assertTrue(rects.isEmpty())
    }

    @Test
    fun layoutWithZeroSizeReturnsEmpty() {
        val items = listOf(TreeMapItem("A", 100.0))
        val bounds = Rectangle(0, 0, 0, 0)

        val rects = SquarifiedTreeMap.layout(items, bounds)

        assertTrue(rects.isEmpty())
    }

    @Test
    fun aspectRatioIsBetterThanSliceAndDice() {
        val items = listOf(
            TreeMapItem("A", 50.0),
            TreeMapItem("B", 50.0)
        )
        val bounds = Rectangle(0, 0, 400, 100)

        val rects = SquarifiedTreeMap.layout(items, bounds)

        // Squarified should produce more square-like rectangles
        // than simple horizontal slicing
        val aspectRatios = rects.map {
            val r = it.rect
            val ratio = maxOf(r.width.toDouble() / r.height, r.height.toDouble() / r.width)
            ratio
        }

        // Neither rectangle should be too elongated (4:1 or worse)
        assertTrue(aspectRatios.all { it < 4.0 }, "Aspect ratios should be reasonable: $aspectRatios")
    }
}
