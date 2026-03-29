package org.openclover.core.instr.java.javaparser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AST-based Java source instrumenter using LexicalPreservingPrinter.
 *
 * Replaces the text-based Insertion+SourceRewriter approach with direct AST modification.
 * The tree structure enforces correct scoping — insertion ordering bugs are structurally impossible.
 */
public class AstInstrumenter {

    private static final String MARKER_COMMENT = "/* $$ This file has been instrumented by OpenClover $$ */";

    private AstInstrumenter() {}

    /**
     * Instrument a Java source string. Returns instrumented source.
     */
    public static String instrument(String source, String recorderPrefix, String initString) {
        CompilationUnit cu = StaticJavaParser.parse(source);
        LexicalPreservingPrinter.setup(cu);

        AtomicInteger indexCounter = new AtomicInteger(0);

        // Visit all classes and instrument their members
        cu.accept(new InstrumentationVisitor(recorderPrefix, indexCounter), null);

        // Inject recorder inner class into each top-level type
        for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (clazz.isTopLevelType()) {
                injectRecorderClass(clazz, recorderPrefix, initString, indexCounter.get());
            }
        }

        // Prepend instrumentation marker
        String output = LexicalPreservingPrinter.print(cu);
        return MARKER_COMMENT + "\n" + output;
    }

    private static void injectRecorderClass(ClassOrInterfaceDeclaration clazz,
                                            String recorderPrefix, String initString, int maxDataIndex) {
        String recorderCode = String.format(
                "public static class %s {"
                + "public static final int[] D = new int[%d];"
                + "public static void inc(int i) { D[i]++; }"
                + "}",
                recorderPrefix, maxDataIndex + 1);
        clazz.addMember(StaticJavaParser.parseBodyDeclaration(recorderCode));
    }

    /**
     * Visitor that instruments methods, statements, and branches via AST modification.
     */
    private static class InstrumentationVisitor extends VoidVisitorAdapter<Void> {

        private final String prefix;
        private final AtomicInteger indexCounter;

        InstrumentationVisitor(String prefix, AtomicInteger indexCounter) {
            this.prefix = prefix;
            this.indexCounter = indexCounter;
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            method.getBody().ifPresent(body -> {
                // Method entry
                int methodIndex = indexCounter.getAndIncrement();
                body.getStatements().addFirst(parseInc(methodIndex));

                // Instrument statements and branches inside the body
                instrumentBlock(body);
            });
            // Don't call super.visit — we handle children manually in instrumentBlock
        }

        @Override
        public void visit(ConstructorDeclaration ctor, Void arg) {
            BlockStmt body = ctor.getBody();
            int ctorIndex = indexCounter.getAndIncrement();

            // Insert after explicit constructor invocation (super/this) if present
            List<Statement> stmts = body.getStatements();
            int insertPos = 0;
            if (!stmts.isEmpty() && stmts.get(0) instanceof ExplicitConstructorInvocationStmt) {
                insertPos = 1;
            }
            body.getStatements().add(insertPos, parseInc(ctorIndex));

            instrumentBlock(body);
        }

        private void instrumentBlock(BlockStmt block) {
            // Iterate statements — instrument each one, handle branches
            // We iterate by index because we're inserting new statements
            int i = 0;
            while (i < block.getStatements().size()) {
                Statement stmt = block.getStatement(i);

                if (stmt instanceof IfStmt) {
                    instrumentIf((IfStmt) stmt);
                    i++; // The if itself stays at position i
                } else if (stmt instanceof WhileStmt) {
                    instrumentLoopBody(((WhileStmt) stmt).getBody());
                    i++;
                } else if (stmt instanceof ForStmt) {
                    instrumentLoopBody(((ForStmt) stmt).getBody());
                    i++;
                } else if (stmt instanceof DoStmt) {
                    instrumentLoopBody(((DoStmt) stmt).getBody());
                    i++;
                } else if (stmt instanceof ForEachStmt) {
                    instrumentLoopBody(((ForEachStmt) stmt).getBody());
                    i++;
                } else if (stmt instanceof BlockStmt) {
                    instrumentBlock((BlockStmt) stmt);
                    i++;
                } else if (isExecutableStatement(stmt)) {
                    // Insert R.inc(N) before this statement
                    int stmtIndex = indexCounter.getAndIncrement();
                    block.getStatements().add(i, parseInc(stmtIndex));
                    i += 2; // Skip both the inc and the original statement
                } else {
                    i++;
                }
            }
        }

        private void instrumentIf(IfStmt ifStmt) {
            // Allocate branch pair: N = true, N+1 = false
            int trueIndex = indexCounter.getAndIncrement();
            int falseIndex = indexCounter.getAndIncrement();

            // True branch
            Statement thenStmt = ifStmt.getThenStmt();
            if (thenStmt instanceof BlockStmt) {
                BlockStmt thenBlock = (BlockStmt) thenStmt;
                thenBlock.getStatements().addFirst(parseInc(trueIndex));
                instrumentBlock(thenBlock);
            } else {
                // Braceless: wrap in block
                BlockStmt wrapper = new BlockStmt();
                wrapper.addStatement(parseInc(trueIndex));
                wrapper.addStatement(thenStmt.clone());
                ifStmt.setThenStmt(wrapper);
            }

            // False branch
            if (ifStmt.getElseStmt().isPresent()) {
                Statement elseStmt = ifStmt.getElseStmt().get();
                if (elseStmt instanceof BlockStmt) {
                    BlockStmt elseBlock = (BlockStmt) elseStmt;
                    elseBlock.getStatements().addFirst(parseInc(falseIndex));
                    instrumentBlock(elseBlock);
                } else if (elseStmt instanceof IfStmt) {
                    // else-if chain: add false inc before the nested if
                    BlockStmt wrapper = new BlockStmt();
                    wrapper.addStatement(parseInc(falseIndex));
                    wrapper.addStatement(elseStmt.clone());
                    ifStmt.setElseStmt(wrapper);
                    // Recurse into the nested if
                    instrumentIf((IfStmt) wrapper.getStatement(1));
                } else {
                    BlockStmt wrapper = new BlockStmt();
                    wrapper.addStatement(parseInc(falseIndex));
                    wrapper.addStatement(elseStmt.clone());
                    ifStmt.setElseStmt(wrapper);
                }
            } else {
                // No else: LPP workaround — replace entire IfStmt
                BlockStmt syntheticElse = new BlockStmt();
                syntheticElse.addStatement(parseInc(falseIndex));
                IfStmt newIf = new IfStmt(
                        ifStmt.getCondition().clone(),
                        ifStmt.getThenStmt().clone(),
                        syntheticElse);
                ifStmt.replace(newIf);
            }
        }

        private void instrumentLoopBody(Statement body) {
            int branchIndex = indexCounter.getAndIncrement();
            if (body instanceof BlockStmt) {
                BlockStmt block = (BlockStmt) body;
                block.getStatements().addFirst(parseInc(branchIndex));
                instrumentBlock(block);
            }
            // Braceless loop bodies are already wrapped by the braceless-body pass
        }

        private Statement parseInc(int index) {
            return StaticJavaParser.parseStatement(prefix + ".inc(" + index + ");");
        }

        private boolean isExecutableStatement(Statement stmt) {
            return stmt.isExpressionStmt()
                    || stmt.isReturnStmt()
                    || stmt.isThrowStmt()
                    || stmt.isAssertStmt()
                    || stmt.isBreakStmt()
                    || stmt.isContinueStmt();
        }
    }
}
