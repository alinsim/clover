package org.openclover.idea.testexplorer

/**
 * Hierarchical model for test results: root → test classes → test methods.
 * Test class nodes aggregate pass/fail counts from their methods.
 *
 * Pure data model — no IntelliJ dependencies.
 */
class TestTreeModel private constructor(val root: TestTreeNode) {

    companion object {
        fun build(tests: List<TestInfo>): TestTreeModel {
            val classMap = mutableMapOf<String, MutableList<TestInfo>>()

            for (test in tests) {
                val qn = test.qualifiedName ?: continue
                val lastDot = qn.lastIndexOf('.')
                val className = if (lastDot > 0) {
                    val fqClass = qn.substring(0, lastDot)
                    val pkgDot = fqClass.lastIndexOf('.')
                    if (pkgDot > 0) fqClass.substring(pkgDot + 1) else fqClass
                } else {
                    qn
                }
                classMap.getOrPut(className) { mutableListOf() }.add(test)
            }

            val root = TestTreeNode(name = "Tests", isClass = false)

            for ((className, classTests) in classMap.toSortedMap()) {
                val passCount = classTests.count { it.status == "pass" }
                val failCount = classTests.count { it.status != "pass" }

                val classNode = TestTreeNode(
                    name = className,
                    isClass = true,
                    passCount = passCount,
                    failCount = failCount,
                )

                for (test in classTests) {
                    val methodName = test.qualifiedName?.let {
                        val dot = it.lastIndexOf('.')
                        if (dot >= 0) it.substring(dot + 1) else it
                    } ?: "unknown"

                    classNode.children.add(
                        TestTreeNode(
                            name = methodName,
                            isClass = false,
                            status = test.status,
                            durationMs = test.durationMs,
                            linesCovered = test.linesCovered,
                            uniqueLinesCovered = test.uniqueLinesCovered,
                        ),
                    )
                }

                root.children.add(classNode)
            }

            return TestTreeModel(root)
        }
    }
}

/**
 * Input data for test tree construction.
 */
data class TestInfo(
    val qualifiedName: String?,
    val status: String,
    val durationMs: Long,
    val linesCovered: Int,
    val uniqueLinesCovered: Int,
)

/**
 * A node in the test tree — either a test class or a test method.
 */
data class TestTreeNode(
    val name: String,
    val isClass: Boolean,
    val status: String? = null,
    val durationMs: Long = 0,
    val linesCovered: Int = 0,
    val uniqueLinesCovered: Int = 0,
    val passCount: Int = 0,
    val failCount: Int = 0,
    val children: MutableList<TestTreeNode> = mutableListOf(),
)
