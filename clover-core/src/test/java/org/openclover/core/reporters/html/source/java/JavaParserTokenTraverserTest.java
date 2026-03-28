package org.openclover.core.reporters.html.source.java;

import org.junit.Test;
import org.openclover.core.util.UnicodeDecodingReader;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test suite for JavaTokenTraverser using JavaParser tokenizer.
 * Tests cover syntax highlighting for HTML reports including keywords, comments,
 * string literals, javadoc tags, package/import tracking, and modern Java features.
 */
@SuppressWarnings("StringOperationCanBeSimplified")
public class JavaParserTokenTraverserTest {

    // Common string literals used in tests
    private static final String PACKAGE = "package";
    private static final String IMPORT = "import";
    private static final String PUBLIC = "public";
    private static final String STATIC = "static";
    private static final String FINAL = "final";
    private static final String CLASS = "class";
    private static final String EXTENDS = "extends";
    private static final String STRING = "String";
    private static final String INT = "int";
    private static final String CHAR = "char";
    private static final String RECORD = "record";
    private static final String COM = "com";
    private static final String JAVA = "java";
    private static final String JAVA_UTIL = "java.util";
    private static final String JAVADOC_START = "/**";
    private static final String COMMENT_END = "*/";
    private static final String AUTHOR_TAG = "@author";
    private static final String PARAM_TAG = "@param";
    private static final String LINK_TAG = "@link";
    private static final String SPACE = " ";
    private static final String DOT = ".";
    private static final String SEMICOLON = ";";
    private static final String EQUALS = "=";
    private static final String PLUS = "+";
    private static final String AT = "@";
    private static final String LBRACE = "{";
    private static final String RBRACE = "}";
    private static final String LPAREN = "(";
    private static final String RPAREN = ")";
    private static final String COMMA = ",";
    private static final String UTIL = "util";
    private static final String OVERRIDE = "Override";
    private static final String DOT_LIST = ".List";
    private static final String DOT_ARRAY_LIST = ".ArrayList";

    // Helper classes to represent expected tokens
    private interface ExpectedToken {
        String text();
    }

    private static class Keyword implements ExpectedToken {
        private final String text;
        public Keyword(String text) { this.text = text; }
        @Override public String text() { return text; }
    }

    private static class StringLiteral implements ExpectedToken {
        private final String text;
        public StringLiteral(String text) { this.text = text; }
        @Override public String text() { return text; }
    }

    private static class Comment implements ExpectedToken {
        private final String text;
        public Comment(String text) { this.text = text; }
        @Override public String text() { return text; }
    }

    private static class JavadocTag implements ExpectedToken {
        private final String text;
        public JavadocTag(String text) { this.text = text; }
        @Override public String text() { return text; }
    }

    private static class Identifier implements ExpectedToken {
        private final String text;
        public Identifier(String text) { this.text = text; }
        @Override public String text() { return text; }
    }

    private static class Chunk implements ExpectedToken {
        private final String text;
        public Chunk(String text) { this.text = text; }
        @Override public String text() { return text; }
    }

    private static class NewLine implements ExpectedToken {
        @Override public String text() { return "\n"; }
    }

    private static class PackageSegment implements ExpectedToken {
        private final String accum;
        private final String seg;
        public PackageSegment(String accum, String seg) {
            this.accum = accum;
            this.seg = seg;
        }
        @Override public String text() { return seg; }
        public String accum() { return accum; }
    }

    private static class ImportSegment implements ExpectedToken {
        private final String accum;
        private final String seg;
        public ImportSegment(String accum, String seg) {
            this.accum = accum;
            this.seg = seg;
        }
        @Override public String text() { return seg; }
        public String accum() { return accum; }
    }

    private static class Import implements ExpectedToken {
        private final String accum;
        public Import(String accum) { this.accum = accum; }
        @Override public String text() { return accum; }
        public String accum() { return accum; }
    }

    /**
     * Recording listener that captures all callback invocations.
     */
    private static class RecordingListener implements JavaSourceListener {
        private final List<ExpectedToken> recorded = new ArrayList<>();
        private boolean startDocumentCalled = false;
        private boolean endDocumentCalled = false;

        @Override
        public void onStartDocument() {
            startDocumentCalled = true;
        }

        @Override
        public void onEndDocument() {
            endDocumentCalled = true;
        }

        @Override
        public void onNewLine() {
            recorded.add(new NewLine());
        }

        @Override
        public void onChunk(String s) {
            recorded.add(new Chunk(s));
        }

        @Override
        public void onPackageSegment(String packageName, String seg) {
            recorded.add(new PackageSegment(packageName, seg));
        }

        @Override
        public void onImportSegment(String importedName, String seg) {
            recorded.add(new ImportSegment(importedName, seg));
        }

        @Override
        public void onImport(String accum) {
            recorded.add(new Import(accum));
        }

        @Override
        public void onIdentifier(String ident) {
            recorded.add(new Identifier(ident));
        }

        @Override
        public void onStringLiteral(String s) {
            recorded.add(new StringLiteral(s));
        }

        @Override
        public void onKeyword(String s) {
            recorded.add(new Keyword(s));
        }

        @Override
        public void onCommentChunk(String s) {
            recorded.add(new Comment(s));
        }

        @Override
        public void onJavadocTag(String s) {
            recorded.add(new JavadocTag(s));
        }

        public List<ExpectedToken> getRecorded() {
            return recorded;
        }

        public boolean isStartDocumentCalled() {
            return startDocumentCalled;
        }

        public boolean isEndDocumentCalled() {
            return endDocumentCalled;
        }
    }

    /**
     * Helper method to traverse source and verify expected callbacks.
     */
    private void checkRendering(String source, List<ExpectedToken> expected) throws Exception {
        RecordingListener listener = new RecordingListener();
        new JavaTokenTraverser().traverse(
            new UnicodeDecodingReader(new StringReader(source)),
            null,
            listener
        );

        assertTrue("onStartDocument should be called", listener.isStartDocumentCalled());
        assertTrue("onEndDocument should be called", listener.isEndDocumentCalled());

        List<ExpectedToken> actual = listener.getRecorded();
        assertEquals("Number of tokens mismatch", expected.size(), actual.size());

        for (int i = 0; i < expected.size(); i++) {
            ExpectedToken exp = expected.get(i);
            ExpectedToken act = actual.get(i);

            assertEquals(
                String.format("Token %d type mismatch", i + 1),
                exp.getClass(),
                act.getClass()
            );

            assertEquals(
                String.format("Token %d value mismatch", i + 1),
                exp.text(),
                act.text()
            );

            // For PackageSegment and ImportSegment, also check accumulator
            if (exp instanceof PackageSegment && act instanceof PackageSegment) {
                PackageSegment expPkg = (PackageSegment) exp;
                PackageSegment actPkg = (PackageSegment) act;
                assertEquals(
                    String.format("Token %d package accumulator mismatch", i + 1),
                    expPkg.accum(),
                    actPkg.accum()
                );
            }
            if (exp instanceof ImportSegment && act instanceof ImportSegment) {
                ImportSegment expImp = (ImportSegment) exp;
                ImportSegment actImp = (ImportSegment) act;
                assertEquals(
                    String.format("Token %d import accumulator mismatch", i + 1),
                    expImp.accum(),
                    actImp.accum()
                );
            }
            if (exp instanceof Import && act instanceof Import) {
                Import expImport = (Import) exp;
                Import actImport = (Import) act;
                assertEquals(
                    String.format("Token %d import name mismatch", i + 1),
                    expImport.accum(),
                    actImport.accum()
                );
            }
        }
    }

    @Test
    public void traverseBasicSourceRendering() throws Exception {
        String source =
            PACKAGE + " " + COM + ".foo.bar;\n" +
            JAVADOC_START + " " + AUTHOR_TAG + " Harry */\n" +
            PUBLIC + " " + CLASS + " Foo " + EXTENDS + " Bar {\n" +
            "\n" +
            PUBLIC + " " + STATIC + " " + FINAL + " " + STRING + " = \"A String\";\n" +
            "}\n";

        List<ExpectedToken> expected = List.of(
            new Keyword(PACKAGE),
            new Chunk(SPACE),
            new PackageSegment(COM, COM),
            new Chunk(DOT),
            new PackageSegment(COM + ".foo", "foo"),
            new Chunk(DOT),
            new PackageSegment(COM + ".foo.bar", "bar"),
            new Chunk(SEMICOLON),
            new NewLine(),
            new Comment(JAVADOC_START + SPACE),
            new JavadocTag(AUTHOR_TAG),
            new Comment(" Harry */"),
            new NewLine(),
            new Keyword(PUBLIC),
            new Chunk(SPACE),
            new Keyword(CLASS),
            new Chunk(SPACE),
            new Identifier("Foo"),
            new Chunk(SPACE),
            new Keyword(EXTENDS),
            new Chunk(SPACE),
            new Identifier("Bar"),
            new Chunk(SPACE),
            new Chunk(LBRACE),
            new NewLine(),
            new NewLine(),
            new Keyword(PUBLIC),
            new Chunk(SPACE),
            new Keyword(STATIC),
            new Chunk(SPACE),
            new Keyword(FINAL),
            new Chunk(SPACE),
            new Identifier(STRING),
            new Chunk(SPACE),
            new Chunk(EQUALS),
            new Chunk(SPACE),
            new StringLiteral("\"A String\""),
            new Chunk(SEMICOLON),
            new NewLine(),
            new Chunk(RBRACE),
            new NewLine()
        );

        checkRendering(source, expected);
    }

    @Test
    public void traverseNewLineHandlingInComments() throws Exception {
        List<ExpectedToken> expected = List.of(
            new Comment(JAVADOC_START),
            new NewLine(),
            new Comment("*"),
            new NewLine(),
            new Comment(COMMENT_END)
        );

        // Test all newline variations
        checkRendering(JAVADOC_START + "\n*\n" + COMMENT_END, expected);
        checkRendering(JAVADOC_START + "\r\n*\r\n" + COMMENT_END, expected);
        checkRendering(JAVADOC_START + "\r*\r" + COMMENT_END, expected);
    }

    @Test
    public void traverseStringLiteral() throws Exception {
        String source = STRING + " s = \"hello world\";";

        List<ExpectedToken> expected = List.of(
            new Identifier(STRING),
            new Chunk(SPACE),
            new Identifier("s"),
            new Chunk(SPACE),
            new Chunk(EQUALS),
            new Chunk(SPACE),
            new StringLiteral("\"hello world\""),
            new Chunk(SEMICOLON)
        );

        checkRendering(source, expected);
    }

    @Test
    public void traverseSingleLineComment() throws Exception {
        String source = "// this is a comment\n";

        List<ExpectedToken> expected = List.of(
            new Comment("// this is a comment"),
            new NewLine()
        );

        checkRendering(source, expected);
    }

    @Test
    public void traverseMultiLineComment() throws Exception {
        String source = "/* hello\nworld */";

        List<ExpectedToken> expected = List.of(
            new Comment("/* hello"),
            new NewLine(),
            new Comment("world */")
        );

        checkRendering(source, expected);
    }

    @Test
    @SuppressWarnings("DuplicateStringLiteralInspection")
    public void traverseJavadocWithTags() throws Exception {
        String source = JAVADOC_START + " " + PARAM_TAG + " name the name */";

        List<ExpectedToken> expected = List.of(
            new Comment(JAVADOC_START + SPACE),
            new JavadocTag(PARAM_TAG),
            new Comment(" name the name */")
        );

        checkRendering(source, expected);
    }

    @Test
    public void traversePackageAndImports() throws Exception {
        String source =
            PACKAGE + " " + COM + ".example;\n" +
            IMPORT + " " + JAVA + ".util.List;\n" +
            IMPORT + " " + JAVA + ".util.ArrayList;\n";

        List<ExpectedToken> expected = List.of(
            new Keyword(PACKAGE),
            new Chunk(SPACE),
            new PackageSegment(COM, COM),
            new Chunk(DOT),
            new PackageSegment(COM + ".example", "example"),
            new Chunk(SEMICOLON),
            new NewLine(),
            new Keyword(IMPORT),
            new Chunk(SPACE),
            new ImportSegment(JAVA, JAVA),
            new Chunk(DOT),
            new ImportSegment(JAVA_UTIL, UTIL),
            new Chunk(DOT),
            new ImportSegment(JAVA_UTIL + DOT_LIST, "List"),
            new Chunk(SEMICOLON),
            new Import(JAVA_UTIL + DOT_LIST),
            new NewLine(),
            new Keyword(IMPORT),
            new Chunk(SPACE),
            new ImportSegment(JAVA, JAVA),
            new Chunk(DOT),
            new ImportSegment(JAVA_UTIL, UTIL),
            new Chunk(DOT),
            new ImportSegment(JAVA_UTIL + DOT_ARRAY_LIST, "ArrayList"),
            new Chunk(SEMICOLON),
            new Import(JAVA_UTIL + DOT_ARRAY_LIST),
            new NewLine()
        );

        checkRendering(source, expected);
    }

    @Test
    public void traverseIdentifiers() throws Exception {
        String source = "myVariable = someMethod();";

        List<ExpectedToken> expected = List.of(
            new Identifier("myVariable"),
            new Chunk(SPACE),
            new Chunk(EQUALS),
            new Chunk(SPACE),
            new Identifier("someMethod"),
            new Chunk(LPAREN),
            new Chunk(RPAREN),
            new Chunk(SEMICOLON)
        );

        checkRendering(source, expected);
    }

    @Test
    public void traverseCharacterLiteralAsStringLiteral() throws Exception {
        String source = CHAR + " c = 'x';";

        List<ExpectedToken> expected = List.of(
            new Keyword(CHAR),
            new Chunk(SPACE),
            new Identifier("c"),
            new Chunk(SPACE),
            new Chunk(EQUALS),
            new Chunk(SPACE),
            new StringLiteral("'x'"),  // Character literal treated as string literal
            new Chunk(SEMICOLON)
        );

        checkRendering(source, expected);
    }

    @Test
    public void traverseTextBlockAsStringLiteral() throws Exception {
        String source = STRING + " text = \"\"\"\n    hello\n    world\n    \"\"\";";

        List<ExpectedToken> expected = List.of(
            new Identifier(STRING),
            new Chunk(SPACE),
            new Identifier("text"),
            new Chunk(SPACE),
            new Chunk(EQUALS),
            new Chunk(SPACE),
            new StringLiteral("\"\"\"\n    hello\n    world\n    \"\"\""),  // Text block as string literal
            new Chunk(SEMICOLON)
        );

        checkRendering(source, expected);
    }

    @Test
    public void traverseModernKeywords() throws Exception {
        String source = RECORD + " Point(" + INT + " x, " + INT + " y) {}";

        List<ExpectedToken> expected = List.of(
            new Keyword(RECORD),
            new Chunk(SPACE),
            new Identifier("Point"),
            new Chunk(LPAREN),
            new Keyword(INT),
            new Chunk(SPACE),
            new Identifier("x"),
            new Chunk(COMMA),
            new Chunk(SPACE),
            new Keyword(INT),
            new Chunk(SPACE),
            new Identifier("y"),
            new Chunk(RPAREN),
            new Chunk(SPACE),
            new Chunk(LBRACE),
            new Chunk(RBRACE)
        );

        checkRendering(source, expected);
    }

    @Test
    public void traverseNumericLiterals() throws Exception {
        String source = INT + " x = 42;";

        List<ExpectedToken> expected = List.of(
            new Keyword(INT),
            new Chunk(SPACE),
            new Identifier("x"),
            new Chunk(SPACE),
            new Chunk(EQUALS),
            new Chunk(SPACE),
            new Chunk("42"),  // Numeric literal as chunk
            new Chunk(SEMICOLON)
        );

        checkRendering(source, expected);
    }

    @Test
    public void traverseAnnotation() throws Exception {
        String source = AT + OVERRIDE;

        List<ExpectedToken> expected = List.of(
            new Chunk(AT),
            new Identifier(OVERRIDE)
        );

        checkRendering(source, expected);
    }

    @Test
    public void traverseOperators() throws Exception {
        String source = "a + b";

        List<ExpectedToken> expected = List.of(
            new Identifier("a"),
            new Chunk(SPACE),
            new Chunk(PLUS),
            new Chunk(SPACE),
            new Identifier("b")
        );

        checkRendering(source, expected);
    }

    @Test
    public void traverseEmptySource() throws Exception {
        String source = "";
        List<ExpectedToken> expected = List.of();
        checkRendering(source, expected);
    }

    @Test
    @SuppressWarnings("DuplicateStringLiteralInspection")
    public void traverseComplexJavadocCCD339() throws Exception {
        // Test 1: @notatag should not be recognized as a tag
        String source1 = JAVADOC_START + "\n*@notatag\n" + COMMENT_END;
        List<ExpectedToken> expected1 = List.of(
            new Comment(JAVADOC_START),
            new NewLine(),
            new Comment("*"),
            new Comment("@notatag"),
            new NewLine(),
            new Comment(COMMENT_END)
        );
        checkRendering(source1, expected1);

        // Test 2: @author with email address
        String source2 = JAVADOC_START + "\n*" + AUTHOR_TAG + " <a href=\"mailto:harry@highpants.com\">Harry HighPants</a>\n" + COMMENT_END;
        List<ExpectedToken> expected2 = List.of(
            new Comment(JAVADOC_START),
            new NewLine(),
            new Comment("*"),
            new JavadocTag(AUTHOR_TAG),
            new Comment(" <a href=\"mailto:harry"),
            new Comment("@highpants.com\">Harry HighPants</a>"),
            new NewLine(),
            new Comment(COMMENT_END)
        );
        checkRendering(source2, expected2);

        // Test 3: Multiple @link tags (from checkstyle-4.2)
        String source3 =
            JAVADOC_START + "\n" +
            " * <p>\n" +
            " * Checks the placement of left curly braces on types, methods and\n" +
            " * other blocks:\n" +
            " *  {" + LINK_TAG + " TokenTypes#LITERAL_CATCH LITERAL_CATCH},  {" + LINK_TAG + "\n" +
            " * TokenTypes#LITERAL_DO LITERAL_DO},  {" + LINK_TAG + " TokenTypes#LITERAL_ELSE\n" +
            " * LITERAL_ELSE},  {" + LINK_TAG + " TokenTypes#LITERAL_FINALLY LITERAL_FINALLY},  {" + LINK_TAG + "\n" +
            " * TokenTypes#LITERAL_FOR LITERAL_FOR},  {" + LINK_TAG + " TokenTypes#LITERAL_IF\n" +
            " * LITERAL_IF},  {" + LINK_TAG + " TokenTypes#LITERAL_SWITCH LITERAL_SWITCH},  {\n" +
            " */";

        List<ExpectedToken> expected3 = List.of(
            new Comment(JAVADOC_START),
            new NewLine(),
            new Comment(" * <p>"),
            new NewLine(),
            new Comment(" * Checks the placement of left curly braces on types, methods and"),
            new NewLine(),
            new Comment(" * other blocks:"),
            new NewLine(),
            new Comment(" *  {"),
            new JavadocTag(LINK_TAG),
            new Comment(" TokenTypes#LITERAL_CATCH LITERAL_CATCH},  {"),
            new JavadocTag(LINK_TAG),
            new NewLine(),
            new Comment(" * TokenTypes#LITERAL_DO LITERAL_DO},  {"),
            new JavadocTag(LINK_TAG),
            new Comment(" TokenTypes#LITERAL_ELSE"),
            new NewLine(),
            new Comment(" * LITERAL_ELSE},  {"),
            new JavadocTag(LINK_TAG),
            new Comment(" TokenTypes#LITERAL_FINALLY LITERAL_FINALLY},  {"),
            new JavadocTag(LINK_TAG),
            new NewLine(),
            new Comment(" * TokenTypes#LITERAL_FOR LITERAL_FOR},  {"),
            new JavadocTag(LINK_TAG),
            new Comment(" TokenTypes#LITERAL_IF"),
            new NewLine(),
            new Comment(" * LITERAL_IF},  {"),
            new JavadocTag(LINK_TAG),
            new Comment(" TokenTypes#LITERAL_SWITCH LITERAL_SWITCH},  {"),
            new NewLine(),
            new Comment(" */")
        );
        checkRendering(source3, expected3);
    }

    @Test
    public void traverseUnicodeCharacters() throws Exception {
        // Test extended Unicode character (emoji: shining sun 0x0001F31E)
        String source = STRING + " s = \"\u0001\uF31E\";";

        List<ExpectedToken> expected = List.of(
            new Identifier(STRING),
            new Chunk(SPACE),
            new Identifier("s"),
            new Chunk(SPACE),
            new Chunk(EQUALS),
            new Chunk(SPACE),
            new StringLiteral("\"\u0001\uF31E\""),
            new Chunk(SEMICOLON)
        );

        checkRendering(source, expected);
    }
}
