package org.openclover.core.instr.java.javaparser;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests that validate JavaParser's ability to parse and instrument modern Java features
 * that ANTLR 2 grammar struggles with. These tests demonstrate JavaParser's advantage
 * in handling Java 8+ syntax.
 * <p>
 * Note: JavaParser 3.25.10 supports features up to approximately Java 17-20.
 * Tests are designed within this range.
 * </p>
 */
public class ModernJavaSyntaxTest {

    private static final String INC_PATTERN = ".inc(";

    // Common code fragments
    private static final String CLASS_FOO_OPEN = "class Foo {\n";
    private static final String INTERFACE_FOO_OPEN = "interface Foo {\n";
    private static final String METHOD_BAR_VOID = "    void bar";
    private static final String METHOD_BAR_OPEN = METHOD_BAR_VOID + "() {\n";
    private static final String METHOD_BAR_OBJECT_OPEN = METHOD_BAR_VOID + "(Object obj) {\n";
    private static final String METHOD_BAR_DEFAULT_OPEN = "    default void bar() {\n";
    private static final String INDENT_4_CLOSE = "    }\n";
    private static final String INDENT_8 = "        ";
    private static final String INDENT_12 = "            ";
    private static final String CLOSE_BRACE = "}";
    private static final String CLOSE_BRACE_NL = CLOSE_BRACE + "\n";
    private static final String INDENT_8_CLOSE = INDENT_8 + "}\n";
    private static final String DIAMOND_CLOSE = "<>();\n";
    private static final String METHOD_AREA_OPEN = "    double area() {\n";
    private static final String RETURN_0 = INDENT_8 + "return 0;\n";
    private static final String PRINTLN_ITEM = INDENT_12 + "System.out.println(item);\n";
    private static final String MAP_TO_UPPERCASE = INDENT_12 + ".map(String::toUpperCase)\n";
    private static final String FILTER_LENGTH_GT_5 = INDENT_12 + ".filter(s -> s.length() > 5)\n";
    private static final String STRING_TO_UPPERCASE = "String::toUpperCase";

    // Java package prefixes (for building FQN in test strings)
    private static final String PKG_JAVA_UTIL = "java.util.";
    private static final String PKG_JAVA_IO = "java.io.";
    private static final String PKG_JAVA_UTIL_FUNCTION = "java.util.function.";

    // Type names for test source strings
    private static final String TYPE_LIST = "List";
    private static final String TYPE_MAP = "Map";
    private static final String TYPE_ARRAYLIST = "ArrayList";
    private static final String TYPE_HASHMAP = "HashMap";
    private static final String TYPE_COLLECTIONS = "Collections";
    private static final String TYPE_OPTIONAL = "Optional";
    private static final String TYPE_FUNCTION = "Function";
    private static final String TYPE_INPUTSTREAM = "InputStream";
    private static final String TYPE_FILEINPUTSTREAM = "FileInputStream";

    // Method signatures with parameterized types
    private static final String METHOD_BAR_LIST_OPEN = METHOD_BAR_VOID + "(" + PKG_JAVA_UTIL + TYPE_LIST + "<String> items) {\n";
    private static final String METHOD_BAR_OPTIONAL_OPEN = METHOD_BAR_VOID + "(" + PKG_JAVA_UTIL + TYPE_OPTIONAL + "<String> opt) {\n";
    private static final String METHOD_BAR_THROWS_OPEN = METHOD_BAR_VOID + "() throws Exception {\n";

    // Common assertion messages
    private static final String MSG_SHOULD_PARSE = "Should parse ";
    private static final String MSG_SHOULD_INSTRUMENT = "Should instrument ";
    private static final String MSG_SHOULD_PRESERVE = "Should preserve ";
    private static final String MSG_METHOD = "method";
    private static final String MSG_METHOD_REFERENCES = MSG_METHOD + " references";

    private String instrument(String source) {
        return TestInstrumentationHelper.instrument(source);
    }

    @Test
    public void testRecordsWithMethods() {
        // Java 16+ records
        String source =
            "public record Point(int x, int y) {\n" +
            "    public double distance() {\n" +
            "        return Math.sqrt(x * x + y * y);\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "records", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "record method", result.contains(INC_PATTERN));
    }

    @Test
    public void testSwitchExpressions() {
        // Java 14+ switch expressions
        String source =
            CLASS_FOO_OPEN +
            "    String classify(int x) {\n" +
            "        return switch (x) {\n" +
            "            case 1 -> \"one\";\n" +
            "            case 2 -> \"two\";\n" +
            "            default -> \"other\";\n" +
            "        };\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "switch expressions", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "switch expression method", result.contains(INC_PATTERN));
    }

    @Test
    public void testTextBlocks() {
        // Java 15+ text blocks
        String source =
            CLASS_FOO_OPEN +
            METHOD_BAR_OPEN +
            "        String sql = \"\"\"\n" +
            "            SELECT * FROM users\n" +
            "            WHERE active = true\n" +
            "            \"\"\";\n" +
            "        System.out.println(sql);\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "text blocks", result);
        assertTrue(MSG_SHOULD_PRESERVE + "text block content", result.contains("SELECT * FROM users"));
        assertTrue(MSG_SHOULD_INSTRUMENT + MSG_METHOD, result.contains(INC_PATTERN));
    }

    @Test
    public void testPatternMatchingInstanceof() {
        // Java 16+ pattern matching for instanceof
        String source =
            CLASS_FOO_OPEN +
            METHOD_BAR_OBJECT_OPEN +
            "        if (obj instanceof String s) {\n" +
            "            System.out.println(s.length());\n" +
            INDENT_8_CLOSE +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "pattern matching instanceof", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "method with pattern matching", result.contains(INC_PATTERN));
        assertTrue(MSG_SHOULD_PRESERVE + "pattern variable", result.contains("s.length()"));
    }

    @Test
    public void testSealedClasses() {
        // Java 17 sealed classes
        String source =
            "sealed class Shape permits Circle, Rectangle {\n" +
            METHOD_AREA_OPEN +
            RETURN_0 +
            INDENT_4_CLOSE +
            CLOSE_BRACE_NL +
            "final class Circle extends Shape {\n" +
            "    double radius;\n" +
            METHOD_AREA_OPEN +
            "        return Math.PI * radius * radius;\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE_NL +
            "final class Rectangle extends Shape {\n" +
            "    double width, height;\n" +
            METHOD_AREA_OPEN +
            "        return width * height;\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "sealed classes", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "sealed class methods", result.contains(INC_PATTERN));
    }

    @Test
    public void testVarLocalVariable() {
        // Java 10+ var
        String source =
            CLASS_FOO_OPEN +
            METHOD_BAR_OPEN +
            "        var x = 42;\n" +
            "        var s = \"hello\";\n" +
            "        System.out.println(x + s);\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "var declarations", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "var statements", result.contains(INC_PATTERN));
    }

    @Test
    public void testMultiLineStringConcatenation() {
        // Basic Java but tests preserving complex expressions
        String source =
            CLASS_FOO_OPEN +
            METHOD_BAR_OPEN +
            "        String result = \"Hello\" +\n" +
            "            \" World\" +\n" +
            "            \"!\";\n" +
            "        System.out.println(result);\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull("Should handle multi-line expressions", result);
        assertTrue(MSG_SHOULD_PRESERVE + "string concatenation", result.contains("Hello"));
    }

    @Test
    public void testEnhancedForWithVar() {
        // Java 10+ var in enhanced for
        String source =
            CLASS_FOO_OPEN +
            METHOD_BAR_LIST_OPEN +
            "        for (var item : items) {\n" +
            PRINTLN_ITEM +
            INDENT_8_CLOSE +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "var in for-each", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "loop body", result.contains(INC_PATTERN));
    }

    @Test
    public void testTryWithResourcesMultiple() {
        // Java 9+ effectively final try-with-resources (but we test standard form)
        String source =
            CLASS_FOO_OPEN +
            METHOD_BAR_THROWS_OPEN +
            INDENT_8 + "try (\n" +
            INDENT_12 + PKG_JAVA_IO + TYPE_INPUTSTREAM + " a = new " + PKG_JAVA_IO + TYPE_FILEINPUTSTREAM + "(\"a\");\n" +
            INDENT_12 + PKG_JAVA_IO + TYPE_INPUTSTREAM + " b = new " + PKG_JAVA_IO + TYPE_FILEINPUTSTREAM + "(\"b\")\n" +
            INDENT_8 + ") {\n" +
            INDENT_12 + "a.read();\n" +
            INDENT_12 + "b.read();\n" +
            INDENT_8_CLOSE +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "multi-resource try", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "try-with-resources", result.contains(INC_PATTERN));
    }

    @Test
    public void testMethodReferencesInStream() {
        // Java 8+ method references with streams
        String source =
            CLASS_FOO_OPEN +
            METHOD_BAR_LIST_OPEN +
            "        items.stream()\n" +
            MAP_TO_UPPERCASE +
            "            .forEach(System.out::println);\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + MSG_METHOD_REFERENCES, result);
        assertTrue(MSG_SHOULD_INSTRUMENT + MSG_METHOD, result.contains(INC_PATTERN));
        assertTrue(MSG_SHOULD_PRESERVE + MSG_METHOD_REFERENCES, result.contains(STRING_TO_UPPERCASE));
    }

    @Test
    public void testDiamondOperatorInGenericConstructor() {
        // Java 7+ diamond operator
        String source =
            CLASS_FOO_OPEN +
            METHOD_BAR_OPEN +
            INDENT_8 + PKG_JAVA_UTIL + TYPE_LIST + "<String> list = new " + PKG_JAVA_UTIL + TYPE_ARRAYLIST + DIAMOND_CLOSE +
            INDENT_8 + PKG_JAVA_UTIL + TYPE_MAP + "<String, " + PKG_JAVA_UTIL + TYPE_LIST + "<Integer>> map = new " + PKG_JAVA_UTIL + TYPE_HASHMAP + DIAMOND_CLOSE +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "diamond operator", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "statements", result.contains(INC_PATTERN));
    }

    @Test
    public void testComplexGenericMethodSignatures() {
        // Complex generics that can challenge parsers
        String source =
            CLASS_FOO_OPEN +
            "    <T extends Comparable<? super T>> void sort(" + PKG_JAVA_UTIL + TYPE_LIST + "<T> list) {\n" +
            INDENT_8 + PKG_JAVA_UTIL + TYPE_COLLECTIONS + ".sort(list);\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "complex generic signatures", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "generic " + MSG_METHOD, result.contains(INC_PATTERN));
    }

    @Test
    public void testNestedLambdas() {
        // Nested lambda expressions
        String source =
            CLASS_FOO_OPEN +
            METHOD_BAR_OPEN +
            INDENT_8 + PKG_JAVA_UTIL_FUNCTION + TYPE_FUNCTION + "<Integer, " + PKG_JAVA_UTIL_FUNCTION + TYPE_FUNCTION + "<Integer, Integer>> add = \n" +
            INDENT_12 + "x -> y -> x + y;\n" +
            INDENT_8 + "int result = add.apply(5).apply(3);\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "nested lambdas", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "nested lambda expressions", result.contains(INC_PATTERN));
    }

    @Test
    public void testDefaultMethodsInInterfaces() {
        // Java 8+ default methods
        String source =
            INTERFACE_FOO_OPEN +
            METHOD_BAR_DEFAULT_OPEN +
            INDENT_8 + "System.out.println(\"default\");\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "default interface methods", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "default " + MSG_METHOD, result.contains(INC_PATTERN));
    }

    @Test
    public void testStaticMethodsInInterfaces() {
        // Java 8+ static methods in interfaces
        String source =
            INTERFACE_FOO_OPEN +
            "    static void bar() {\n" +
            INDENT_8 + "System.out.println(\"static\");\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "static interface methods", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "static " + MSG_METHOD, result.contains(INC_PATTERN));
    }

    @Test
    public void testPrivateMethodsInInterfaces() {
        // Java 9+ private methods in interfaces
        String source =
            INTERFACE_FOO_OPEN +
            METHOD_BAR_DEFAULT_OPEN +
            INDENT_8 + "helper();\n" +
            INDENT_4_CLOSE +
            "    private void helper() {\n" +
            INDENT_8 + "System.out.println(\"helper\");\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "private interface methods", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "interface methods", result.contains(INC_PATTERN));
    }

    @Test
    public void testAnnotationsWithComplexValues() {
        // Complex annotation values
        String source =
            CLASS_FOO_OPEN +
            "    @SuppressWarnings({\"unchecked\", \"rawtypes\"})\n" +
            METHOD_BAR_OPEN +
            "        System.out.println(\"test\");\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "annotations with array values", result);
        assertTrue(MSG_SHOULD_PRESERVE + "annotation", result.contains("@SuppressWarnings"));
        assertTrue(MSG_SHOULD_INSTRUMENT + "annotated method", result.contains(INC_PATTERN));
    }

    @Test
    public void testMultiCatchExceptions() {
        // Java 7+ multi-catch
        String source =
            CLASS_FOO_OPEN +
            METHOD_BAR_OPEN +
            "        try {\n" +
            "            System.out.println(\"try\");\n" +
            "        } catch (IllegalArgumentException | IllegalStateException e) {\n" +
            "            e.printStackTrace();\n" +
            INDENT_8_CLOSE +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "multi-catch exceptions", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "try-catch block", result.contains(INC_PATTERN));
    }

    @Test
    public void testUnderscoresInNumericLiterals() {
        // Java 7+ underscores in numeric literals
        String source =
            CLASS_FOO_OPEN +
            METHOD_BAR_OPEN +
            "        int million = 1_000_000;\n" +
            "        long creditCard = 1234_5678_9012_3456L;\n" +
            "        float pi = 3.14_15F;\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "numeric literals with underscores", result);
        assertTrue(MSG_SHOULD_PRESERVE + "underscored literals", result.contains("1_000_000"));
        assertTrue(MSG_SHOULD_INSTRUMENT + MSG_METHOD, result.contains(INC_PATTERN));
    }

    @Test
    public void testComplexStreamOperations() {
        // Java 8+ complex stream pipeline
        String source =
            CLASS_FOO_OPEN +
            METHOD_BAR_LIST_OPEN +
            INDENT_8 + "long count = items.stream()\n" +
            FILTER_LENGTH_GT_5 +
            MAP_TO_UPPERCASE +
            INDENT_12 + ".distinct()\n" +
            INDENT_12 + ".sorted()\n" +
            INDENT_12 + ".count();\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "complex stream operations", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + MSG_METHOD + " with streams", result.contains(INC_PATTERN));
    }

    @Test
    public void testOptionalChaining() {
        // Java 8+ Optional usage
        String source =
            CLASS_FOO_OPEN +
            METHOD_BAR_OPTIONAL_OPEN +
            INDENT_8 + "String result = opt.map(String::toUpperCase)\n" +
            FILTER_LENGTH_GT_5 +
            INDENT_12 + ".orElse(\"default\");\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "Optional chaining", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + MSG_METHOD + " with Optional", result.contains(INC_PATTERN));
    }

    @Test
    public void testComplexConditionalExpression() {
        // Complex nested ternary operators
        String source =
            CLASS_FOO_OPEN +
            "    void bar(int x, int y) {\n" +
            "        String result = x > 0 ? (y > 0 ? \"both positive\" : \"x positive\") : \n" +
            "                        (y > 0 ? \"y positive\" : \"both negative\");\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "nested ternary operators", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "method with complex conditionals", result.contains(INC_PATTERN));
    }

    @Test
    public void testInstanceOfPatternWithComplexExpression() {
        // Pattern matching with complex expressions
        String source =
            CLASS_FOO_OPEN +
            METHOD_BAR_OBJECT_OPEN +
            "        if (obj instanceof String s && s.length() > 5 && s.startsWith(\"test\")) {\n" +
            "            System.out.println(s.toUpperCase());\n" +
            INDENT_8_CLOSE +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "pattern matching with complex conditions", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "pattern matching with boolean logic", result.contains(INC_PATTERN));
    }

    @Test
    @Ignore("CompactConstructorDeclaration not yet supported in AstInstrumenter - JavaParser 3.26.3 parses records but visitor missing")
    public void testRecordWithCompactConstructor() {
        // Java 16+ record with compact constructor
        String source =
            "public record Range(int min, int max) {\n" +
            "    public Range {\n" +
            "        if (min > max) {\n" +
            "            throw new IllegalArgumentException(\"min > max\");\n" +
            INDENT_8_CLOSE +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "record with compact constructor", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "compact constructor", result.contains(INC_PATTERN));
    }

    @Test
    public void testLocalRecordDeclaration() {
        // Java 16+ local record (record defined inside method)
        String source =
            CLASS_FOO_OPEN +
            METHOD_BAR_OPEN +
            "        record Point(int x, int y) {}\n" +
            "        Point p = new Point(1, 2);\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "local record declarations", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "method containing local record", result.contains(INC_PATTERN));
    }

    @Test
    public void testGenericRecordType() {
        // Java 16+ generic records
        String source =
            "public record Pair<T, U>(T first, U second) {\n" +
            "    public boolean hasNulls() {\n" +
            "        return first == null || second == null;\n" +
            INDENT_4_CLOSE +
            CLOSE_BRACE;
        String result = instrument(source);
        assertNotNull(MSG_SHOULD_PARSE + "generic record types", result);
        assertTrue(MSG_SHOULD_INSTRUMENT + "generic record methods", result.contains(INC_PATTERN));
    }
}
