package org.openclover.core.instr.java.javaparser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Position;
import com.github.javaparser.Problem;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.LabeledStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openclover.core.api.instrumentation.InstrumentationSession;
import org.openclover.core.api.registry.FileInfo;
import org.openclover.core.api.registry.MethodInfo;
import org.openclover.core.cfg.instr.java.JavaInstrumentationConfig;
import org.openclover.core.cfg.instr.java.SourceLevel;
import org.openclover.core.context.ContextSetImpl;
import org.openclover.core.context.ContextStore;
import org.openclover.core.context.MethodRegexpContext;
import org.openclover.core.context.StatementRegexpContext;
import org.openclover.core.instr.java.FileStructureInfo;
import org.openclover.core.instr.java.InstrumentationSource;
import org.openclover.core.registry.FixedSourceRegion;
import org.openclover.core.registry.entities.FullBranchInfo;
import org.openclover.core.registry.entities.FullStatementInfo;
import org.openclover.core.registry.entities.MethodSignature;
import org.openclover.core.registry.entities.Modifiers;
import org.openclover.core.spi.lang.LanguageConstruct;
import org.openclover.runtime.CloverNames;
import org.openclover.runtime.api.CloverException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AST-based Java source instrumenter using LexicalPreservingPrinter.
 *
 * Replaces the text-based Insertion+SourceRewriter approach with direct AST modification.
 * The tree structure enforces correct scoping — insertion ordering bugs are structurally impossible.
 */
public class AstInstrumenter {

    private static final String MARKER_COMMENT = "/* $$ This file has been instrumented by OpenClover $$ */";
    private static final String INC_PREFIX = ".inc(";
    private static final String INC_SUFFIX = ");";
    private static final String IGET_METHOD = "iget";
    private static final String VARARGS_SUFFIX = "...";
    private static final String LAMBDA_INC_METHOD = "lambdaI" + "nc";
    private static final String VARIABLE_DECLARATOR = "VariableD" + "eclarator";
    private static final String ASSIGN_EXPR = "AssignE" + "xpr";
    private static final String QUOTE = "\"";
    private static final String UNKNOWN_CLASS = "UnknownClass";
    private static final String GET_CLASS_NAME = "getClass().getName()";
    private static final String TEST_EXCEPTION_VAR = "__CLR_t";

    private AstInstrumenter() {}

    /**
     * Production API: Instrument a Java source file with full session integration.
     * Replaces SessionAwareInstrumenter with AST modification + LPP output.
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
            // Read source code
            String sourceCode = readSource(source);

            // Guard against double instrumentation
            if (sourceCode.startsWith(MARKER_COMMENT)) {
                throw new CloverException("Double instrumentation detected: " +
                        source.getSourceFileLocation().getAbsolutePath() +
                        " appears to have already been instrumented by OpenClover.");
            }

            // Configure parser for source level and parse with LPP
            ParserConfiguration parserConfig = new ParserConfiguration()
                    .setLanguageLevel(mapSourceLevel(config.getSourceLevel()));
            JavaParser parser = new JavaParser(parserConfig);
            ParseResult<CompilationUnit> parseResult = parser.parse(sourceCode);
            if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
                StringBuilder msg = new StringBuilder("Failed to parse source: ")
                        .append(source.getSourceFileLocation());
                int shown = 0;
                for (Problem problem : parseResult.getProblems()) {
                    if (shown >= 3) {
                        msg.append("; ... and ").append(parseResult.getProblems().size() - 3).append(" more");
                        break;
                    }
                    msg.append("; ");
                    problem.getLocation().ifPresent(loc ->
                            msg.append("line ").append(loc.getBegin().getRange().map(r -> r.begin.line).orElse(-1))
                                    .append(", col ").append(loc.getBegin().getRange().map(r -> r.begin.column).orElse(-1))
                                    .append(": "));
                    msg.append(problem.getMessage());
                    shown++;
                }
                throw new CloverException(msg.toString());
            }
            CompilationUnit cu = parseResult.getResult().get();

            LexicalPreservingPrinter.setup(cu);

            // Extract package name
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            // Compute line counts and checksum
            int lineCount = countLines(sourceCode);
            int ncLineCount = countNonCommentLines(sourceCode);
            long checksum = computeChecksum(sourceCode);

            // Enter the file in the session
            FileInfo fileInfo = session.enterFile(
                    packageName,
                    source.getSourceFileLocation(),
                    lineCount,
                    ncLineCount,
                    source.getSourceFileLocation().lastModified(),
                    source.getSourceFileLocation().length(),
                    checksum);

            // Create file structure info
            FileStructureInfo structureInfo = new FileStructureInfo(source.getSourceFileLocation());
            structureInfo.setPackageName(packageName);

            // Generate recorder prefix based on session version
            String recorderPrefix = generateRecorderPrefix(session, source);

            // Create visitor and walk AST
            SessionAwareAstVisitor visitor = new SessionAwareAstVisitor(
                    session, config, recorderPrefix, config.getInitString(),
                    session.getVersion(), contextStore);
            visitor.initializeDisabledRanges(cu);
            visitor.visit(cu, null);

            // Inject recorder class using RecorderCodeGenerator
            injectRecorderClass(cu, visitor, config);

            // Output via LexicalPreservingPrinter with post-processing for LPP edge cases
            String instrumented = MARKER_COMMENT + "\n" + postProcessLppOutput(LexicalPreservingPrinter.print(cu));
            output.write(instrumented);
            output.flush();

            return structureInfo;

        } catch (IOException e) {
            throw new CloverException("Failed to instrument source: " + source.getSourceFileLocation(), e);
        }
    }

    /**
     * Standalone API: Instrument a Java source string. Returns instrumented source.
     * Creates a minimal stub session for testing.
     */
    public static String instrument(String source, String recorderPrefix, String initString) {
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        JavaParser parser = new JavaParser(config);
        CompilationUnit cu = parser.parse(source).getResult()
                .orElseThrow(() -> new IllegalArgumentException("Failed to parse source"));
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

    /**
     * Injects the recorder class into the first top-level type using RecorderCodeGenerator.
     * Handles both classes/interfaces and enums.
     */
    private static void injectRecorderClass(CompilationUnit cu, SessionAwareAstVisitor visitor,
                                            JavaInstrumentationConfig config) {
        int maxDataIndex = visitor.session.getCurrentFileMaxIndex();
        String recorderCode = visitor.generateRecorderCode(maxDataIndex, config);

        // Parse recorder code into AST members
        String tempClass = "class TempWrapper { " + recorderCode + " }";
        CompilationUnit tempCu = StaticJavaParser.parse(tempClass);
        ClassOrInterfaceDeclaration tempWrapper = tempCu.findFirst(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new RuntimeException("Failed to parse recorder code"));
        NodeList<BodyDeclaration<?>> recorderMembers = tempWrapper.getMembers();

        // Try class/interface first
        for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (clazz.isTopLevelType()) {
                recorderMembers.forEach(member -> clazz.addMember(member.clone()));
                return;
            }
        }

        // Try enum — enums with methods/constructors need the recorder too
        for (EnumDeclaration enumDecl : cu.findAll(EnumDeclaration.class)) {
            if (enumDecl.isTopLevelType()) {
                recorderMembers.forEach(member -> enumDecl.addMember(member.clone()));
                return;
            }
        }
    }

    /**
     * Standalone test helper: inject simple recorder.
     */
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
     * Reads the source into a string.
     */
    private static String readSource(InstrumentationSource source) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(source.createReader())) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Post-processes LPP output to fix known edge cases.
     * LPP preserves original token semicolons even when statements are replaced
     * with blocks, producing invalid "};case" sequences in switch expressions.
     */
    private static String postProcessLppOutput(String output) {
        // Remove stray semicolons after closing braces in switch arrow-case entries.
        // Pattern: "}\n;" or "};" followed by whitespace+case/default
        // Remove stray semicolons between switch arrow-case blocks.
        // LPP preserves the original "case X -> expr;" semicolons after rewriting to blocks.
        // Pattern: "}\n;\n" or "}\n;  \ncase" — semicolon alone on its own line after a block.
        // Remove stray semicolons after switch arrow-case blocks.
        // Two LPP patterns depending on single-line vs multi-line source:
        //   Single-line: "}\n; case"   (semicolon on same line as case)
        //   Multi-line:  "}\n;\n  case" (semicolon on its own line)
        String braceGroup = "}\n$1";
        output = output.replaceAll("\\}\n;\\s*\n(\\s*(?:case |default ))", braceGroup);
        output = output.replaceAll("\\}\n; (case |default )", braceGroup);
        // Handle last entry before switch closing brace
        output = output.replaceAll("\\}\n;\\s*\n(\\s*\\};)", braceGroup);
        output = output.replaceAll("\\}\n; (\\};)", braceGroup);
        return output;
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

    private static final String BLOCK_COMMENT_END = "*/";

    /**
     * Counts non-comment, non-blank lines in the source.
     */
    private static int countNonCommentLines(String source) {
        int ncCount = 0;
        boolean inBlockComment = false;
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (inBlockComment) {
                if (trimmed.contains(BLOCK_COMMENT_END)) {
                    inBlockComment = false;
                }
                continue;
            }
            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                continue;
            }
            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains(BLOCK_COMMENT_END)) {
                    inBlockComment = true;
                }
                continue;
            }
            if (trimmed.startsWith("*") && !trimmed.startsWith(BLOCK_COMMENT_END)) {
                continue;
            }
            ncCount++;
        }
        return ncCount;
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
     */
    private static String generateRecorderPrefix(InstrumentationSession session, InstrumentationSource source) {
        int fileHash = source.getSourceFileLocation().getAbsolutePath().hashCode();
        long timestamp = System.currentTimeMillis();
        String prefix = CloverNames.CLOVER_RECORDER_PREFIX +
                        Integer.toString(fileHash & 0x7FFFFFFF, 36) +
                        Long.toString(timestamp, 36);
        return prefix + "." + CloverNames.RECORDER_FIELD_NAME;
    }

    /**
     * Session-aware visitor that walks the AST and registers coverage points with the session.
     * Uses AST modification instead of text insertions.
     *
     * Ported from SessionAwareInstrumenter.SessionAwareVisitor to use AST modification.
     */
    private static class SessionAwareAstVisitor extends VoidVisitorAdapter<Void> {
        final InstrumentationSession session;
        private final JavaInstrumentationConfig config;
        private final String recorderPrefix;
        private final String initString;
        private final long registryVersion;
        private final ContextStore contextStore;
        private final List<DisabledRange> disabledRanges;
        private final HashSet<String> instrumentedLambdas = new HashSet<>();

        SessionAwareAstVisitor(InstrumentationSession session, JavaInstrumentationConfig config,
                              String recorderPrefix, String initString, long registryVersion,
                              ContextStore contextStore) {
            this.session = session;
            this.config = config;
            this.recorderPrefix = recorderPrefix;
            this.initString = initString;
            this.registryVersion = registryVersion;
            this.contextStore = contextStore;
            this.disabledRanges = new ArrayList<>();
        }

        private static class DisabledRange {
            final int startLine;
            final int endLine;

            DisabledRange(int startLine, int endLine) {
                this.startLine = startLine;
                this.endLine = endLine;
            }
        }

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

        private boolean isInstrumentationEnabled(int line) {
            for (DisabledRange range : disabledRanges) {
                if (line >= range.startLine && line <= range.endLine) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            Position begin = classDecl.getBegin().orElse(null);
            Position end = classDecl.getEnd().orElse(null);
            boolean isInterface = classDecl.isInterface();

            if (begin != null) {
                String className = classDecl.getNameAsString();
                FixedSourceRegion region = new FixedSourceRegion(
                        begin.line, begin.column,
                        end != null ? end.line : begin.line,
                        end != null ? end.column : begin.column);

                Modifiers mods = buildModifiers(classDecl);
                session.enterClass(className, region, mods, isInterface, false, false);
            }

            // Visit children
            super.visit(classDecl, arg);

            if (begin != null && end != null) {
                session.exitClass(end.line, end.column);
            }
        }

        @Override
        public void visit(EnumDeclaration enumDecl, Void arg) {
            Position begin = enumDecl.getBegin().orElse(null);
            Position end = enumDecl.getEnd().orElse(null);

            if (begin != null) {
                String enumName = enumDecl.getNameAsString();
                FixedSourceRegion region = new FixedSourceRegion(
                        begin.line, begin.column,
                        end != null ? end.line : begin.line,
                        end != null ? end.column : begin.column);

                Modifiers mods = buildModifiersEnum(enumDecl);
                session.enterClass(enumName, region, mods, false, true, false);
            }

            super.visit(enumDecl, arg);

            if (begin != null && end != null) {
                session.exitClass(end.line, end.column);
            }
        }

        @Override
        public void visit(AnnotationDeclaration annoDecl, Void arg) {
            Position begin = annoDecl.getBegin().orElse(null);
            Position end = annoDecl.getEnd().orElse(null);

            if (begin != null) {
                String annoName = annoDecl.getNameAsString();
                FixedSourceRegion region = new FixedSourceRegion(
                        begin.line, begin.column,
                        end != null ? end.line : begin.line,
                        end != null ? end.column : begin.column);

                Modifiers mods = buildModifiersAnnotation(annoDecl);
                session.enterClass(annoName, region, mods, false, false, true);
            }

            super.visit(annoDecl, arg);

            if (begin != null && end != null) {
                session.exitClass(end.line, end.column);
            }
        }

        @Override
        public void visit(RecordDeclaration recordDecl, Void arg) {
            Position begin = recordDecl.getBegin().orElse(null);
            Position end = recordDecl.getEnd().orElse(null);

            if (begin != null && isInstrumentationEnabled(begin.line)) {
                String recordName = recordDecl.getNameAsString();
                FixedSourceRegion region = new FixedSourceRegion(
                        begin.line, begin.column,
                        end != null ? end.line : begin.line,
                        end != null ? end.column : begin.column);

                Modifiers mods = buildModifiersRecord(recordDecl);
                session.enterClass(recordName, region, mods, false, false, false);
            }

            super.visit(recordDecl, arg);

            if (begin != null && end != null) {
                session.exitClass(end.line, end.column);
            }
        }

        private Modifiers buildModifiers(ClassOrInterfaceDeclaration classDecl) {
            long modMask = 0;
            if (classDecl.isPublic()) modMask |= Modifier.PUBLIC;
            if (classDecl.isPrivate()) modMask |= Modifier.PRIVATE;
            if (classDecl.isProtected()) modMask |= Modifier.PROTECTED;
            if (classDecl.isAbstract()) modMask |= Modifier.ABSTRACT;
            if (classDecl.isFinal()) modMask |= Modifier.FINAL;
            if (classDecl.isStatic()) modMask |= Modifier.STATIC;
            return Modifiers.createFrom(modMask, null);
        }

        private Modifiers buildModifiersEnum(EnumDeclaration enumDecl) {
            long modMask = 0;
            if (enumDecl.isPublic()) modMask |= Modifier.PUBLIC;
            if (enumDecl.isPrivate()) modMask |= Modifier.PRIVATE;
            if (enumDecl.isProtected()) modMask |= Modifier.PROTECTED;
            if (enumDecl.isStatic()) modMask |= Modifier.STATIC;
            return Modifiers.createFrom(modMask, null);
        }

        private Modifiers buildModifiersAnnotation(AnnotationDeclaration annoDecl) {
            long modMask = 0;
            if (annoDecl.isPublic()) modMask |= Modifier.PUBLIC;
            if (annoDecl.isPrivate()) modMask |= Modifier.PRIVATE;
            if (annoDecl.isProtected()) modMask |= Modifier.PROTECTED;
            if (annoDecl.isStatic()) modMask |= Modifier.STATIC;
            return Modifiers.createFrom(modMask, null);
        }

        private Modifiers buildModifiersRecord(RecordDeclaration recordDecl) {
            long modMask = 0;
            if (recordDecl.isPublic()) modMask |= Modifier.PUBLIC;
            if (recordDecl.isPrivate()) modMask |= Modifier.PRIVATE;
            if (recordDecl.isProtected()) modMask |= Modifier.PROTECTED;
            if (recordDecl.isFinal()) modMask |= Modifier.FINAL;
            if (recordDecl.isStatic()) modMask |= Modifier.STATIC;
            return Modifiers.createFrom(modMask, null);
        }

        // ========== METHOD INSTRUMENTATION ==========

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            Position begin = method.getBegin().orElse(null);
            Position end = method.getEnd().orElse(null);
            if (begin == null || !isInstrumentationEnabled(begin.line)) {
                super.visit(method, arg);
                return;
            }

            FixedSourceRegion region = new FixedSourceRegion(begin.line, begin.column);
            MethodSignature signature = buildMethodSignature(method);
            boolean isTest = isTestMethod(method);
            int complexity = calculateComplexity(method);

            String staticTestName = isTest ? method.getNameAsString() : null;
            ContextSetImpl methodContext = matchMethodContexts(method);
            MethodInfo methodInfo = session.enterMethod(
                    methodContext,
                    region, signature, isTest, staticTestName, false, complexity,
                    LanguageConstruct.Builtin.METHOD);

            method.getBody().ifPresent(body -> {
                // Instrument statements/branches FIRST, then add method entry R.inc
                // (otherwise instrumentBlock would try to instrument the R.inc itself)
                instrumentBlock(body);
                int methodIndex = methodInfo.getDataIndex();
                body.getStatements().addFirst(
                        StaticJavaParser.parseStatement(recorderPrefix + INC_PREFIX + methodIndex + INC_SUFFIX));

                // Wrap test methods with globalSliceStart/End
                if (isTest) {
                    String recorderBase = extractRecorderBase();
                    String recorderSuffix = extractRecorderSuffix();
                    int testIndex = methodInfo.getDataIndex();
                    // Must be ClassName.methodName — resolve() requires a dot to find the method
                    String enclosingClassName = method.findAncestor(ClassOrInterfaceDeclaration.class)
                            .map(ClassOrInterfaceDeclaration::getNameAsString).orElse(UNKNOWN_CLASS);
                    String methodName = enclosingClassName + "." + method.getNameAsString();

                    // For static test methods, getClass() is not available — use class name literal
                    String runtimeTypeExpr = method.isStatic()
                            ? QUOTE + method.findAncestor(ClassOrInterfaceDeclaration.class)
                                    .map(c -> c.getFullyQualifiedName().orElse(c.getNameAsString()))
                                    .orElse(UNKNOWN_CLASS) + QUOTE
                            : GET_CLASS_NAME;

                    // Move existing statements into a try block
                    NodeList<Statement> originalStmts = new NodeList<>(body.getStatements());
                    body.getStatements().clear();

                    // Create the try-catch-finally wrapper
                    BlockStmt tryBlock = new BlockStmt();
                    tryBlock.addStatement(StaticJavaParser.parseStatement(
                            recorderPrefix + ".globalSliceStart(" + runtimeTypeExpr + "," + testIndex + INC_SUFFIX));
                    originalStmts.forEach(tryBlock::addStatement);

                    // Catch block: record failure and rethrow
                    BlockStmt catchBlock = new BlockStmt();
                    catchBlock.addStatement(StaticJavaParser.parseStatement(
                            buildGlobalSliceEndStatement(runtimeTypeExpr, methodName, testIndex, 0, TEST_EXCEPTION_VAR)));
                    catchBlock.addStatement(StaticJavaParser.parseStatement("throw " + TEST_EXCEPTION_VAR + ";"));

                    CatchClause catchClause = new CatchClause(
                            new Parameter(
                                    new ClassOrInterfaceType(null, "Throwable"), TEST_EXCEPTION_VAR),
                            catchBlock);

                    // Finally block: record success
                    BlockStmt finallyBlock = new BlockStmt();
                    finallyBlock.addStatement(StaticJavaParser.parseStatement(
                            buildGlobalSliceEndStatement(runtimeTypeExpr, methodName, testIndex, 1, "null")));

                    TryStmt tryStmt = new TryStmt(
                            tryBlock,
                            new NodeList<>(catchClause),
                            finallyBlock);

                    body.addStatement(tryStmt);
                }

                // Recurse into nested type declarations (anonymous classes, local classes)
                // that instrumentBlock cannot reach
                instrumentNestedClasses(body);
            });

            if (end != null) {
                session.exitMethod(end.line, end.column);
            }
        }

        @Override
        public void visit(ConstructorDeclaration ctor, Void arg) {
            Position begin = ctor.getBegin().orElse(null);
            Position end = ctor.getEnd().orElse(null);
            if (begin == null || !isInstrumentationEnabled(begin.line)) {
                super.visit(ctor, arg);
                return;
            }

            FixedSourceRegion region = new FixedSourceRegion(begin.line, begin.column);
            MethodSignature signature = buildConstructorSignature(ctor);
            int complexity = calculateComplexityBlock(ctor.getBody());

            MethodInfo methodInfo = session.enterMethod(
                    new ContextSetImpl(),
                    region, signature, false, null, false, complexity,
                    LanguageConstruct.Builtin.METHOD);

            BlockStmt body = ctor.getBody();
            // Instrument statements/branches FIRST, then add constructor entry R.inc
            instrumentBlock(body);

            int insertPos = 0;
            List<Statement> stmts = body.getStatements();
            if (!stmts.isEmpty() && stmts.get(0) instanceof ExplicitConstructorInvocationStmt) {
                insertPos = 1;
            }
            int ctorIndex = methodInfo.getDataIndex();
            body.getStatements().add(insertPos,
                    StaticJavaParser.parseStatement(recorderPrefix + INC_PREFIX + ctorIndex + INC_SUFFIX));

            // Recurse into nested type declarations (anonymous classes, local classes)
            instrumentNestedClasses(body);

            if (end != null) {
                session.exitMethod(end.line, end.column);
            }
        }

        // ========== STATIC INITIALIZER INSTRUMENTATION ==========

        @Override
        public void visit(InitializerDeclaration initDecl, Void arg) {
            Position begin = initDecl.getBegin().orElse(null);
            Position end = initDecl.getEnd().orElse(null);
            if (begin == null || !isInstrumentationEnabled(begin.line)) {
                super.visit(initDecl, arg);
                return;
            }

            BlockStmt body = initDecl.getBody();
            instrumentBlock(body);

            // Recurse into nested type declarations (anonymous classes, local classes)
            instrumentNestedClasses(body);
        }

        // ========== LAMBDA INSTRUMENTATION ==========

        @Override
        public void visit(LambdaExpr lambda, Void arg) {
            // Track by position to prevent double-wrapping (VoidVisitorAdapter may
            // visit the cloned lambda inside the lambdaInc wrapper)
            String key = lambda.getBegin().map(p -> p.line + ":" + p.column).orElse("");
            if (instrumentedLambdas.contains(key) || isInsideLambdaIncWrapper(lambda)) {
                return;
            }
            Position begin = lambda.getBegin().orElse(null);
            if (begin == null || !isInstrumentationEnabled(begin.line)) {
                super.visit(lambda, arg);
                return;
            }
            instrumentedLambdas.add(key);

            // Register lambda as a method with the session
            FixedSourceRegion region = new FixedSourceRegion(begin.line, begin.column);
            MethodSignature sig = new MethodSignature("lambda", null, null, null, null,
                    Modifiers.createFrom(0, null));
            MethodInfo lambdaInfo = session.enterMethod(
                    new ContextSetImpl(), region, sig, false, null, true, 1,
                    LanguageConstruct.Builtin.METHOD);

            if (lambda.getBody().isBlockStmt()) {
                // Block lambda: instrument FIRST, then add R.inc (avoid re-instrumentation)
                BlockStmt body = lambda.getBody().asBlockStmt();
                instrumentBlock(body);
                int lambdaIndex = lambdaInfo.getDataIndex();
                body.getStatements().addFirst(
                        StaticJavaParser.parseStatement(recorderPrefix + INC_PREFIX + lambdaIndex + INC_SUFFIX));
            } else if (isSafeForLambdaIncWrapping(lambda)) {
                // Expression lambda in safe context: wrap with lambdaInc()
                FixedSourceRegion stmtRegion = new FixedSourceRegion(begin.line, begin.column);
                FullStatementInfo stmtInfo = session.addStatement(
                        new ContextSetImpl(), stmtRegion, 0, LanguageConstruct.Builtin.STATEMENT);
                int methodIndex = lambdaInfo.getDataIndex();
                int stmtIndex = stmtInfo.getDataIndex();
                String recorderBase = extractRecorderBase();

                // Build: lambdaInc(methodIndex, <original lambda>, stmtIndex)
                // lambdaInc is a static method on the OUTER class, not inside the __CLR inner class
                MethodCallExpr wrapper = new MethodCallExpr(
                        LAMBDA_INC_METHOD,
                        new IntegerLiteralExpr(String.valueOf(methodIndex)),
                        lambda.clone(),
                        new IntegerLiteralExpr(String.valueOf(stmtIndex)));
                lambda.replace(wrapper);
            }
            // Expression lambdas in unsafe contexts (method args, casts): skip wrapping

            Position end = lambda.getEnd().orElse(null);
            if (end != null) {
                session.exitMethod(end.line, end.column);
            }
        }

        // ========== TERNARY EXPRESSION BRANCH INSTRUMENTATION ==========

        @Override
        public void visit(ConditionalExpr ternary, Void arg) {
            // Visit nested expressions FIRST (process inner ternaries before outer)
            super.visit(ternary, arg);

            Position pos = ternary.getBegin().orElse(null);
            if (pos == null || !isInstrumentationEnabled(pos.line)) {
                return;
            }

            // Register branch with session
            FixedSourceRegion region = new FixedSourceRegion(pos.line, pos.column);
            FullBranchInfo branchInfo = session.addBranch(
                    new ContextSetImpl(),
                    region, true, 0,
                    LanguageConstruct.Builtin.BRANCH);
            if (branchInfo == null) {
                return;
            }

            int trueIndex = branchInfo.getDataIndex();
            int falseIndex = trueIndex + 1;

            // Wrap condition with iget pattern: (((condition)&&(R.iget(N)!=0|true))||(R.iget(N+1)==0&false))
            Expression condition = ternary.getCondition().clone();

            // Build R.iget(trueIndex)
            MethodCallExpr igetTrue = new MethodCallExpr(
                    new NameExpr(recorderPrefix),
                    IGET_METHOD,
                    new NodeList<>(new IntegerLiteralExpr(String.valueOf(trueIndex))));

            // Build R.iget(falseIndex)
            MethodCallExpr igetFalse = new MethodCallExpr(
                    new NameExpr(recorderPrefix),
                    IGET_METHOD,
                    new NodeList<>(new IntegerLiteralExpr(String.valueOf(falseIndex))));

            // Build: (R.iget(trueIndex) != 0 | true)
            BinaryExpr igetTrueCheck = new BinaryExpr(
                    new BinaryExpr(igetTrue, new IntegerLiteralExpr("0"), BinaryExpr.Operator.NOT_EQUALS),
                    new BooleanLiteralExpr(true),
                    BinaryExpr.Operator.BINARY_OR);

            // Build: (R.iget(falseIndex) == 0 & false)
            BinaryExpr igetFalseCheck = new BinaryExpr(
                    new BinaryExpr(igetFalse, new IntegerLiteralExpr("0"), BinaryExpr.Operator.EQUALS),
                    new BooleanLiteralExpr(false),
                    BinaryExpr.Operator.BINARY_AND);

            // Build: (condition && igetTrueCheck)
            BinaryExpr leftSide = new BinaryExpr(
                    new EnclosedExpr(condition),
                    igetTrueCheck,
                    BinaryExpr.Operator.AND);

            // Build: (leftSide || igetFalseCheck)
            BinaryExpr wrappedCondition = new BinaryExpr(
                    new EnclosedExpr(leftSide),
                    igetFalseCheck,
                    BinaryExpr.Operator.OR);

            // Replace the condition in the ternary
            ternary.setCondition(new EnclosedExpr(wrappedCondition));
        }

        // ========== TRY-WITH-RESOURCES INSTRUMENTATION ==========

        @Override
        public void visit(TryStmt tryStmt, Void arg) {
            // Only special-case try-with-resources
            if (!tryStmt.getResources().isEmpty()) {
                Position begin = tryStmt.getBegin().orElse(null);
                if (begin != null && isInstrumentationEnabled(begin.line)) {
                    // Register entry statement
                    FixedSourceRegion region = new FixedSourceRegion(begin.line, begin.column);
                    FullStatementInfo entryInfo = session.addStatement(
                            new ContextSetImpl(), region, 0,
                            LanguageConstruct.Builtin.STATEMENT);

                    // Insert R.inc(entryIndex) before the try statement in its parent block
                    int entryIndex = entryInfo.getDataIndex();
                    tryStmt.getParentNode().ifPresent(parent -> {
                        if (parent instanceof BlockStmt) {
                            BlockStmt parentBlock = (BlockStmt) parent;
                            int tryPos = parentBlock.getStatements().indexOf(tryStmt);
                            if (tryPos >= 0) {
                                parentBlock.getStatements().add(tryPos,
                                        StaticJavaParser.parseStatement(
                                                recorderPrefix + INC_PREFIX + entryIndex + INC_SUFFIX));
                            }
                        }
                    });

                    // Register cleanup statement and inject Tracker resource
                    Expression lastResource =
                            tryStmt.getResources().get(tryStmt.getResources().size() - 1);
                    Position lastResEnd = lastResource.getEnd().orElse(null);
                    if (lastResEnd != null) {
                        FixedSourceRegion closeRegion = new FixedSourceRegion(lastResEnd.line, lastResEnd.column);
                        FullStatementInfo closeInfo = session.addStatement(
                                new ContextSetImpl(), closeRegion, 0,
                                LanguageConstruct.Builtin.STATEMENT);
                        int closeIndex = closeInfo.getDataIndex();

                        // Add Tracker as a variable declaration resource:
                        // Type varName = new RecorderBase.Tracker(closeIndex)
                        String recorderBase = extractRecorderBase();
                        String trackerDecl = recorderBase + ".Tracker __CLR_rt" + closeIndex
                                + " = new " + recorderBase + ".Tracker(" + closeIndex + ")";
                        tryStmt.getResources().add(StaticJavaParser.parseVariableDeclarationExpr(trackerDecl));
                    }
                }
            }

            // Instrument the try body normally
            instrumentBlock(tryStmt.getTryBlock());

            // Instrument catch blocks
            for (CatchClause catchClause : tryStmt.getCatchClauses()) {
                instrumentBlock(catchClause.getBody());
            }

            // Instrument finally block if present
            tryStmt.getFinallyBlock().ifPresent(this::instrumentBlock);

            // Don't call super.visit — we handled children above
        }

        // ========== SWITCH EXPRESSION INSTRUMENTATION ==========

        @Override
        public void visit(SwitchEntry entry, Void arg) {
            List<Statement> statements = entry.getStatements();
            if (statements.isEmpty()) {
                super.visit(entry, arg);
                return;
            }

            Statement first = statements.get(0);
            Position pos = first.getBegin().orElse(null);
            if (pos == null || !isInstrumentationEnabled(pos.line)) {
                super.visit(entry, arg);
                return;
            }

            boolean isInSwitchExpr = entry.getParentNode()
                    .map(p -> p instanceof SwitchExpr).orElse(false);

            if (entry.getType() == SwitchEntry.Type.EXPRESSION
                    || entry.getType() == SwitchEntry.Type.THROWS_STATEMENT) {
                FixedSourceRegion region = new FixedSourceRegion(pos.line, pos.column);
                FullStatementInfo stmtInfo = session.addStatement(
                        new ContextSetImpl(), region, 1,
                        LanguageConstruct.Builtin.STATEMENT);
                int stmtIndex = stmtInfo.getDataIndex();

                if (isInSwitchExpr && !(first instanceof ThrowStmt) && first.isExpressionStmt()) {
                    // Switch EXPRESSION arrow case: preserve expression form with incRet()
                    // to avoid VerifyError from block-form yield changing javac's type inference.
                    Expression expr = first.asExpressionStmt().getExpression();
                    MethodCallExpr wrapper = new MethodCallExpr(
                            "incRet",
                            new IntegerLiteralExpr(String.valueOf(stmtIndex)),
                            expr.clone());
                    expr.replace(wrapper);
                } else {
                    // Switch STATEMENT arrow case, or throw: rewrite to block (safe, no type inference)
                    BlockStmt block = new BlockStmt();
                    block.addStatement(StaticJavaParser.parseStatement(
                            recorderPrefix + INC_PREFIX + stmtIndex + INC_SUFFIX));
                    block.addStatement(first.clone());
                    statements.clear();
                    statements.add(block);
                    entry.setType(SwitchEntry.Type.BLOCK);
                }
            } else if (entry.getType() == SwitchEntry.Type.BLOCK) {
                // Arrow-case with block: case X -> { ... }
                if (first instanceof BlockStmt) {
                    BlockStmt existingBlock = (BlockStmt) first;
                    instrumentBlock(existingBlock);
                }
            } else {
                // Colon-case: case X: stmt; — insert R.inc before first statement
                if (isExecutableStatement(first)) {
                    FixedSourceRegion region = new FixedSourceRegion(pos.line, pos.column);
                    FullStatementInfo stmtInfo = session.addStatement(
                            new ContextSetImpl(), region, 0,
                            LanguageConstruct.Builtin.STATEMENT);
                    int stmtIndex = stmtInfo.getDataIndex();
                    statements.add(0,
                            StaticJavaParser.parseStatement(
                                    recorderPrefix + INC_PREFIX + stmtIndex + INC_SUFFIX));
                }
            }
            super.visit(entry, arg);
        }

        // ========== STATEMENT & BRANCH INSTRUMENTATION ==========

        private void instrumentBlock(BlockStmt block) {
            int i = 0;
            while (i < block.getStatements().size()) {
                Statement stmt = block.getStatement(i);

                if (stmt instanceof IfStmt) {
                    instrumentIf((IfStmt) stmt);
                    i++;
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
                } else if (stmt instanceof TryStmt) {
                    visit((TryStmt) stmt, null);
                    i = block.getStatements().indexOf(stmt) + 1;
                    if (i <= 0) i = block.getStatements().size();
                } else if (stmt instanceof SwitchStmt) {
                    for (SwitchEntry entry : ((SwitchStmt) stmt).getEntries()) {
                        visit(entry, null);
                    }
                    i++;
                } else if (stmt instanceof SynchronizedStmt) {
                    instrumentBlock(((SynchronizedStmt) stmt).getBody());
                    i++;
                } else if (stmt instanceof LabeledStmt) {
                    Statement inner = ((LabeledStmt) stmt).getStatement();
                    if (inner instanceof IfStmt) {
                        instrumentIf((IfStmt) inner);
                    } else if (inner instanceof WhileStmt) {
                        instrumentLoopBody(((WhileStmt) inner).getBody());
                    } else if (inner instanceof ForStmt) {
                        instrumentLoopBody(((ForStmt) inner).getBody());
                    } else if (inner instanceof DoStmt) {
                        instrumentLoopBody(((DoStmt) inner).getBody());
                    } else if (inner instanceof ForEachStmt) {
                        instrumentLoopBody(((ForEachStmt) inner).getBody());
                    } else if (inner instanceof SwitchStmt) {
                        for (SwitchEntry entry : ((SwitchStmt) inner).getEntries()) {
                            visit(entry, null);
                        }
                    } else if (inner instanceof BlockStmt) {
                        instrumentBlock((BlockStmt) inner);
                    }
                    i++;
                } else if (stmt instanceof BlockStmt) {
                    instrumentBlock((BlockStmt) stmt);
                    i++;
                } else if (isExecutableStatement(stmt)) {
                    Position pos = stmt.getBegin().orElse(null);
                    if (pos != null && isInstrumentationEnabled(pos.line)) {
                        FixedSourceRegion region = new FixedSourceRegion(pos.line, pos.column);
                        ContextSetImpl stmtContext = matchStatementContexts(stmt);
                        FullStatementInfo stmtInfo = session.addStatement(
                                stmtContext,
                                region, 0,
                                LanguageConstruct.Builtin.STATEMENT);
                        int stmtIndex = stmtInfo.getDataIndex();
                        block.getStatements().add(i,
                                StaticJavaParser.parseStatement(recorderPrefix + INC_PREFIX + stmtIndex + INC_SUFFIX));
                        i += 2;
                    } else {
                        i++;
                    }
                } else {
                    i++;
                }
            }

            // Scan entire block for constructs nested inside expressions
            // that instrumentBlock can't reach (lambdas, method refs, switch expressions, ternaries)
            instrumentNestedLambdas(block);
            instrumentNestedSwitchExpressions(block);
            instrumentNestedTernaries(block);
        }

        /**
         * Scans a node's subtree for nested ClassOrInterfaceDeclaration nodes
         * (local classes) and ObjectCreationExpr with anonymous class bodies,
         * and visits them.
         */
        private void instrumentNestedClasses(Node node) {
            // Handle local classes
            for (ClassOrInterfaceDeclaration nestedClass : node.findAll(ClassOrInterfaceDeclaration.class)) {
                visit(nestedClass, null);
            }
            // Handle anonymous classes
            for (ObjectCreationExpr objCreation : node.findAll(ObjectCreationExpr.class)) {
                if (objCreation.getAnonymousClassBody().isPresent()) {
                    // Visit the anonymous class as if it were a class declaration
                    // The visitor will process its methods
                    for (BodyDeclaration<?> member : objCreation.getAnonymousClassBody().get()) {
                        if (member instanceof MethodDeclaration) {
                            visit((MethodDeclaration) member, null);
                        } else if (member instanceof ConstructorDeclaration) {
                            visit((ConstructorDeclaration) member, null);
                        } else if (member instanceof InitializerDeclaration) {
                            visit((InitializerDeclaration) member, null);
                        }
                    }
                }
            }
        }

        /**
         * Scans a node's subtree for LambdaExpr and MethodReferenceExpr nodes
         * not already instrumented (nested inside expressions).
         */
        private void instrumentNestedLambdas(Node node) {
            for (LambdaExpr lambda : node.findAll(LambdaExpr.class)) {
                // Skip if already inside a lambdaInc wrapper (prevents double-wrapping)
                if (isInsideLambdaIncWrapper(lambda)) {
                    continue;
                }
                // Skip if block body already has R.inc (already instrumented)
                if (lambda.getBody().isBlockStmt()) {
                    BlockStmt body = lambda.getBody().asBlockStmt();
                    if (!body.getStatements().isEmpty()
                            && body.getStatement(0).toString().contains(INC_PREFIX)) {
                        continue;
                    }
                }
                visit(lambda, null);
            }
            for (MethodReferenceExpr methodRef :
                    node.findAll(MethodReferenceExpr.class)) {
                if (!isInsideLambdaIncWrapper(methodRef)) {
                    instrumentMethodReference(methodRef);
                }
            }
        }

        /**
         * Scans for SwitchExpr nodes inside expressions (return values, assignments, etc.)
         * and instruments their entries.
         */
        private void instrumentNestedSwitchExpressions(Node node) {
            for (SwitchExpr switchExpr : node.findAll(SwitchExpr.class)) {
                for (SwitchEntry entry : switchExpr.getEntries()) {
                    visit(entry, null);
                }
            }
        }

        /**
         * Scans for ConditionalExpr (ternary) nodes inside expressions and instruments them.
         */
        private void instrumentNestedTernaries(Node node) {
            for (ConditionalExpr ternary : node.findAll(ConditionalExpr.class)) {
                visit(ternary, null);
            }
        }

        private boolean isInsideLambdaIncWrapper(Node node) {
            return node.getParentNode()
                    .filter(p -> p instanceof MethodCallExpr)
                    .map(p -> ((MethodCallExpr) p).getNameAsString())
                    .filter(LAMBDA_INC_METHOD::equals)
                    .isPresent();
        }

        /**
         * Instruments a method reference (e.g., this::process, String::valueOf)
         * by registering it as a lambda method with the session.
         */
        private void instrumentMethodReference(MethodReferenceExpr methodRef) {
            Position begin = methodRef.getBegin().orElse(null);
            if (begin == null || !isInstrumentationEnabled(begin.line)) return;

            FixedSourceRegion region = new FixedSourceRegion(begin.line, begin.column);
            String refName = "methodRef$" + methodRef.getIdentifier();
            MethodSignature sig = new MethodSignature(refName, null, null, null, null,
                    Modifiers.createFrom(0, null));
            MethodInfo refInfo = session.enterMethod(
                    new ContextSetImpl(), region, sig, false, null, true, 1,
                    LanguageConstruct.Builtin.METHOD);

            // Wrap with lambdaInc if in safe context
            if (isSafeForLambdaIncWrapping(methodRef)) {
                FullStatementInfo stmtInfo = session.addStatement(
                        new ContextSetImpl(), region, 0, LanguageConstruct.Builtin.STATEMENT);
                int methodIndex = refInfo.getDataIndex();
                int stmtIndex = stmtInfo.getDataIndex();
                String recorderBase = extractRecorderBase();

                // lambdaInc is a static method on the OUTER class, not the __CLR inner class
                MethodCallExpr wrapper = new MethodCallExpr(
                        LAMBDA_INC_METHOD,
                        new IntegerLiteralExpr(String.valueOf(methodIndex)),
                        methodRef.clone(),
                        new IntegerLiteralExpr(String.valueOf(stmtIndex)));
                methodRef.replace(wrapper);
            }

            Position end = methodRef.getEnd().orElse(null);
            if (end != null) {
                session.exitMethod(end.line, end.column);
            }
        }

        private boolean isSafeForLambdaIncWrapping(Expression expr) {
            if (!expr.getParentNode().isPresent()) {
                return false;
            }
            Node parent = expr.getParentNode().get();
            String parentType = parent.getClass().getSimpleName();
            return VARIABLE_DECLARATOR.equals(parentType) || ASSIGN_EXPR.equals(parentType);
        }

        private void instrumentIf(IfStmt ifStmt) {
            Position pos = ifStmt.getBegin().orElse(null);
            if (pos == null || !isInstrumentationEnabled(pos.line)) return;

            FixedSourceRegion region = new FixedSourceRegion(pos.line, pos.column);
            FullBranchInfo branchInfo = session.addBranch(
                    new ContextSetImpl(),
                    region, true, 0,
                    LanguageConstruct.Builtin.BRANCH);
            if (branchInfo == null) return;

            int trueIndex = branchInfo.getDataIndex();
            int falseIndex = trueIndex + 1;

            // True branch — instrument FIRST, then add R.inc (avoid re-instrumentation)
            Statement thenStmt = ifStmt.getThenStmt();
            if (thenStmt instanceof BlockStmt) {
                BlockStmt thenBlock = (BlockStmt) thenStmt;
                instrumentBlock(thenBlock);
                thenBlock.getStatements().addFirst(
                        StaticJavaParser.parseStatement(recorderPrefix + INC_PREFIX + trueIndex + INC_SUFFIX));
            } else {
                BlockStmt wrapper = new BlockStmt();
                wrapper.addStatement(StaticJavaParser.parseStatement(recorderPrefix + INC_PREFIX + trueIndex + INC_SUFFIX));
                wrapper.addStatement(thenStmt.clone());
                ifStmt.setThenStmt(wrapper);
            }

            // False branch
            if (ifStmt.getElseStmt().isPresent()) {
                Statement elseStmt = ifStmt.getElseStmt().get();
                if (elseStmt instanceof BlockStmt) {
                    BlockStmt elseBlock = (BlockStmt) elseStmt;
                    instrumentBlock(elseBlock);
                    elseBlock.getStatements().addFirst(
                            StaticJavaParser.parseStatement(recorderPrefix + INC_PREFIX + falseIndex + INC_SUFFIX));
                } else if (elseStmt instanceof IfStmt) {
                    BlockStmt wrapper = new BlockStmt();
                    wrapper.addStatement(StaticJavaParser.parseStatement(recorderPrefix + INC_PREFIX +falseIndex + INC_SUFFIX));
                    wrapper.addStatement(elseStmt.clone());
                    ifStmt.setElseStmt(wrapper);
                    instrumentIf((IfStmt) wrapper.getStatement(1));
                } else {
                    BlockStmt wrapper = new BlockStmt();
                    wrapper.addStatement(StaticJavaParser.parseStatement(recorderPrefix + INC_PREFIX +falseIndex + INC_SUFFIX));
                    wrapper.addStatement(elseStmt.clone());
                    ifStmt.setElseStmt(wrapper);
                }
            } else {
                // Synthetic else — LPP workaround: replace entire IfStmt
                BlockStmt syntheticElse = new BlockStmt();
                syntheticElse.addStatement(
                        StaticJavaParser.parseStatement(recorderPrefix + INC_PREFIX +falseIndex + INC_SUFFIX));
                IfStmt newIf = new IfStmt(
                        ifStmt.getCondition().clone(),
                        ifStmt.getThenStmt().clone(),
                        syntheticElse);
                ifStmt.replace(newIf);
            }
        }

        private void instrumentLoopBody(Statement body) {
            Position pos = body.getBegin().orElse(null);
            if (pos == null || !isInstrumentationEnabled(pos.line)) return;

            FixedSourceRegion region = new FixedSourceRegion(pos.line, pos.column);
            FullBranchInfo branchInfo = session.addBranch(
                    new ContextSetImpl(),
                    region, true, 0,
                    LanguageConstruct.Builtin.BRANCH);
            if (branchInfo == null) return;

            int branchIndex = branchInfo.getDataIndex();
            if (body instanceof BlockStmt) {
                BlockStmt block = (BlockStmt) body;
                instrumentBlock(block);
                block.getStatements().addFirst(
                        StaticJavaParser.parseStatement(recorderPrefix + INC_PREFIX + branchIndex + INC_SUFFIX));
            } else {
                // Braceless loop body: wrap in block with R.inc
                BlockStmt wrapper = new BlockStmt();
                wrapper.addStatement(
                        StaticJavaParser.parseStatement(recorderPrefix + INC_PREFIX + branchIndex + INC_SUFFIX));
                wrapper.addStatement(body.clone());
                // Replace the body on the parent loop
                if (body.getParentNode().isPresent()) {
                    Node parent = body.getParentNode().get();
                    if (parent instanceof WhileStmt) {
                        ((WhileStmt) parent).setBody(wrapper);
                    } else if (parent instanceof ForStmt) {
                        ((ForStmt) parent).setBody(wrapper);
                    } else if (parent instanceof DoStmt) {
                        ((DoStmt) parent).setBody(wrapper);
                    } else if (parent instanceof ForEachStmt) {
                        ((ForEachStmt) parent).setBody(wrapper);
                    }
                }
            }
        }

        // ========== TEST DETECTION ==========

        private boolean isSafeForLambdaIncWrapping(LambdaExpr lambda) {
            if (!lambda.getParentNode().isPresent()) {
                return false;
            }
            Node parent = lambda.getParentNode().get();
            String parentType = parent.getClass().getSimpleName();
            return VARIABLE_DECLARATOR.equals(parentType) || ASSIGN_EXPR.equals(parentType);
        }

        private boolean isTestMethod(MethodDeclaration method) {
            for (AnnotationExpr ann : method.getAnnotations()) {
                String name = ann.getNameAsString();
                if ("Test".equals(name) || "ParameterizedTest".equals(name)
                        || name.endsWith(".Test") || name.endsWith(".ParameterizedTest")) {
                    return true;
                }
            }
            // JUnit 3 style: method name starts with "test" in a TestCase subclass
            if (method.getNameAsString().startsWith("test")) {
                return method.findAncestor(ClassOrInterfaceDeclaration.class)
                        .map(this::isTestClass).orElse(false);
            }
            return false;
        }

        private boolean isTestClass(ClassOrInterfaceDeclaration classDecl) {
            for (ClassOrInterfaceType ext : classDecl.getExtendedTypes()) {
                String name = ext.getNameAsString();
                if ("TestCase".equals(name) || name.endsWith(".TestCase")) {
                    return true;
                }
            }
            return false;
        }

        // ========== COMPLEXITY CALCULATION ==========

        private int calculateComplexity(MethodDeclaration method) {
            return method.getBody().map(this::calculateComplexityBlock).orElse(1);
        }

        private int calculateComplexityBlock(BlockStmt block) {
            int complexity = 1; // Base complexity
            complexity += block.findAll(IfStmt.class).size();
            complexity += block.findAll(WhileStmt.class).size();
            complexity += block.findAll(ForStmt.class).size();
            complexity += block.findAll(ForEachStmt.class).size();
            complexity += block.findAll(DoStmt.class).size();
            complexity += block.findAll(CatchClause.class).size();
            complexity += block.findAll(ConditionalExpr.class).size();
            complexity += block.findAll(BinaryExpr.class).stream()
                    .filter(b -> b.getOperator() == BinaryExpr.Operator.AND
                            || b.getOperator() == BinaryExpr.Operator.OR)
                    .count();
            return complexity;
        }

        // ========== HELPERS ==========

        private MethodSignature buildMethodSignature(MethodDeclaration method) {
            String name = method.getNameAsString();
            String returnType = method.getType().asString();
            long modMask = 0;
            if (method.isPublic()) modMask |= Modifier.PUBLIC;
            if (method.isPrivate()) modMask |= Modifier.PRIVATE;
            if (method.isProtected()) modMask |= Modifier.PROTECTED;
            if (method.isStatic()) modMask |= Modifier.STATIC;
            if (method.isAbstract()) modMask |= Modifier.ABSTRACT;
            if (method.isFinal()) modMask |= Modifier.FINAL;
            if (method.isSynchronized()) modMask |= Modifier.SYNCHRONIZED;
            if (method.isNative()) modMask |= Modifier.NATIVE;
            Modifiers mods = Modifiers.createFrom(modMask, null);

            // Extract parameters
            org.openclover.core.registry.entities.Parameter[] parameters = extractParameters(method.getParameters());

            // Extract type parameters (generics like <T, E>)
            String typeParams = method.getTypeParameters().isEmpty() ? null :
                    method.getTypeParameters().toString();

            // Extract throws types
            String[] throwsTypes = method.getThrownExceptions().isEmpty() ? null :
                    method.getThrownExceptions().stream()
                            .map(type -> type.asString())
                            .toArray(String[]::new);

            return new MethodSignature(name, typeParams, returnType, parameters, throwsTypes, mods);
        }

        private MethodSignature buildConstructorSignature(ConstructorDeclaration ctor) {
            String name = ctor.getNameAsString();
            long modMask = 0;
            if (ctor.isPublic()) modMask |= Modifier.PUBLIC;
            if (ctor.isPrivate()) modMask |= Modifier.PRIVATE;
            if (ctor.isProtected()) modMask |= Modifier.PROTECTED;
            Modifiers mods = Modifiers.createFrom(modMask, null);

            // Extract parameters
            org.openclover.core.registry.entities.Parameter[] parameters = extractParameters(ctor.getParameters());

            // Extract type parameters (generics like <T>)
            String typeParams = ctor.getTypeParameters().isEmpty() ? null :
                    ctor.getTypeParameters().toString();

            // Extract throws types
            String[] throwsTypes = ctor.getThrownExceptions().isEmpty() ? null :
                    ctor.getThrownExceptions().stream()
                            .map(type -> type.asString())
                            .toArray(String[]::new);

            return new MethodSignature(name, typeParams, null, parameters, throwsTypes, mods);
        }

        /**
         * Extracts parameters from JavaParser's Parameter list and converts them to Clover's Parameter type.
         * Handles varargs (e.g., Object...) and generic types (e.g., List<String>).
         */
        private org.openclover.core.registry.entities.Parameter[] extractParameters(NodeList<Parameter> javaParserParams) {
            if (javaParserParams.isEmpty()) {
                return null;
            }

            org.openclover.core.registry.entities.Parameter[] parameters =
                    new org.openclover.core.registry.entities.Parameter[javaParserParams.size()];

            for (int i = 0; i < javaParserParams.size(); i++) {
                Parameter param = javaParserParams.get(i);
                String type = param.getTypeAsString();
                String name = param.getNameAsString();

                // Handle varargs: JavaParser returns type without "..." but param.isVarArgs() is true
                if (param.isVarArgs() && !type.endsWith(VARARGS_SUFFIX)) {
                    // Remove trailing [] if present (JavaParser may represent varargs as array)
                    if (type.endsWith("[]")) {
                        type = type.substring(0, type.length() - 2);
                    }
                    type = type + VARARGS_SUFFIX;
                }

                parameters[i] = new org.openclover.core.registry.entities.Parameter(type, name);
            }

            return parameters;
        }

        // ========== CONTEXT MATCHING ==========

        private ContextSetImpl matchMethodContexts(MethodDeclaration method) {
            ContextSetImpl ctx = new ContextSetImpl();
            if (contextStore == null) return ctx;
            String normalizedSig = method.getDeclarationAsString(true, true, true);
            for (MethodRegexpContext mctx : contextStore.getMethodContexts()) {
                if (mctx.matches(normalizedSig)) {
                    ctx.set(mctx.getIndex());
                }
            }
            return ctx;
        }

        private ContextSetImpl matchStatementContexts(Statement stmt) {
            ContextSetImpl ctx = new ContextSetImpl();
            if (contextStore == null) return ctx;
            String normalizedStmt = stmt.toString().replaceAll("\\s+", " ").trim();
            for (StatementRegexpContext sctx : contextStore.getStatementContexts()) {
                if (sctx.matches(normalizedStmt)) {
                    ctx.set(sctx.getIndex());
                }
            }
            return ctx;
        }

        private String buildGlobalSliceEndStatement(String runtimeTypeExpr, String methodName,
                                                     int testIndex, int passedFlag, String exceptionVar) {
            return recorderPrefix + ".globalSliceEnd(" + runtimeTypeExpr + ",\"" + methodName + "\","
                    + CloverNames.CLOVER_TEST_NAME_SNIFFER + ".getTestName(),"
                    + testIndex + "," + passedFlag + "," + exceptionVar + INC_SUFFIX;
        }

        private boolean isExecutableStatement(Statement stmt) {
            return stmt.isExpressionStmt()
                    || stmt.isReturnStmt()
                    || stmt.isThrowStmt()
                    || stmt.isAssertStmt()
                    || stmt.isBreakStmt()
                    || stmt.isContinueStmt();
        }

        String generateRecorderCode(int maxDataIndex, JavaInstrumentationConfig config) {
            RecorderCodeGenerator.RecorderConfig cfg = new RecorderCodeGenerator.RecorderConfig();
            cfg.recorderBase = extractRecorderBase();
            cfg.recorderSuffix = extractRecorderSuffix();
            cfg.initString = initString;
            cfg.registryVersion = registryVersion;
            cfg.areLambdasSupported = true;
            cfg.recorderCfg = org_openclover_runtime.CoverageRecorder.getConfigBits(
                    config.getFlushPolicy(),
                    config.getFlushInterval(),
                    false,
                    false,
                    !config.isSliceRecording());
            cfg.maxDataIndex = maxDataIndex;
            cfg.distributedConfig = config.getDistributedConfigString();
            cfg.profiles = config.getProfiles();
            return RecorderCodeGenerator.generate(cfg);
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

    /**
     * Visitor that instruments methods, statements, and branches via AST modification.
     * Used by the standalone test API.
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
                // Instrument statements/branches FIRST, then add method entry R.inc
                // (avoids re-instrumentation of the R.inc itself)
                instrumentBlock(body);
                int methodIndex = indexCounter.getAndIncrement();
                body.getStatements().addFirst(parseInc(methodIndex));
            });
        }

        @Override
        public void visit(ConstructorDeclaration ctor, Void arg) {
            BlockStmt body = ctor.getBody();

            // Instrument statements/branches FIRST, then add constructor entry R.inc
            instrumentBlock(body);

            int ctorIndex = indexCounter.getAndIncrement();
            List<Statement> stmts = body.getStatements();
            int insertPos = 0;
            if (!stmts.isEmpty() && stmts.get(0) instanceof ExplicitConstructorInvocationStmt) {
                insertPos = 1;
            }
            body.getStatements().add(insertPos, parseInc(ctorIndex));
        }

        @Override
        public void visit(InitializerDeclaration initDecl, Void arg) {
            instrumentBlock(initDecl.getBody());
        }

        @Override
        public void visit(SwitchEntry entry, Void arg) {
            List<Statement> stmts = entry.getStatements();
            if (stmts.isEmpty()) {
                super.visit(entry, arg);
                return;
            }

            if (entry.getType() == SwitchEntry.Type.BLOCK) {
                // Arrow-case with block: case X -> { ... }
                if (stmts.get(0) instanceof BlockStmt) {
                    BlockStmt block = (BlockStmt) stmts.get(0);
                    int idx = indexCounter.getAndIncrement();
                    block.getStatements().addFirst(parseInc(idx));
                    instrumentBlock(block);
                }
            } else if (entry.getType() == SwitchEntry.Type.STATEMENT_GROUP) {
                // Colon-case: case X: stmt; — instrument first statement
                if (isExecutableStatement(stmts.get(0))) {
                    int idx = indexCounter.getAndIncrement();
                    stmts.add(0, parseInc(idx));
                }
            }
            // Arrow expression/throw cases: these need text-level rewriting that
            // LPP doesn't support well (yield is context-sensitive). Handled by
            // the session-aware visitor in production. For standalone tests, the
            // switch entries are left as-is — the method body still gets R.inc.
            super.visit(entry, arg);
        }

        private void instrumentBlock(BlockStmt block) {
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
                } else if (stmt instanceof SwitchStmt) {
                    for (SwitchEntry entry : ((SwitchStmt) stmt).getEntries()) {
                        visit(entry, null);
                    }
                    i++;
                } else if (stmt instanceof SynchronizedStmt) {
                    instrumentBlock(((SynchronizedStmt) stmt).getBody());
                    i++;
                } else if (stmt instanceof LabeledStmt) {
                    Statement inner = ((LabeledStmt) stmt).getStatement();
                    if (inner instanceof IfStmt) {
                        instrumentIf((IfStmt) inner);
                    } else if (inner instanceof WhileStmt) {
                        instrumentLoopBody(((WhileStmt) inner).getBody());
                    } else if (inner instanceof ForStmt) {
                        instrumentLoopBody(((ForStmt) inner).getBody());
                    } else if (inner instanceof DoStmt) {
                        instrumentLoopBody(((DoStmt) inner).getBody());
                    } else if (inner instanceof ForEachStmt) {
                        instrumentLoopBody(((ForEachStmt) inner).getBody());
                    } else if (inner instanceof SwitchStmt) {
                        for (SwitchEntry entry : ((SwitchStmt) inner).getEntries()) {
                            visit(entry, null);
                        }
                    } else if (inner instanceof BlockStmt) {
                        instrumentBlock((BlockStmt) inner);
                    }
                    i++;
                } else if (stmt instanceof BlockStmt) {
                    instrumentBlock((BlockStmt) stmt);
                    i++;
                } else if (isExecutableStatement(stmt)) {
                    int stmtIndex = indexCounter.getAndIncrement();
                    block.getStatements().add(i, parseInc(stmtIndex));
                    i += 2;
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
            } else {
                // Braceless loop body: wrap in block with R.inc
                BlockStmt wrapper = new BlockStmt();
                wrapper.addStatement(parseInc(branchIndex));
                wrapper.addStatement(body.clone());
                // Replace the body on the parent loop
                if (body.getParentNode().isPresent()) {
                    Node parent = body.getParentNode().get();
                    if (parent instanceof WhileStmt) {
                        ((WhileStmt) parent).setBody(wrapper);
                    } else if (parent instanceof ForStmt) {
                        ((ForStmt) parent).setBody(wrapper);
                    } else if (parent instanceof DoStmt) {
                        ((DoStmt) parent).setBody(wrapper);
                    } else if (parent instanceof ForEachStmt) {
                        ((ForEachStmt) parent).setBody(wrapper);
                    }
                }
            }
        }

        private Statement parseInc(int index) {
            return StaticJavaParser.parseStatement(prefix + INC_PREFIX + index + INC_SUFFIX);
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
