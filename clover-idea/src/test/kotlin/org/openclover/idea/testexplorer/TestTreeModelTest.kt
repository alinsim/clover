package org.openclover.idea.testexplorer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for TestTreeModel — hierarchical test class → test method tree.
 */
class TestTreeModelTest {

    @Test
    fun emptyModelHasNoChildren() {
        val model = TestTreeModel.build(emptyList())
        assertEquals(0, model.root.children.size)
    }

    @Test
    fun singleTestCreatesClassAndMethodNodes() {
        val tests = listOf(
            TestInfo("com.example.FooTest.testBar", "pass", 45, 22, 3),
        )
        val model = TestTreeModel.build(tests)

        assertEquals(1, model.root.children.size)
        val classNode = model.root.children[0]
        assertEquals("FooTest", classNode.name)
        assertEquals(1, classNode.children.size)
        assertEquals("testBar", classNode.children[0].name)
    }

    @Test
    fun multipleTestsInSameClassGrouped() {
        val tests = listOf(
            TestInfo("com.example.FooTest.testA", "pass", 10, 5, 1),
            TestInfo("com.example.FooTest.testB", "fail", 20, 8, 0),
        )
        val model = TestTreeModel.build(tests)

        assertEquals(1, model.root.children.size)
        assertEquals(2, model.root.children[0].children.size)
    }

    @Test
    fun classNodeAggregatesPassFail() {
        val tests = listOf(
            TestInfo("com.example.FooTest.testA", "pass", 10, 5, 1),
            TestInfo("com.example.FooTest.testB", "fail", 20, 8, 0),
            TestInfo("com.example.FooTest.testC", "pass", 15, 3, 2),
        )
        val model = TestTreeModel.build(tests)

        val classNode = model.root.children[0]
        assertEquals(2, classNode.passCount)
        assertEquals(1, classNode.failCount)
    }

    @Test
    fun differentClassesCreateSeparateNodes() {
        val tests = listOf(
            TestInfo("com.example.FooTest.testA", "pass", 10, 5, 0),
            TestInfo("com.example.BarTest.testB", "pass", 20, 8, 0),
        )
        val model = TestTreeModel.build(tests)

        assertEquals(2, model.root.children.size)
    }

    @Test
    fun classesSortedAlphabetically() {
        val tests = listOf(
            TestInfo("z.ZTest.testZ", "pass", 1, 1, 0),
            TestInfo("a.ATest.testA", "pass", 1, 1, 0),
        )
        val model = TestTreeModel.build(tests)

        assertEquals("ATest", model.root.children[0].name)
        assertEquals("ZTest", model.root.children[1].name)
    }

    @Test
    fun nullQualifiedNameSkipped() {
        val tests = listOf(
            TestInfo(null, "pass", 10, 5, 0),
            TestInfo("com.example.FooTest.testA", "pass", 10, 5, 0),
        )
        val model = TestTreeModel.build(tests)

        assertEquals(1, model.root.children.size)
    }
}
