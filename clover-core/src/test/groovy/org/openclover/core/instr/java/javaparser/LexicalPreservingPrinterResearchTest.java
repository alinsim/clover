package org.openclover.core.instr.java.javaparser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Research tests for LexicalPreservingPrinter (LPP) reliability.
 * Tests each AST modification pattern needed for instrumentation.
 */
public class LexicalPreservingPrinterResearchTest {

    private static final String INC_0 = "R.inc(0)";
    private static final String INC_0_STMT = "R.inc(0);";
    private static final String INC_1_STMT = "R.inc(1);";
    private static final String INC_1 = "R.inc(1)";
    private static final String INC_2_STMT = "R.inc(2);";
    private static final String INC_3_STMT = "R.inc(3);";
    private static final String SHOULD_CONTAIN_INC_0 = "Should contain R.inc(0)";
    private static final String DO_SOMETHING = "doSomething()";
    private static final String RETURN_TRUE = "return true";
    private static final String RETURN_FALSE = "return false";
    private static final String GLOBAL_SLICE_START = "globalSliceStart";
    private static final String GLOBAL_SLICE_END = "globalSliceEnd";

    private static final String SIMPLE_CLASS =
            "class Foo {\n"
            + "    void bar() {\n"
            + "        System.out.println(\"hello\");\n"
            + "    }\n"
            + "}";

    @Test
    public void insertStatementAtMethodBodyStart() {
        CompilationUnit cu = parse(SIMPLE_CLASS);
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();
        BlockStmt body = method.getBody().orElseThrow();

        body.getStatements().addFirst(StaticJavaParser.parseStatement(INC_0_STMT));

        String output = print(cu);
        assertTrue(SHOULD_CONTAIN_INC_0, output.contains(INC_0));
        assertTrue("Should preserve original statement", output.contains("System.out.println"));
        assertParseable(output);
    }

    @Test
    public void insertStatementBeforeExisting() {
        String source =
                "class Foo {\n"
                + "    void bar() {\n"
                + "        int x = 1;\n"
                + "        int y = 2;\n"
                + "    }\n"
                + "}";
        CompilationUnit cu = parse(source);
        BlockStmt body = cu.findFirst(MethodDeclaration.class).orElseThrow().getBody().orElseThrow();

        body.getStatements().add(1, StaticJavaParser.parseStatement(INC_0_STMT));

        String output = print(cu);
        assertTrue(SHOULD_CONTAIN_INC_0, output.contains(INC_0));
        assertTrue("R.inc should appear before int y", output.indexOf(INC_0) < output.indexOf("int y = 2"));
        assertParseable(output);
    }

    @Test
    public void addSyntheticElseToIfWithoutElse() {
        String source =
                "class Foo {\n"
                + "    void bar(boolean b) {\n"
                + "        if (b) {\n"
                + "            doSomething();\n"
                + "        }\n"
                + "    }\n"
                + "}";
        CompilationUnit cu = parse(source);
        IfStmt ifStmt = cu.findFirst(IfStmt.class).orElseThrow();

        // LPP bug: setElseStmt() on if-without-else throws UnsupportedOperationException (CsmIndent).
        // Workaround: replace the entire IfStmt with a new one that has the else branch.
        BlockStmt elseBlock = new BlockStmt();
        elseBlock.addStatement(StaticJavaParser.parseStatement(INC_1_STMT));
        IfStmt newIf = new IfStmt(ifStmt.getCondition().clone(), ifStmt.getThenStmt().clone(), elseBlock);
        ifStmt.replace(newIf);

        String output = print(cu);
        assertTrue("Should contain else block", output.contains("else"));
        assertTrue("Should contain R.inc(1) in else", output.contains(INC_1));
        assertTrue("Should preserve then body", output.contains(DO_SOMETHING));
        assertParseable(output);
    }

    @Test
    public void wrapBracelessIfBodyInBlock() {
        String source =
                "class Foo {\n"
                + "    boolean bar(boolean b) {\n"
                + "        if (b) return true;\n"
                + "        return false;\n"
                + "    }\n"
                + "}";
        CompilationUnit cu = parse(source);
        IfStmt ifStmt = cu.findFirst(IfStmt.class).orElseThrow();

        Statement thenStmt = ifStmt.getThenStmt();
        BlockStmt block = new BlockStmt();
        block.addStatement(StaticJavaParser.parseStatement(INC_0_STMT));
        block.addStatement(thenStmt.clone());
        ifStmt.setThenStmt(block);

        String output = print(cu);
        assertTrue(SHOULD_CONTAIN_INC_0, output.contains(INC_0));
        assertTrue("Should contain return true", output.contains(RETURN_TRUE));
        assertTrue("Should contain return false", output.contains(RETURN_FALSE));
        assertParseable(output);
    }

    @Test
    public void addInnerClassMember() {
        CompilationUnit cu = parse(SIMPLE_CLASS);
        ClassOrInterfaceDeclaration clazz = cu.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();

        ClassOrInterfaceDeclaration recorder = new ClassOrInterfaceDeclaration();
        recorder.setName("__CLR");
        recorder.addModifier(Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC);
        clazz.addMember(recorder);

        String output = print(cu);
        assertTrue("Should contain inner class __CLR", output.contains("__CLR"));
        assertTrue("Should preserve original method", output.contains("void bar()"));
        assertParseable(output);
    }

    @Test
    public void wrapMethodBodyInTryCatchFinally() {
        String source =
                "class Foo {\n"
                + "    void testSomething() {\n"
                + "        doTest();\n"
                + "        verify();\n"
                + "    }\n"
                + "}";
        CompilationUnit cu = parse(source);
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();
        BlockStmt body = method.getBody().orElseThrow();

        NodeList<Statement> originalStmts = new NodeList<>(body.getStatements());
        body.getStatements().clear();

        BlockStmt tryBlock = new BlockStmt();
        tryBlock.addStatement(StaticJavaParser.parseStatement("R.globalSliceStart(\"test\", 0);"));
        for (Statement stmt : originalStmts) {
            tryBlock.addStatement(stmt.clone());
        }

        BlockStmt catchBlock = new BlockStmt();
        catchBlock.addStatement(StaticJavaParser.parseStatement("throw __t;"));
        CatchClause catchClause = new CatchClause(
                new com.github.javaparser.ast.body.Parameter(
                        new ClassOrInterfaceType(null, "Throwable"), "__t"),
                catchBlock);

        BlockStmt finallyBlock = new BlockStmt();
        finallyBlock.addStatement(StaticJavaParser.parseStatement("R.globalSliceEnd(\"test\", 0);"));

        TryStmt tryStmt = new TryStmt();
        tryStmt.setTryBlock(tryBlock);
        tryStmt.setCatchClauses(new NodeList<>(catchClause));
        tryStmt.setFinallyBlock(finallyBlock);

        body.addStatement(tryStmt);

        String output = print(cu);
        assertTrue("Should contain globalSliceStart", output.contains(GLOBAL_SLICE_START));
        assertTrue("Should contain globalSliceEnd", output.contains(GLOBAL_SLICE_END));
        assertTrue("Should contain original doTest()", output.contains("doTest()"));
        assertTrue("Should contain catch Throwable", output.contains("Throwable"));
        assertTrue("Should contain finally", output.contains("finally"));
        assertParseable(output);
    }

    @Test
    public void replaceExpressionWithWrappingCall() {
        String source =
                "class Foo {\n"
                + "    java.util.function.Supplier<String> s = () -> \"hello\";\n"
                + "}";
        CompilationUnit cu = parse(source);

        cu.findFirst(LambdaExpr.class).ifPresent(lambda -> {
            MethodCallExpr wrapper = new MethodCallExpr(
                    new NameExpr("R"),
                    "lambdaInc",
                    new NodeList<>(
                            new IntegerLiteralExpr("0"),
                            lambda.clone(),
                            new NameExpr("R")));
            lambda.replace(wrapper);
        });

        String output = print(cu);
        assertTrue("Should contain lambdaInc wrapping", output.contains("lambdaInc"));
        assertTrue("Should contain original lambda body", output.contains("hello"));
        assertParseable(output);
    }

    @Test
    public void preserveUnmodifiedCode() {
        String source =
                "package com.example;\n"
                + "\n"
                + "import java.util.List;\n"
                + "\n"
                + "/**\n"
                + " * A class with various formatting.\n"
                + " */\n"
                + "public class Foo {\n"
                + "    // single line comment\n"
                + "    private int x = 1;\n"
                + "\n"
                + "    /* multi-line\n"
                + "       comment */\n"
                + "    public void bar(int a,\n"
                + "                    int b) {\n"
                + "        if (a > b) {\n"
                + "            System.out.println(a);\n"
                + "        } else {\n"
                + "            System.out.println(b);\n"
                + "        }\n"
                + "    }\n"
                + "}\n";

        CompilationUnit cu = parse(source);
        String output = print(cu);

        assertTrue("Package preserved", output.contains("package com.example;"));
        assertTrue("Import preserved", output.contains("import java.util.List;"));
        assertTrue("Javadoc preserved", output.contains("A class with various formatting."));
        assertTrue("Comment preserved", output.contains("// single line comment"));
        assertTrue("Multi-line comment preserved", output.contains("multi-line"));
        assertParseable(output);
    }

    @Test
    public void multipleModificationsToSameMethod() {
        String source =
                "class Foo {\n"
                + "    void bar(boolean flag) {\n"
                + "        if (flag) {\n"
                + "            doA();\n"
                + "        }\n"
                + "        doB();\n"
                + "    }\n"
                + "}";
        CompilationUnit cu = parse(source);
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class).orElseThrow();
        BlockStmt body = method.getBody().orElseThrow();

        body.getStatements().addFirst(StaticJavaParser.parseStatement(INC_0_STMT));

        // LPP workaround: add R.inc(1) to then block directly (this works),
        // then replace entire IfStmt to add else branch (setElseStmt doesn't work with LPP)
        IfStmt ifStmt = cu.findFirst(IfStmt.class).orElseThrow();
        ifStmt.getThenStmt().asBlockStmt().getStatements().addFirst(
                StaticJavaParser.parseStatement(INC_1_STMT));
        // Now replace to add else — must re-read the modified then block
        String modifiedIf = "if (" + ifStmt.getCondition() + ") " + ifStmt.getThenStmt()
                + " else { R.inc(2); }";
        Statement replacement = StaticJavaParser.parseStatement(modifiedIf);
        ifStmt.replace(replacement);

        int doBIndex = -1;
        for (int i = 0; i < body.getStatements().size(); i++) {
            if (body.getStatement(i).toString().contains("doB")) {
                doBIndex = i;
                break;
            }
        }
        if (doBIndex >= 0) {
            body.getStatements().add(doBIndex, StaticJavaParser.parseStatement(INC_3_STMT));
        }

        String output = print(cu);
        assertTrue("Method entry R.inc(0)", output.contains("R.inc(0)"));
        assertTrue("Branch true R.inc(1)", output.contains(INC_1));
        assertTrue("Branch false R.inc(2)", output.contains("R.inc(2)"));
        assertTrue("Statement R.inc(3)", output.contains("R.inc(3)"));
        assertTrue("Original doA preserved", output.contains("doA()"));
        assertTrue("Original doB preserved", output.contains("doB()"));
        assertParseable(output);
    }

    private CompilationUnit parse(String source) {
        CompilationUnit cu = StaticJavaParser.parse(source);
        LexicalPreservingPrinter.setup(cu);
        return cu;
    }

    private String print(CompilationUnit cu) {
        return LexicalPreservingPrinter.print(cu);
    }

    private void assertParseable(String source) {
        try {
            StaticJavaParser.parse(source);
        } catch (Exception e) {
            throw new AssertionError("Output is not parseable Java:\n" + source, e);
        }
    }
}
