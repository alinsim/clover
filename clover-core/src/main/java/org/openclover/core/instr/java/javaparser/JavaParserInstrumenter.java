package org.openclover.core.instr.java.javaparser;

import com.github.javaparser.Position;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
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

    private static final String JAVA_LANG_PREFIX = "java.lang.";
    private static final String QUOTE = "\"";
    private static final String BACKSLASH = "\\";

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
        CompilationUnit cu = StaticJavaParser.parse(sourceCode);

        List<Insertion> insertions = new ArrayList<>();
        AtomicInteger indexCounter = new AtomicInteger(0);

        CoverageInstrumentationVisitor visitor = new CoverageInstrumentationVisitor(
                recorderPrefix, initString, registryVersion, indexCounter);
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
        CompilationUnit cu = StaticJavaParser.parse(sourceCode);
        List<Insertion> insertions = new ArrayList<>();
        AtomicInteger indexCounter = new AtomicInteger(0);

        CoverageInstrumentationVisitor visitor = new CoverageInstrumentationVisitor(
                recorderPrefix, "", 0L, indexCounter);
        visitor.visit(cu, insertions);
        return indexCounter.get();
    }

    /**
     * Visitor that walks the JavaParser AST and collects coverage instrumentation insertions.
     */
    static class CoverageInstrumentationVisitor extends VoidVisitorAdapter<List<Insertion>> {
        private final String recorderPrefix;
        private final String initString;
        private final long registryVersion;
        private final AtomicInteger indexCounter;

        CoverageInstrumentationVisitor(String recorderPrefix, String initString,
                                       long registryVersion, AtomicInteger indexCounter) {
            this.recorderPrefix = recorderPrefix;
            this.initString = initString;
            this.registryVersion = registryVersion;
            this.indexCounter = indexCounter;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, List<Insertion> insertions) {
            if (!classDecl.isInterface()) {
                injectRecorder(classDecl, insertions);
            }
            super.visit(classDecl, insertions);
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

        /**
         * Injects the static recorder class inside the class body.
         * Inserts before the first member, or before the closing brace for empty classes.
         */
        private void injectRecorder(ClassOrInterfaceDeclaration classDecl, List<Insertion> insertions) {
            String recorderCode = generateRecorderCode();

            if (!classDecl.getMembers().isEmpty()) {
                // Insert before the first member
                Optional<Position> firstMemberPos = classDecl.getMembers().get(0).getBegin();
                if (firstMemberPos.isPresent()) {
                    Position pos = firstMemberPos.get();
                    insertions.add(Insertion.before(pos.line, pos.column, recorderCode, 0));
                }
            } else {
                // Empty class — insert before the closing brace
                Optional<Position> endPos = classDecl.getEnd();
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

            int index = indexCounter.getAndIncrement();
            Position pos = bodyStart.get();
            // The BlockStmt begins at '{'. Insert our inc() call right after it.
            String incCode = recorderPrefix + ".inc(" + index + ");";
            insertions.add(Insertion.after(pos.line, pos.column, incCode, 10));
        }

        /**
         * Generates the static recorder inner class code.
         * This is a simplified version of {@code RecorderInstrEmitter.getInstr()}.
         * In a full implementation, this would generate the complete recorder with
         * error handling, version checks, and profile support.
         */
        private String generateRecorderCode() {
            String recorderBase = extractRecorderBase();
            String recorderSuffix = extractRecorderSuffix();

            StringBuilder sb = new StringBuilder();
            sb.append("public static class ").append(recorderBase).append("{");
            sb.append("public static org_openclover_runtime.CoverageRecorder ").append(recorderSuffix).append(";");
            sb.append("static{");
            sb.append(recorderSuffix).append("=org_openclover_runtime.Clover.getNullRecorder();");
            sb.append("try{").append(recorderSuffix).append("=org_openclover_runtime.Clover.getRecorder(");
            sb.append(QUOTE).append(escapeJavaString(initString)).append(QUOTE).append(",");
            sb.append(registryVersion).append("L,0L,0,null,null);");
            sb.append("}catch(").append(JAVA_LANG_PREFIX).append("Throwable t){}");
            sb.append("}}");
            sb.append("public static final org_openclover_runtime.TestNameSniffer ");
            sb.append("__CLR_TEST_NAME_SNIFFER=org_openclover_runtime.TestNameSniffer.NULL_INSTANCE;");
            return sb.toString();
        }

        private String extractRecorderBase() {
            int lastDot = recorderPrefix.lastIndexOf('.');
            return lastDot >= 0 ? recorderPrefix.substring(0, lastDot) : recorderPrefix;
        }

        private String extractRecorderSuffix() {
            int lastDot = recorderPrefix.lastIndexOf('.');
            return lastDot >= 0 ? recorderPrefix.substring(lastDot + 1) : "R";
        }

        private static String escapeJavaString(String s) {
            if (s == null) {
                return "";
            }
            return s.replace(BACKSLASH, BACKSLASH + BACKSLASH).replace(QUOTE, BACKSLASH + QUOTE);
        }
    }
}
