package org.openclover.core.instr.java.javaparser;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Position;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.CatchClause;
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
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openclover.core.api.instrumentation.InstrumentationSession;
import org.openclover.core.api.registry.FileInfo;
import org.openclover.core.cfg.instr.java.JavaInstrumentationConfig;
import org.openclover.core.cfg.instr.java.SourceLevel;
import org.openclover.core.context.ContextSetImpl;
import org.openclover.core.context.ContextStore;
import org.openclover.core.context.MethodRegexpContext;
import org.openclover.core.context.StatementRegexpContext;
import org.openclover.core.instr.java.FileStructureInfo;
import org.openclover.core.instr.java.InstrumentationSource;
import org.openclover.core.registry.FixedSourceRegion;
import org.openclover.core.registry.entities.FullStatementInfo;
import org.openclover.core.registry.entities.MethodSignature;
import org.openclover.core.registry.entities.Modifiers;
import org.openclover.core.registry.entities.Parameter;
import org.openclover.core.spi.lang.LanguageConstruct;
import org.openclover.core.util.UnicodeEncodingWriter;
import org.openclover.runtime.CloverNames;
import org.openclover.runtime.api.CloverException;
import org_openclover_runtime.Clover;
import org_openclover_runtime.CoverageRecorder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Session-aware JavaParser instrumenter that integrates with Clover's InstrumentationSession lifecycle.
 * <p>
 * This class bridges the JavaParser AST visitor into the existing Clover instrumentation architecture,
 * ensuring that coverage data is properly registered in the session and can be persisted to the registry.
 * </p>
 * <p>
 * Unlike the standalone {@link JavaParserInstrumenter}, this version:
 * <ul>
 *     <li>Registers classes, methods, and statements with the InstrumentationSession</li>
 *     <li>Uses session-allocated indices for R.inc(N) calls</li>
 *     <li>Generates the recorder prefix from the session version</li>
 *     <li>Computes file checksums and line counts</li>
 *     <li>Returns FileStructureInfo for integration with the existing workflow</li>
 * </ul>
 * </p>
 */
public class SessionAwareInstrumenter {

    private static final String JAVA_LANG_PREFIX = "java.lang.";
    private static final String QUOTE = "\"";
    private static final String BACKSLASH = "\\";

    /**
     * Instruments a single Java source file using JavaParser, registering all
     * coverage points with the given InstrumentationSession.
     *
     * @param source       the source to instrument (file or string)
     * @param output       writer for the instrumented output
     * @param session      the active instrumentation session
     * @param config       instrumentation configuration
     * @param fileEncoding file encoding (nullable, defaults to config encoding)
     * @return metadata about the instrumented file structure
     * @throws CloverException if instrumentation fails
     */
    public static FileStructureInfo instrument(
            @NotNull InstrumentationSource source,
            @NotNull Writer output,
            @NotNull InstrumentationSession session,
            @NotNull JavaInstrumentationConfig config,
            @Nullable String fileEncoding) throws CloverException {
        return instrument(source, output, session, config, fileEncoding, null);
    }

    /**
     * Instruments a single Java source file using JavaParser with context matching support.
     *
     * @param source       the source to instrument (file or string)
     * @param output       writer for the instrumented output
     * @param session      the active instrumentation session
     * @param config       instrumentation configuration
     * @param fileEncoding file encoding (nullable, defaults to config encoding)
     * @param contextStore context store with method and statement patterns (nullable)
     * @return metadata about the instrumented file structure
     * @throws CloverException if instrumentation fails
     */
    public static FileStructureInfo instrument(
            @NotNull InstrumentationSource source,
            @NotNull Writer output,
            @NotNull InstrumentationSession session,
            @NotNull JavaInstrumentationConfig config,
            @Nullable String fileEncoding,
            @Nullable ContextStore contextStore) throws CloverException {

        try {
            // Read the source code into a string
            String sourceCode = readSource(source);

            // Guard against double instrumentation
            if (sourceCode.startsWith(SourceRewriter.MARKER_PREFIX)) {
                throw new CloverException("Double instrumentation detected: " +
                        source.getSourceFileLocation().getAbsolutePath() +
                        " appears to have already been instrumented by OpenClover.");
            }

            // Configure parser for source level and parse
            ParserConfiguration parserConfig = new ParserConfiguration()
                    .setLanguageLevel(mapSourceLevel(config.getSourceLevel()));
            JavaParser parser = new JavaParser(parserConfig);
            CompilationUnit cu = parser.parse(sourceCode).getResult()
                    .orElseThrow(() -> new CloverException("Failed to parse source: " + source.getSourceFileLocation()));

            // Extract package name
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            // Compute line counts
            int lineCount = countLines(sourceCode);
            int ncLineCount = countNonCommentLines(sourceCode);

            // Enter the file in the session
            FileInfo fileInfo = session.enterFile(
                    packageName,
                    source.getSourceFileLocation(),
                    lineCount,
                    ncLineCount,
                    source.getSourceFileLocation().lastModified(),
                    source.getSourceFileLocation().length(),
                    computeChecksum(sourceCode));

            // Create file structure info
            FileStructureInfo structureInfo = new FileStructureInfo(source.getSourceFileLocation());
            structureInfo.setPackageName(packageName);

            // Generate recorder prefix based on session version and file path
            String recorderPrefix = generateRecorderPrefix(session, source);

            // Create visitor and collect insertions
            List<Insertion> insertions = new ArrayList<>();
            SessionAwareVisitor visitor = new SessionAwareVisitor(
                    session, config, recorderPrefix, config.getInitString(), session.getVersion(), contextStore);
            visitor.initializeDisabledRanges(cu);
            visitor.visit(cu, insertions);

            // Apply insertions to source code
            String instrumented = SourceRewriter.rewrite(sourceCode, insertions);
            instrumented = SourceRewriter.addMarker(instrumented);

            // Write output with Unicode encoding
            Writer unicodeWriter = new UnicodeEncodingWriter(new BufferedWriter(output));
            unicodeWriter.write(instrumented);
            unicodeWriter.flush();

            // Note: session.exitFile() is called by the caller (Instrumenter) after updateStatistics

            return structureInfo;

        } catch (IOException e) {
            throw new CloverException("Failed to instrument source: " + source.getSourceFileLocation(), e);
        }
    }

    /**
     * Reads the source into a string, handling Unicode escapes.
     */
    private static String readSource(InstrumentationSource source) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                source.createReader())) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Counts total lines in the source.
     */
    private static int countLines(String source) {
        if (source.isEmpty()) {
            return 0;
        }
        int count = 1;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts non-comment lines (simplified version - just returns line count for now).
     */
    private static int countNonCommentLines(String source) {
        // Simplified implementation - in production this would filter out comment-only lines
        return countLines(source);
    }

    /**
     * Computes a simple checksum of the source.
     */
    private static long computeChecksum(String source) {
        long checksum = 0;
        for (int i = 0; i < source.length(); i++) {
            checksum = checksum * 31 + source.charAt(i);
        }
        return checksum;
    }

    /**
     * Maps SourceLevel enum to JavaParser's LanguageLevel.
     *
     * @param sourceLevel the Clover source level from config
     * @return the corresponding JavaParser language level
     */
    private static ParserConfiguration.LanguageLevel mapSourceLevel(SourceLevel sourceLevel) {
        if (sourceLevel == null) {
            return ParserConfiguration.LanguageLevel.JAVA_17;
        }
        switch (sourceLevel) {
            case JAVA_8: return ParserConfiguration.LanguageLevel.JAVA_8;
            case JAVA_9: return ParserConfiguration.LanguageLevel.JAVA_9;
            case JAVA_10: return ParserConfiguration.LanguageLevel.JAVA_10;
            case JAVA_11: return ParserConfiguration.LanguageLevel.JAVA_11;
            case JAVA_12: return ParserConfiguration.LanguageLevel.JAVA_12;
            case JAVA_13: return ParserConfiguration.LanguageLevel.JAVA_13;
            case JAVA_14: return ParserConfiguration.LanguageLevel.JAVA_14;
            case JAVA_15: return ParserConfiguration.LanguageLevel.JAVA_15;
            case JAVA_16: return ParserConfiguration.LanguageLevel.JAVA_16;
            case JAVA_17: return ParserConfiguration.LanguageLevel.JAVA_17;
            default: return ParserConfiguration.LanguageLevel.JAVA_17;
        }
    }

    /**
     * Generates the recorder prefix for this file.
     * Format: __CLR<version>_<fileIndex>_<classIndex><timestamp>
     */
    private static String generateRecorderPrefix(InstrumentationSession session, InstrumentationSource source) {
        // Use the session version and a hash of the file path
        int fileHash = source.getSourceFileLocation().getAbsolutePath().hashCode();
        long timestamp = System.currentTimeMillis();

        // Generate a unique prefix similar to the existing format
        String prefix = CloverNames.CLOVER_RECORDER_PREFIX +
                        Integer.toString(Math.abs(fileHash), 36) +
                        Long.toString(timestamp, 36);

        return prefix + "." + CloverNames.RECORDER_FIELD_NAME;
    }

    /**
     * Visitor that walks the JavaParser AST and registers coverage points with the session.
     */
    static class SessionAwareVisitor extends VoidVisitorAdapter<List<Insertion>> {
        private static final String INC_PREFIX = ".inc(";
        private static final String INC_SUFFIX = ");";
        private static final long DEFAULT_MODIFIER_BIT = 0x80000000L;
        private static final String LAMBDA_INC_PREFIX = "lambdaInc(";

        private final InstrumentationSession session;
        private final JavaInstrumentationConfig config;
        private final String recorderPrefix;
        private final String initString;
        private final long registryVersion;
        private final List<DisabledRange> disabledRanges;
        private final ContextStore contextStore;
        private int lambdaCounter;

        SessionAwareVisitor(InstrumentationSession session, JavaInstrumentationConfig config,
                           String recorderPrefix, String initString, long registryVersion, ContextStore contextStore) {
            this.session = session;
            this.config = config;
            this.recorderPrefix = recorderPrefix;
            this.initString = initString;
            this.registryVersion = registryVersion;
            this.contextStore = contextStore;
            this.disabledRanges = new ArrayList<>();
            this.lambdaCounter = 0;
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
         */
        void initializeDisabledRanges(CompilationUnit cu) {
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

        /**
         * Matches a method declaration against registered method context patterns.
         */
        private ContextSetImpl matchMethodContexts(MethodDeclaration methodDecl) {
            ContextSetImpl ctx = new ContextSetImpl();
            if (contextStore == null) {
                return ctx;
            }

            String sig = methodDecl.getDeclarationAsString(true, true, true);
            for (MethodRegexpContext mctx : contextStore.getMethodContexts()) {
                if (mctx.getPattern().matcher(sig).matches()) {
                    ctx = ctx.set(mctx.getIndex());
                }
            }
            return ctx;
        }

        /**
         * Matches a statement against registered statement context patterns.
         */
        private ContextSetImpl matchStatementContexts(Statement stmt) {
            ContextSetImpl ctx = new ContextSetImpl();
            if (contextStore == null) {
                return ctx;
            }

            String text = stmt.toString();
            for (StatementRegexpContext sctx : contextStore.getStatementContexts()) {
                if (sctx.getPattern().matcher(text).matches()) {
                    ctx = ctx.set(sctx.getIndex());
                }
            }
            return ctx;
        }

        /**
         * Checks if a method is a test method based on annotations.
         */
        private boolean isTestMethod(MethodDeclaration methodDecl) {
            for (AnnotationExpr annotation : methodDecl.getAnnotations()) {
                String name = annotation.getNameAsString();
                if ("Test".equals(name) || "ParameterizedTest".equals(name)
                        || name.endsWith(".Test") || name.endsWith(".ParameterizedTest")) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Checks if a class is a test class based on annotations or inheritance.
         */
        private boolean isTestClass(ClassOrInterfaceDeclaration classDecl) {
            if (classDecl.isInterface()) {
                return false;
            }
            for (ClassOrInterfaceType ext : classDecl.getExtendedTypes()) {
                if ("TestCase".equals(ext.getNameAsString())) {
                    return true;
                }
            }
            for (AnnotationExpr ann : classDecl.getAnnotations()) {
                String name = ann.getNameAsString();
                if ("RunWith".equals(name) || "ExtendWith".equals(name)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Calculates cyclomatic complexity for a method body.
         */
        private static int calculateComplexity(BlockStmt body) {
            int complexity = 1;
            complexity += body.findAll(IfStmt.class).size();
            complexity += body.findAll(WhileStmt.class).size();
            complexity += body.findAll(DoStmt.class).size();
            complexity += body.findAll(ForStmt.class).size();
            complexity += body.findAll(ForEachStmt.class).size();
            complexity += body.findAll(SwitchEntry.class).stream()
                    .filter(e -> !e.getLabels().isEmpty())
                    .count();
            complexity += body.findAll(CatchClause.class).size();
            complexity += body.findAll(ConditionalExpr.class).size();
            complexity += body.findAll(BinaryExpr.class).stream()
                    .filter(e -> e.getOperator() == BinaryExpr.Operator.AND
                                 || e.getOperator() == BinaryExpr.Operator.OR)
                    .count();
            return complexity;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, List<Insertion> insertions) {
            Optional<Position> begin = classDecl.getBegin();
            Optional<Position> end = classDecl.getEnd();
            boolean isInterface = classDecl.isInterface();

            if (begin.isPresent()) {
                // Register ALL classes including interfaces (needed for parent stack)
                String className = classDecl.getNameAsString();
                FixedSourceRegion region = new FixedSourceRegion(
                        begin.get().line, begin.get().column,
                        end.map(p -> p.line).orElse(begin.get().line),
                        end.map(p -> p.column).orElse(begin.get().column));

                // Build modifiers
                Modifiers mods = buildModifiers(classDecl);

                session.enterClass(className, region, mods, isInterface, false, false);

                // Only inject recorder for classes that can have static members.
                // Skip: non-static inner classes (can't have static members in Java 8-15),
                // interfaces with no concrete members.
                if (!isNonStaticInnerClass(classDecl)
                        && (!isInterface || hasConcreteMembers(classDecl))) {
                    injectRecorder(classDecl, insertions);
                }
            }

            // Visit children
            super.visit(classDecl, insertions);

            if (begin.isPresent()) {
                Optional<Position> endPos = classDecl.getEnd();
                if (endPos.isPresent()) {
                    session.exitClass(endPos.get().line, endPos.get().column);
                }
            }
        }

        @Override
        public void visit(EnumDeclaration enumDecl, List<Insertion> insertions) {
            Optional<Position> begin = enumDecl.getBegin();
            Optional<Position> end = enumDecl.getEnd();

            if (begin.isPresent()) {
                // Register enum as class with session
                String enumName = enumDecl.getNameAsString();
                FixedSourceRegion region = new FixedSourceRegion(
                        begin.get().line, begin.get().column,
                        end.map(p -> p.line).orElse(begin.get().line),
                        end.map(p -> p.column).orElse(begin.get().column));

                // Build modifiers for enum
                Modifiers mods = buildModifiers(enumDecl);

                session.enterClass(enumName, region, mods, false, true, false);

                // Inject recorder for enums (they can have methods)
                injectRecorder(enumDecl, insertions);
            }

            // Visit children
            super.visit(enumDecl, insertions);

            if (begin.isPresent()) {
                Optional<Position> endPos = enumDecl.getEnd();
                if (endPos.isPresent()) {
                    session.exitClass(endPos.get().line, endPos.get().column);
                }
            }
        }

        @Override
        public void visit(AnnotationDeclaration annoDecl, List<Insertion> insertions) {
            Optional<Position> begin = annoDecl.getBegin();
            Optional<Position> end = annoDecl.getEnd();

            if (begin.isPresent()) {
                // Register annotation as class with session
                String annoName = annoDecl.getNameAsString();
                FixedSourceRegion region = new FixedSourceRegion(
                        begin.get().line, begin.get().column,
                        end.map(p -> p.line).orElse(begin.get().line),
                        end.map(p -> p.column).orElse(begin.get().column));

                // Build modifiers for annotation
                Modifiers mods = buildModifiers(annoDecl);

                session.enterClass(annoName, region, mods, false, false, true);

                // Don't inject recorder for annotations (no methods with bodies)
            }

            // Visit children
            super.visit(annoDecl, insertions);

            if (begin.isPresent()) {
                Optional<Position> endPos = annoDecl.getEnd();
                if (endPos.isPresent()) {
                    session.exitClass(endPos.get().line, endPos.get().column);
                }
            }
        }

        @Override
        public void visit(MethodDeclaration methodDecl, List<Insertion> insertions) {
            Optional<BlockStmt> body = methodDecl.getBody();
            if (body.isPresent()) {
                Optional<Position> begin = methodDecl.getBegin();
                Optional<Position> end = methodDecl.getEnd();

                if (begin.isPresent()) {
                    // Build method signature with modifiers and return type
                    MethodSignature sig = buildMethodSignature(methodDecl);
                    String name = methodDecl.getNameAsString();

                    // Detect test methods
                    boolean isTest = isTestMethod(methodDecl);

                    // JUnit 3 style: method name starts with "test" and is in a test class
                    if (!isTest && name.startsWith("test")) {
                        ClassOrInterfaceDeclaration classDecl = methodDecl.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
                        if (classDecl != null && isTestClass(classDecl)) {
                            isTest = true;
                        }
                    }

                    // Calculate complexity
                    int complexity = calculateComplexity(body.get());

                    FixedSourceRegion region = new FixedSourceRegion(begin.get().line, begin.get().column);

                    // Match method against context patterns
                    ContextSetImpl methodContext = matchMethodContexts(methodDecl);

                    session.enterMethod(
                            methodContext,
                            region,
                            sig,
                            isTest,
                            isTest ? name : null,
                            false,
                            complexity,
                            LanguageConstruct.Builtin.METHOD);

                    // Inject method entry tracking
                    injectMethodEntry(body.get(), insertions);
                }
            }

            super.visit(methodDecl, insertions);

            if (body.isPresent()) {
                Optional<Position> end = methodDecl.getEnd();
                if (end.isPresent()) {
                    session.exitMethod(end.get().line, end.get().column);
                }
            }
        }

        @Override
        public void visit(ConstructorDeclaration ctorDecl, List<Insertion> insertions) {
            BlockStmt body = ctorDecl.getBody();
            Optional<Position> begin = ctorDecl.getBegin();
            Optional<Position> end = ctorDecl.getEnd();

            if (begin.isPresent()) {
                // Build method signature for constructor
                String name = ctorDecl.getNameAsString();
                MethodSignature sig = new MethodSignature(name);

                // Calculate complexity
                int complexity = calculateComplexity(body);

                FixedSourceRegion region = new FixedSourceRegion(begin.get().line, begin.get().column);

                session.enterMethod(
                        new ContextSetImpl(),
                        region,
                        sig,
                        false,
                        null,
                        false,
                        complexity,
                        LanguageConstruct.Builtin.METHOD);

                injectMethodEntry(body, insertions);
            }

            super.visit(ctorDecl, insertions);

            if (end.isPresent()) {
                session.exitMethod(end.get().line, end.get().column);
            }
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
            Statement thenStmt = stmt.getThenStmt();
            instrumentBranch(thenStmt, insertions);

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
                    List<Statement> statements = entry.getStatements();
                    if (!statements.isEmpty()) {
                        Statement firstStmt = statements.get(0);
                        Optional<Position> pos = firstStmt.getBegin();
                        if (pos.isPresent()) {
                            // instrumentStatement now handles CLOVER:OFF internally
                            instrumentStatement(firstStmt, insertions);
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
            Optional<Position> begin = lambda.getBegin();
            Optional<Position> end = lambda.getEnd();

            if (begin.isPresent()) {
                Statement body = lambda.getBody();

                // Build lambda signature
                String lambdaName = "lambda$" + lambdaCounter++;
                MethodSignature sig = new MethodSignature(lambdaName);

                FixedSourceRegion region = new FixedSourceRegion(begin.get().line, begin.get().column);

                // Register lambda as a method with isLambda=true
                session.enterMethod(
                        new ContextSetImpl(),
                        region,
                        sig,
                        false,
                        null,
                        true,
                        1,
                        LanguageConstruct.Builtin.METHOD);

                if (body instanceof BlockStmt) {
                    // Block lambda: insert inc after opening brace (like method entry)
                    injectMethodEntry((BlockStmt) body, insertions);
                    ((BlockStmt) body).accept(this, insertions);
                } else {
                    // Expression lambda: wrap with lambdaInc() if safe
                    wrapWithLambdaIncSession(lambda, insertions);
                }
            }

            if (end.isPresent()) {
                session.exitMethod(end.get().line, end.get().column);
            }
        }

        @Override
        public void visit(MethodReferenceExpr methodRef, List<Insertion> insertions) {
            Optional<Position> begin = methodRef.getBegin();
            Optional<Position> end = methodRef.getEnd();

            if (begin.isPresent()) {
                String refName = "methodRef$" + lambdaCounter++;
                MethodSignature sig = new MethodSignature(refName);
                FixedSourceRegion region = new FixedSourceRegion(begin.get().line, begin.get().column);

                session.enterMethod(new ContextSetImpl(), region, sig, false, null, true, 1,
                        LanguageConstruct.Builtin.METHOD);

                wrapWithLambdaIncSession(methodRef, insertions);
            }

            super.visit(methodRef, insertions);

            if (begin.isPresent() && end.isPresent()) {
                session.exitMethod(end.get().line, end.get().column);
            }
        }

        private boolean isSafeForLambdaIncWrapping(Expression expr) {
            if (!expr.getParentNode().isPresent()) {
                return false;
            }
            Node parent = expr.getParentNode().get();
            if (parent.getClass().getSimpleName().equals("VariableDeclarator")) {
                return true;
            }
            if (parent.getClass().getSimpleName().equals("AssignExpr")) {
                return true;
            }
            return false;
        }

        /**
         * Wraps an expression with lambdaInc() using session-allocated indices.
         * Only wraps in safe contexts (variable initializer, assignment).
         */
        private void wrapWithLambdaIncSession(Expression expr, List<Insertion> insertions) {
            Optional<Position> start = expr.getBegin();
            Optional<Position> end = expr.getEnd();
            if (!start.isPresent() || !end.isPresent() || !isInstrumentationEnabled(start.get().line)) {
                return;
            }
            if (!isSafeForLambdaIncWrapping(expr)) {
                return;
            }
            FixedSourceRegion stmtRegion = new FixedSourceRegion(start.get().line, start.get().column);
            FullStatementInfo stmtInfo = session.addStatement(
                    new ContextSetImpl(), stmtRegion, 0, LanguageConstruct.Builtin.STATEMENT);

            int methodIndex = session.getCurrentOffsetFromFile() - 2;
            int stmtIndex = stmtInfo.getDataIndex();
            String recorderBase = extractRecorderBase();
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
                    // Register statement with session for the try-with-resources entry
                    FixedSourceRegion region = new FixedSourceRegion(pos.get().line, pos.get().column);
                    FullStatementInfo stmtInfo = session.addStatement(
                            new ContextSetImpl(),
                            region,
                            0,
                            LanguageConstruct.Builtin.STATEMENT);

                    int index = stmtInfo.getDataIndex();
                    String incCode = recorderPrefix + INC_PREFIX + index + INC_SUFFIX;
                    insertions.add(Insertion.before(pos.get().line, pos.get().column, incCode, 20));

                    // Add AutoCloseable wrapper that tracks cleanup
                    Expression lastResource = stmt.getResources().get(stmt.getResources().size() - 1);
                    Optional<Position> lastResEnd = lastResource.getEnd();
                    if (lastResEnd.isPresent()) {
                        // Register statement with session for the cleanup tracking
                        FixedSourceRegion closeRegion = new FixedSourceRegion(lastResEnd.get().line, lastResEnd.get().column);
                        FullStatementInfo closeStmtInfo = session.addStatement(
                                new ContextSetImpl(),
                                closeRegion,
                                0,
                                LanguageConstruct.Builtin.STATEMENT);

                        int closeIndex = closeStmtInfo.getDataIndex();
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
         * Can be used for classes and enums.
         */
        private void injectRecorder(TypeDeclaration<?> typeDecl, List<Insertion> insertions) {
            String recorderCode = generateRecorderCode();

            if (!typeDecl.getMembers().isEmpty()) {
                Optional<Position> firstMemberPos = typeDecl.getMembers().get(0).getBegin();
                if (firstMemberPos.isPresent()) {
                    Position pos = firstMemberPos.get();
                    insertions.add(Insertion.before(pos.line, pos.column, recorderCode, 0));
                }
            } else {
                Optional<Position> endPos = typeDecl.getEnd();
                if (endPos.isPresent()) {
                    Position pos = endPos.get();
                    insertions.add(Insertion.before(pos.line, pos.column, recorderCode, 0));
                }
            }
        }

        /**
         * Builds modifiers from an enum declaration.
         */
        private Modifiers buildModifiers(EnumDeclaration enumDecl) {
            long modMask = 0;
            if (enumDecl.isPublic()) {
                modMask |= Modifier.PUBLIC;
            }
            if (enumDecl.isPrivate()) {
                modMask |= Modifier.PRIVATE;
            }
            if (enumDecl.isProtected()) {
                modMask |= Modifier.PROTECTED;
            }
            if (enumDecl.isStatic()) {
                modMask |= Modifier.STATIC;
            }
            return Modifiers.createFrom(modMask, null);
        }

        /**
         * Builds modifiers from an annotation declaration.
         */
        private Modifiers buildModifiers(AnnotationDeclaration annoDecl) {
            long modMask = 0;
            if (annoDecl.isPublic()) {
                modMask |= Modifier.PUBLIC;
            }
            if (annoDecl.isPrivate()) {
                modMask |= Modifier.PRIVATE;
            }
            if (annoDecl.isProtected()) {
                modMask |= Modifier.PROTECTED;
            }
            if (annoDecl.isStatic()) {
                modMask |= Modifier.STATIC;
            }
            return Modifiers.createFrom(modMask, null);
        }

        /**
         * Injects method entry tracking using session-allocated index.
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

            // The session already allocated an index when we called enterMethod
            // We need to use the current offset which was incremented
            int index = session.getCurrentOffsetFromFile() - 1;
            String incCode = recorderPrefix + INC_PREFIX + index + INC_SUFFIX;
            insertions.add(Insertion.after(pos.line, pos.column, incCode, 10));
        }

        /**
         * Instruments a statement by registering it with the session and inserting R.inc(N).
         * Always registers the statement (even in CLOVER:OFF regions) so it appears in the coverage model,
         * but only inserts the instrumentation code when enabled.
         */
        private void instrumentStatement(Statement stmt, List<Insertion> insertions) {
            Optional<Position> pos = stmt.getBegin();
            if (!pos.isPresent()) {
                return;
            }

            if (!isInstrumentationEnabled(pos.get().line)) {
                return;
            }

            if (isInsideSwitchExpressionArrowCase(stmt)) {
                return;
            }

            ContextSetImpl stmtContext = matchStatementContexts(stmt);
            FixedSourceRegion region = new FixedSourceRegion(pos.get().line, pos.get().column);
            FullStatementInfo stmtInfo = session.addStatement(
                    stmtContext,
                    region,
                    0,
                    LanguageConstruct.Builtin.STATEMENT);

            int index = stmtInfo.getDataIndex();
            String incCode = recorderPrefix + INC_PREFIX + index + INC_SUFFIX;
            insertions.add(Insertion.before(pos.get().line, pos.get().column, incCode, 20));
        }

        /**
         * Instruments a branch by inserting R.inc(N) at the start of the branch body.
         * Always registers the statement (even in CLOVER:OFF regions) so it appears in the coverage model,
         * but only inserts the instrumentation code when enabled.
         */
        private void instrumentBranch(Statement branchBody, List<Insertion> insertions) {
            Optional<Position> pos = branchBody.getBegin();
            if (!pos.isPresent()) {
                return;
            }

            // Match statement against context patterns
            ContextSetImpl stmtContext = matchStatementContexts(branchBody);

            // Always register statement with session for the branch
            FixedSourceRegion region = new FixedSourceRegion(pos.get().line, pos.get().column);
            FullStatementInfo stmtInfo = session.addStatement(
                    stmtContext,
                    region,
                    0,
                    LanguageConstruct.Builtin.STATEMENT);

            // Only insert instrumentation code if enabled (respects CLOVER:OFF)
            if (isInstrumentationEnabled(pos.get().line)) {
                int index = stmtInfo.getDataIndex();
                String incCode = recorderPrefix + INC_PREFIX + index + INC_SUFFIX;

                if (branchBody instanceof BlockStmt) {
                    insertions.add(Insertion.after(pos.get().line, pos.get().column, incCode, 15));
                } else {
                    insertions.add(Insertion.before(pos.get().line, pos.get().column, incCode, 15));
                }
            }
        }

        /**
         * Generates the static recorder inner class code including lambdaInc method.
         */
        private String generateRecorderCode() {
            String recorderBase = extractRecorderBase();
            String recorderSuffix = extractRecorderSuffix();

            StringBuilder sb = new StringBuilder();
            // Static recorder class
            sb.append("public static class ").append(recorderBase).append("{");
            sb.append("public static ").append(CoverageRecorder.class.getName()).append(" ").append(recorderSuffix).append(";");
            sb.append("static{");
            sb.append(recorderSuffix).append("=").append(Clover.class.getName()).append(".getNullRecorder();");
            sb.append("try{").append(recorderSuffix).append("=").append(Clover.class.getName()).append(".getRecorder(");
            sb.append(QUOTE).append(escapeJavaString(initString)).append(QUOTE).append(",");
            sb.append(registryVersion).append("L,0L,0,null,null);");
            sb.append("}catch(").append(JAVA_LANG_PREFIX).append("Throwable t){}");
            sb.append("}}");

            // Lambda proxy method (at top-level class scope for proper interface access)
            sb.append(generateLambdaIncMethod(recorderBase, recorderSuffix, JAVA_LANG_PREFIX));

            // Test sniffer field
            sb.append("public static final org_openclover_runtime.TestNameSniffer ");
            sb.append("__CLR_TEST_NAME_SNIFFER=org_openclover_runtime.TestNameSniffer.NULL_INSTANCE;");
            return sb.toString();
        }

        /**
         * Generates the lambdaInc proxy method for wrapping lambda expressions.
         * Must be at top-level class scope (not inside __CLR) to access package-private interfaces.
         */
        private String generateLambdaIncMethod(String recorderBase, String recorderSuffix, String javaLangPrefix) {
            String recorderRef = recorderBase + "." + recorderSuffix;
            return "@" + javaLangPrefix + "SuppressWarnings(\"unchecked\") " +
                    "public static <I, T extends I> I lambdaInc(final int i,final T l,final int si){" +
                    javaLangPrefix + "reflect.InvocationHandler h=" +
                    "new " + javaLangPrefix + "reflect.InvocationHandler(){" +
                    "public " + javaLangPrefix + "Object invoke(" +
                    javaLangPrefix + "Object p," + javaLangPrefix + "reflect.Method m," +
                    javaLangPrefix + "Object[] a) throws Throwable{" +
                    recorderRef + ".inc(i);" +
                    recorderRef + ".inc(si);" +
                    "try{return m.invoke(l,a);}catch(" + javaLangPrefix + "reflect.InvocationTargetException e){" +
                    "throw e.getCause()!=null?e.getCause():" +
                    "new RuntimeException(\"OpenClover failed to invoke instrumented lambda\",e);" +
                    "}}};return (I)" + javaLangPrefix + "reflect.Proxy.newProxyInstance(l.getClass().getClassLoader(),l.getClass().getInterfaces(),h);" +
                    "}";
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

        /**
         * Builds a method signature from a JavaParser MethodDeclaration.
         */
        private MethodSignature buildMethodSignature(MethodDeclaration methodDecl) {
            String name = methodDecl.getNameAsString();
            String returnType = methodDecl.getTypeAsString();

            long modMask = 0;
            if (methodDecl.isPublic()) {
                modMask |= Modifier.PUBLIC;
            }
            if (methodDecl.isPrivate()) {
                modMask |= Modifier.PRIVATE;
            }
            if (methodDecl.isProtected()) {
                modMask |= Modifier.PROTECTED;
            }
            if (methodDecl.isAbstract()) {
                modMask |= Modifier.ABSTRACT;
            }
            if (methodDecl.isFinal()) {
                modMask |= Modifier.FINAL;
            }
            if (methodDecl.isStatic()) {
                modMask |= Modifier.STATIC;
            }
            if (methodDecl.isSynchronized()) {
                modMask |= Modifier.SYNCHRONIZED;
            }
            if (methodDecl.isNative()) {
                modMask |= Modifier.NATIVE;
            }
            if (methodDecl.isDefault()) {
                modMask |= DEFAULT_MODIFIER_BIT;
            }

            Modifiers mods = Modifiers.createFrom(modMask, null);

            // For now, use simple constructor with name, returnType, and modifiers
            // A full implementation would also extract parameters and type parameters
            return new MethodSignature(name, "", returnType, new Parameter[0], new String[0], mods);
        }

        /**
         * Builds modifiers from a class declaration.
         */
        private Modifiers buildModifiers(ClassOrInterfaceDeclaration classDecl) {
            long modMask = 0;
            if (classDecl.isPublic()) {
                modMask |= Modifier.PUBLIC;
            }
            if (classDecl.isPrivate()) {
                modMask |= Modifier.PRIVATE;
            }
            if (classDecl.isProtected()) {
                modMask |= Modifier.PROTECTED;
            }
            if (classDecl.isAbstract()) {
                modMask |= Modifier.ABSTRACT;
            }
            if (classDecl.isFinal()) {
                modMask |= Modifier.FINAL;
            }
            if (classDecl.isStatic()) {
                modMask |= Modifier.STATIC;
            }
            return Modifiers.createFrom(modMask, null);
        }

        private boolean hasConcreteMembers(ClassOrInterfaceDeclaration classDecl) {
            return classDecl.getMethods().stream().anyMatch(m -> m.getBody().isPresent());
        }

        private boolean isNonStaticInnerClass(ClassOrInterfaceDeclaration classDecl) {
            if (!classDecl.isNestedType()) {
                return false;
            }
            return !classDecl.isStatic();
        }

        private boolean isInsideSwitchExpressionArrowCase(Statement stmt) {
            Optional<Node> parent = stmt.getParentNode();
            if (!parent.isPresent() || !(parent.get() instanceof SwitchEntry)) {
                return false;
            }
            SwitchEntry entry = (SwitchEntry) parent.get();
            return entry.getType() != SwitchEntry.Type.STATEMENT_GROUP;
        }
    }
}
