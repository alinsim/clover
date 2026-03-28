package org.openclover.core.instr.java.javaparser;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Position;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.AssertStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JavaParser-based instrumenter for OpenClover.
 * <p>
 * This is a proof-of-concept implementation that demonstrates using JavaParser
 * instead of ANTLR 2 for Java source instrumentation. It parses Java source,
 * walks the AST to identify instrumentation points, and uses {@link SourceRewriter}
 * to apply the modifications.
 * </p>
 * <p>
 * Currently instruments:
 * <ul>
 *     <li>Class-level recorder placeholder (static inner class for coverage recording)</li>
 *     <li>Method entry tracking ({@code R.inc(N)} at each method start)</li>
 *     <li>Constructor entry tracking ({@code R.inc(N)} at each constructor start)</li>
 * </ul>
 * </p>
 */
public class JavaParserInstrumenter {


    /**
     * Instruments the given Java source code, adding coverage recording calls.
     *
     * @param sourceCode      the Java source to instrument
     * @param recorderPrefix  the prefix for the coverage recorder (e.g. "__CLR4_1_100hckkb3w8.R")
     * @param initString      the Clover init string (path to coverage database)
     * @param registryVersion version of the coverage registry
     * @return instrumented Java source code
     */
    public static String instrument(String sourceCode, String recorderPrefix,
                                     String initString, long registryVersion) {
        // Guard against double instrumentation
        if (sourceCode.startsWith(SourceRewriter.MARKER_PREFIX)) {
            throw new IllegalArgumentException("Double instrumentation detected: " +
                    "source appears to have already been instrumented by OpenClover.");
        }

        configureParserForLatestJava();
        CompilationUnit cu = StaticJavaParser.parse(sourceCode);

        List<Insertion> insertions = new ArrayList<>();
        AtomicInteger indexCounter = new AtomicInteger(0);

        CoverageInstrumentationVisitor visitor = new CoverageInstrumentationVisitor(
                recorderPrefix, initString, registryVersion, indexCounter);
        visitor.initializeDisabledRanges(cu);
        visitor.visit(cu, insertions);

        String instrumented = SourceRewriter.rewrite(sourceCode, insertions);
        return SourceRewriter.addMarker(instrumented);
    }

    /**
     * Returns the number of instrumentation points found in the given source code.
     * Useful for tests to verify the correct number of points were identified.
     *
     * @param sourceCode     the Java source code
     * @param recorderPrefix the recorder prefix
     * @return number of instrumentation points (method entries, etc.)
     */
    public static int countInstrumentationPoints(String sourceCode, String recorderPrefix) {
        configureParserForLatestJava();
        CompilationUnit cu = StaticJavaParser.parse(sourceCode);
        List<Insertion> insertions = new ArrayList<>();
        AtomicInteger indexCounter = new AtomicInteger(0);

        CoverageInstrumentationVisitor visitor = new CoverageInstrumentationVisitor(
                recorderPrefix, "", 0L, indexCounter);
        visitor.initializeDisabledRanges(cu);
        visitor.visit(cu, insertions);
        return indexCounter.get();
    }

    /**
     * Configures the JavaParser to support the latest available Java language level.
     * This ensures modern syntax (records, sealed classes, text blocks, pattern matching)
     * is parsed correctly.
     */
    private static void configureParserForLatestJava() {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    /**
     * Visitor that walks the JavaParser AST and collects coverage instrumentation insertions.
     */
    static class CoverageInstrumentationVisitor extends VoidVisitorAdapter<List<Insertion>> {
        private static final String INC_PREFIX = ".inc(";
        private static final String INC_SUFFIX = ");";
        private static final String LAMBDA_INC_PREFIX = "lambdaInc(";

        private final String recorderPrefix;
        private final String initString;
        private final long registryVersion;
        private final AtomicInteger indexCounter;
        private final List<DisabledRange> disabledRanges;

        CoverageInstrumentationVisitor(String recorderPrefix, String initString,
                                       long registryVersion, AtomicInteger indexCounter) {
            this.recorderPrefix = recorderPrefix;
            this.initString = initString;
            this.registryVersion = registryVersion;
            this.indexCounter = indexCounter;
            this.disabledRanges = new ArrayList<>();
        }

        /**
         * Represents a range where instrumentation is disabled (CLOVER:OFF to CLOVER:ON).
         */
        private static class DisabledRange {
            final int startLine;
            final int endLine;

            DisabledRange(int startLine, int endLine) {
                this.startLine = startLine;
                this.endLine = endLine;
            }
        }

        /**
         * Initializes disabled ranges from CLOVER:OFF/ON comments.
         * Must be called before visiting nodes.
         */
        private void initializeDisabledRanges(CompilationUnit cu) {
            disabledRanges.clear();
            List<Comment> comments = cu.getAllComments();
            Integer offLine = null;

            for (Comment comment : comments) {
                String content = comment.getContent().trim();
                Optional<Position> beginPos = comment.getBegin();

                if (!beginPos.isPresent()) {
                    continue;
                }

                if (content.contains("CLOVER:OFF")) {
                    offLine = beginPos.get().line;
                } else if (content.contains("CLOVER:ON") && offLine != null) {
                    disabledRanges.add(new DisabledRange(offLine, beginPos.get().line));
                    offLine = null;
                }
            }

            // If CLOVER:OFF without matching ON, disable until end of file
            if (offLine != null) {
                disabledRanges.add(new DisabledRange(offLine, Integer.MAX_VALUE));
            }
        }

        /**
         * Checks if instrumentation is enabled for the given line.
         */
        private boolean isInstrumentationEnabled(int line) {
            for (DisabledRange range : disabledRanges) {
                if (line >= range.startLine && line <= range.endLine) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, List<Insertion> insertions) {
            if (shouldInjectRecorder(classDecl)) {
                injectRecorder(classDecl, insertions);
            }
            super.visit(classDecl, insertions);
        }

        /**
         * Returns true if the type should get a recorder injected.
         * Skips: interfaces with no concrete members, non-static inner classes
         * (can't have static members in Java 8-15).
         */
        private boolean shouldInjectRecorder(ClassOrInterfaceDeclaration classDecl) {
            if (classDecl.isInterface() && !hasConcreteMembers(classDecl)) {
                return false;
            }
            if (isNonStaticInnerClass(classDecl)) {
                return false;
            }
            return true;
        }

        private boolean hasConcreteMembers(ClassOrInterfaceDeclaration classDecl) {
            return classDecl.getMethods().stream().anyMatch(m -> m.getBody().isPresent());
        }

        /**
         * Returns true if the class is a non-static inner class (nested inside another class
         * without the static modifier). Non-static inner classes can't have static members
         * in Java 8-15, so we can't inject the static recorder class into them.
         */
        private boolean isNonStaticInnerClass(ClassOrInterfaceDeclaration classDecl) {
            if (!classDecl.isNestedType()) {
                return false;
            }
            return !classDecl.isStatic();
        }

        @Override
        public void visit(EnumDeclaration enumDecl, List<Insertion> insertions) {
            // Inject recorder for enums (they can have methods)
            injectRecorder(enumDecl, insertions);
            super.visit(enumDecl, insertions);
        }

        @Override
        public void visit(AnnotationDeclaration annoDecl, List<Insertion> insertions) {
            // Don't inject recorder for annotations (no methods with bodies)
            super.visit(annoDecl, insertions);
        }

        @Override
        public void visit(MethodDeclaration methodDecl, List<Insertion> insertions) {
            Optional<BlockStmt> body = methodDecl.getBody();
            if (body.isPresent()) {
                injectMethodEntry(body.get(), insertions);
            }
            super.visit(methodDecl, insertions);
        }

        @Override
        public void visit(ConstructorDeclaration ctorDecl, List<Insertion> insertions) {
            BlockStmt body = ctorDecl.getBody();
            injectMethodEntry(body, insertions);
            super.visit(ctorDecl, insertions);
        }

        @Override
        public void visit(ExpressionStmt stmt, List<Insertion> insertions) {
            instrumentStatement(stmt, insertions);
            super.visit(stmt, insertions);
        }

        @Override
        public void visit(ReturnStmt stmt, List<Insertion> insertions) {
            instrumentStatement(stmt, insertions);
            super.visit(stmt, insertions);
        }

        @Override
        public void visit(ThrowStmt stmt, List<Insertion> insertions) {
            instrumentStatement(stmt, insertions);
            super.visit(stmt, insertions);
        }

        @Override
        public void visit(AssertStmt stmt, List<Insertion> insertions) {
            instrumentStatement(stmt, insertions);
            super.visit(stmt, insertions);
        }

        @Override
        public void visit(BreakStmt stmt, List<Insertion> insertions) {
            instrumentStatement(stmt, insertions);
            super.visit(stmt, insertions);
        }

        @Override
        public void visit(ContinueStmt stmt, List<Insertion> insertions) {
            instrumentStatement(stmt, insertions);
            super.visit(stmt, insertions);
        }

        @Override
        public void visit(IfStmt stmt, List<Insertion> insertions) {
            // Instrument then branch
            Statement thenStmt = stmt.getThenStmt();
            instrumentBranch(thenStmt, insertions);

            // Instrument else branch if present
            Optional<Statement> elseStmt = stmt.getElseStmt();
            if (elseStmt.isPresent()) {
                instrumentBranch(elseStmt.get(), insertions);
            }

            super.visit(stmt, insertions);
        }

        @Override
        public void visit(WhileStmt stmt, List<Insertion> insertions) {
            instrumentBranch(stmt.getBody(), insertions);
            super.visit(stmt, insertions);
        }

        @Override
        public void visit(DoStmt stmt, List<Insertion> insertions) {
            instrumentBranch(stmt.getBody(), insertions);
            super.visit(stmt, insertions);
        }

        @Override
        public void visit(ForStmt stmt, List<Insertion> insertions) {
            instrumentBranch(stmt.getBody(), insertions);
            super.visit(stmt, insertions);
        }

        @Override
        public void visit(ForEachStmt stmt, List<Insertion> insertions) {
            instrumentBranch(stmt.getBody(), insertions);
            super.visit(stmt, insertions);
        }

        @Override
        public void visit(SwitchEntry entry, List<Insertion> insertions) {
            // Only instrument switch entries in switch STATEMENTS (colon-cases)
            // Switch EXPRESSION entries (arrow-cases) are handled by visit(SwitchExpr)
            if (entry.getParentNode().isPresent()) {
                Node parent = entry.getParentNode().get();
                // Only instrument if parent is NOT a switch expression
                if (!(parent instanceof SwitchExpr)) {
                    // Traditional switch statement - instrument the first statement
                    List<Statement> statements = entry.getStatements();
                    if (!statements.isEmpty()) {
                        Statement firstStmt = statements.get(0);
                        Optional<Position> pos = firstStmt.getBegin();
                        if (pos.isPresent() && isInstrumentationEnabled(pos.get().line)) {
                            int index = indexCounter.getAndIncrement();
                            String incCode = recorderPrefix + INC_PREFIX + index + INC_SUFFIX;
                            insertions.add(Insertion.before(pos.get().line, pos.get().column, incCode, 15));
                        }
                    }
                }
            }
            super.visit(entry, insertions);
        }

        @Override
        public void visit(SwitchExpr switchExpr, List<Insertion> insertions) {
            // For switch expressions with arrow syntax:
            // - Block cases (case X -> { ... }): instrument after opening brace
            // - Expression cases (case X -> expr): skip branch instrumentation
            for (SwitchEntry entry : switchExpr.getEntries()) {
                List<Statement> statements = entry.getStatements();
                if (!statements.isEmpty()) {
                    Statement firstStmt = statements.get(0);
                    // Only instrument if it's a block (arrow -> { ... })
                    // Skip expression cases (arrow -> expr) as we can't insert statements before expressions
                    if (firstStmt instanceof BlockStmt) {
                        instrumentBranch(firstStmt, insertions);
                    }
                }
            }
            // Recursively visit children for nested statement instrumentation
            super.visit(switchExpr, insertions);
        }

        @Override
        public void visit(LambdaExpr lambda, List<Insertion> insertions) {
            Statement body = lambda.getBody();
            if (body instanceof BlockStmt) {
                // Block lambda: insert inc after opening brace (like method entry)
                injectMethodEntry((BlockStmt) body, insertions);
                super.visit(lambda, insertions);
            } else {
                // Expression lambda: wrap with lambdaInc() if safe
                wrapWithLambdaInc(lambda, insertions);
                // No super.visit() — lambdaInc already tracks invocation
            }
        }

        @Override
        public void visit(MethodReferenceExpr methodRef, List<Insertion> insertions) {
            wrapWithLambdaInc(methodRef, insertions);
            super.visit(methodRef, insertions);
        }

        /**
         * Returns true if the expression is in a context where lambdaInc wrapping is safe.
         * Safe: variable initializer (Execute e = lambda), assignment (e = lambda).
         * Unsafe: method arguments, casts, returns, ternary — wrapping breaks type inference.
         */
        private boolean isSafeForLambdaIncWrapping(Expression expr) {
            if (!expr.getParentNode().isPresent()) {
                return false;
            }
            Node parent = expr.getParentNode().get();
            // Safe: variable initializer (VariableDeclarator)
            if (parent.getClass().getSimpleName().equals("VariableDeclarator")) {
                return true;
            }
            // Safe: assignment expression (AssignExpr)
            if (parent.getClass().getSimpleName().equals("AssignExpr")) {
                return true;
            }
            return false;
        }

        /**
         * Wraps an expression (lambda or method reference) with lambdaInc() for coverage tracking.
         * Only wraps in safe contexts (variable initializer, assignment).
         */
        private void wrapWithLambdaInc(Expression expr, List<Insertion> insertions) {
            Optional<Position> start = expr.getBegin();
            Optional<Position> end = expr.getEnd();
            if (!start.isPresent() || !end.isPresent() || !isInstrumentationEnabled(start.get().line)) {
                return;
            }
            // Only wrap in safe contexts (variable initializer, assignment).
            // Skip method arguments, casts, returns, ternary — wrapping breaks type inference.
            if (!isSafeForLambdaIncWrapping(expr)) {
                return;
            }
            int methodIndex = indexCounter.getAndIncrement();
            int stmtIndex = indexCounter.getAndIncrement();
            String prefix = LAMBDA_INC_PREFIX + methodIndex + ",";
            String suffix = "," + stmtIndex + ")";
            insertions.add(Insertion.before(start.get().line, start.get().column, prefix, 12));
            insertions.add(Insertion.after(end.get().line, end.get().column, suffix, 12));
        }

        @Override
        public void visit(TryStmt stmt, List<Insertion> insertions) {
            // Track try-with-resources entry
            if (!stmt.getResources().isEmpty()) {
                Optional<Position> pos = stmt.getBegin();
                if (pos.isPresent() && isInstrumentationEnabled(pos.get().line)) {
                    // Track try-with-resources entry
                    int entryIndex = indexCounter.getAndIncrement();
                    String incCode = recorderPrefix + INC_PREFIX + entryIndex + INC_SUFFIX;
                    insertions.add(Insertion.before(pos.get().line, pos.get().column, incCode, 20));

                    // Add AutoCloseable wrapper that tracks cleanup
                    Expression lastResource = stmt.getResources().get(stmt.getResources().size() - 1);
                    Optional<Position> lastResEnd = lastResource.getEnd();
                    if (lastResEnd.isPresent()) {
                        int closeIndex = indexCounter.getAndIncrement();
                        String recorderBase = extractRecorderBase();
                        String autoCloseCode = ";AutoCloseable __CLR_resource_" + closeIndex +
                            " = new AutoCloseable(){public void close(){" +
                            recorderPrefix + INC_PREFIX + closeIndex + INC_SUFFIX + ";}}" ;
                        insertions.add(Insertion.after(lastResEnd.get().line, lastResEnd.get().column, autoCloseCode, 20));
                    }
                }
            }
            super.visit(stmt, insertions);
        }

        /**
         * Injects the static recorder class inside the class body.
         * Inserts before the first member, or before the closing brace for empty classes.
         * Can be used for classes and enums.
         */
        private void injectRecorder(TypeDeclaration<?> typeDecl, List<Insertion> insertions) {
            String recorderCode = generateRecorderCode();

            if (!typeDecl.getMembers().isEmpty()) {
                // Insert before the first member
                Optional<Position> firstMemberPos = typeDecl.getMembers().get(0).getBegin();
                if (firstMemberPos.isPresent()) {
                    Position pos = firstMemberPos.get();
                    insertions.add(Insertion.before(pos.line, pos.column, recorderCode, 0));
                }
            } else {
                // Empty class — insert before the closing brace
                Optional<Position> endPos = typeDecl.getEnd();
                if (endPos.isPresent()) {
                    Position pos = endPos.get();
                    insertions.add(Insertion.before(pos.line, pos.column, recorderCode, 0));
                }
            }
        }

        /**
         * Injects a coverage increment call after the opening brace of a method/constructor body.
         */
        private void injectMethodEntry(BlockStmt body, List<Insertion> insertions) {
            Optional<Position> bodyStart = body.getBegin();
            if (!bodyStart.isPresent()) {
                return;
            }

            Position pos = bodyStart.get();
            if (!isInstrumentationEnabled(pos.line)) {
                return;
            }

            int index = indexCounter.getAndIncrement();
            // The BlockStmt begins at '{'. Insert our inc() call right after it.
            String incCode = recorderPrefix + INC_PREFIX + index + INC_SUFFIX;
            insertions.add(Insertion.after(pos.line, pos.column, incCode, 10));
        }

        /**
         * Instruments a statement by inserting R.inc(N) before it.
         */
        private void instrumentStatement(Statement stmt, List<Insertion> insertions) {
            Optional<Position> pos = stmt.getBegin();
            if (!pos.isPresent()) {
                return;
            }

            if (!isInstrumentationEnabled(pos.get().line)) {
                return;
            }

            int index = indexCounter.getAndIncrement();
            String incCode = recorderPrefix + INC_PREFIX + index + INC_SUFFIX;
            insertions.add(Insertion.before(pos.get().line, pos.get().column, incCode, 20));
        }

        /**
         * Instruments a branch by inserting R.inc(N) at the start of the branch body.
         * If the branch is not a block statement, inserts before the statement directly.
         */
        private void instrumentBranch(Statement branchBody, List<Insertion> insertions) {
            Optional<Position> pos = branchBody.getBegin();
            if (!pos.isPresent()) {
                return;
            }

            if (!isInstrumentationEnabled(pos.get().line)) {
                return;
            }

            int index = indexCounter.getAndIncrement();
            String incCode = recorderPrefix + INC_PREFIX + index + INC_SUFFIX;

            if (branchBody instanceof BlockStmt) {
                // For block statements, insert after the opening brace
                insertions.add(Insertion.after(pos.get().line, pos.get().column, incCode, 15));
            } else {
                // For single statements, insert before the statement
                insertions.add(Insertion.before(pos.get().line, pos.get().column, incCode, 15));
            }
        }

        /**
         * Generates the static recorder inner class code.
         * This is a simplified version of {@code RecorderInstrEmitter.getInstr()}.
         * In a full implementation, this would generate the complete recorder with
         * error handling, version checks, and profile support.
         */
        private String generateRecorderCode() {
            RecorderCodeGenerator.RecorderConfig config = new RecorderCodeGenerator.RecorderConfig();
            config.recorderBase = extractRecorderBase();
            config.recorderSuffix = extractRecorderSuffix();
            config.initString = initString;
            config.registryVersion = registryVersion;
            config.areLambdasSupported = true;
            return RecorderCodeGenerator.generate(config);
        }

        private String extractRecorderBase() {
            int lastDot = recorderPrefix.lastIndexOf('.');
            return lastDot >= 0 ? recorderPrefix.substring(0, lastDot) : recorderPrefix;
        }

        private String extractRecorderSuffix() {
            int lastDot = recorderPrefix.lastIndexOf('.');
            return lastDot >= 0 ? recorderPrefix.substring(lastDot + 1) : "R";
        }

    }
}
