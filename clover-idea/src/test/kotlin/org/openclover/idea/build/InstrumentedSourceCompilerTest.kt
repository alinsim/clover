package org.openclover.idea.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Tests for InstrumentedSourceCompiler — compiles instrumented .java files
 * using javax.tools.JavaCompiler.
 */
class InstrumentedSourceCompilerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun compilesSimpleJavaFile() {
        val sourceDir = tempDir.resolve("src").toFile().apply { mkdirs() }
        val outputDir = tempDir.resolve("classes").toFile().apply { mkdirs() }

        // Write a simple Java file
        File(sourceDir, "Hello.java").writeText(
            "public class Hello { public static String greet() { return \"hello\"; } }"
        )

        val result = InstrumentedSourceCompiler.compile(
            sourceDir = sourceDir,
            outputDir = outputDir,
            classpath = emptyList(),
        )

        assertTrue(result.success, "Compilation should succeed: ${result.errors}")
        assertTrue(File(outputDir, "Hello.class").exists(), "Hello.class should exist")
    }

    @Test
    fun compilesWithDependencyOnClasspath() {
        val sourceDir = tempDir.resolve("src").toFile().apply { mkdirs() }
        val outputDir = tempDir.resolve("classes").toFile().apply { mkdirs() }
        val depDir = tempDir.resolve("deps").toFile().apply { mkdirs() }

        // Create a dependency class
        File(depDir, "Base.java").writeText(
            "public class Base { public int value() { return 42; } }"
        )
        InstrumentedSourceCompiler.compile(depDir, depDir, emptyList())

        // Create source that depends on Base
        File(sourceDir, "Child.java").writeText(
            "public class Child extends Base { public int doubled() { return value() * 2; } }"
        )

        val result = InstrumentedSourceCompiler.compile(
            sourceDir = sourceDir,
            outputDir = outputDir,
            classpath = listOf(depDir),
        )

        assertTrue(result.success, "Compilation should succeed: ${result.errors}")
        assertTrue(File(outputDir, "Child.class").exists(), "Child.class should exist")
    }

    @Test
    fun reportsCompilationErrors() {
        val sourceDir = tempDir.resolve("src").toFile().apply { mkdirs() }
        val outputDir = tempDir.resolve("classes").toFile().apply { mkdirs() }

        // Write invalid Java
        File(sourceDir, "Bad.java").writeText(
            "public class Bad { this is not valid java }"
        )

        val result = InstrumentedSourceCompiler.compile(
            sourceDir = sourceDir,
            outputDir = outputDir,
            classpath = emptyList(),
        )

        assertTrue(!result.success, "Compilation should fail")
        assertTrue(result.errors.isNotEmpty(), "Should have error messages")
    }

    @Test
    fun compilesPackagedSource() {
        val sourceDir = tempDir.resolve("src").toFile().apply { mkdirs() }
        val outputDir = tempDir.resolve("classes").toFile().apply { mkdirs() }

        // Write a packaged Java file
        val pkgDir = File(sourceDir, "com/example").apply { mkdirs() }
        File(pkgDir, "Foo.java").writeText(
            "package com.example;\npublic class Foo { public String name() { return \"foo\"; } }"
        )

        val result = InstrumentedSourceCompiler.compile(
            sourceDir = sourceDir,
            outputDir = outputDir,
            classpath = emptyList(),
        )

        assertTrue(result.success, "Compilation should succeed: ${result.errors}")
        assertTrue(
            File(outputDir, "com/example/Foo.class").exists(),
            "Packaged class should exist at com/example/Foo.class"
        )
    }

    @Test
    fun returnsFileCountOnSuccess() {
        val sourceDir = tempDir.resolve("src").toFile().apply { mkdirs() }
        val outputDir = tempDir.resolve("classes").toFile().apply { mkdirs() }

        File(sourceDir, "A.java").writeText("public class A {}")
        File(sourceDir, "B.java").writeText("public class B {}")

        val result = InstrumentedSourceCompiler.compile(
            sourceDir = sourceDir,
            outputDir = outputDir,
            classpath = emptyList(),
        )

        assertTrue(result.success)
        assertEquals(2, result.compiledCount)
    }
}
