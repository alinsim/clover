package org.openclover.core.reporters.agent;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extracts branch condition expressions from Java source files using JavaParser.
 * Maps line numbers to the actual condition text for richer branch coverage reporting.
 *
 * Example: line 42 with an uncovered "false" branch might yield:
 * {@code "userContext == null"} — telling the agent exactly what condition to test.
 */
public class BranchConditionExtractor {

    private static final ParserConfiguration PARSER_CONFIG = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);

    /**
     * Extract all branch conditions from a source file.
     *
     * @param sourceFile the Java source file
     * @return list of branch conditions with line numbers
     */
    public List<BranchCondition> extractConditions(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            return Collections.emptyList();
        }

        try {
            String source = new String(Files.readAllBytes(sourceFile.toPath()), StandardCharsets.UTF_8);
            return extractConditions(source);
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Extract all branch conditions from source code string.
     *
     * @param source Java source code
     * @return list of branch conditions with line numbers
     */
    public List<BranchCondition> extractConditions(String source) {
        JavaParser parser = new JavaParser(PARSER_CONFIG);
        CompilationUnit cu = parser.parse(source).getResult().orElse(null);
        if (cu == null) {
            return Collections.emptyList();
        }

        List<BranchCondition> conditions = new ArrayList<>();

        cu.findAll(IfStmt.class).forEach(ifStmt ->
            ifStmt.getBegin().ifPresent(pos -> {
                BranchCondition bc = new BranchCondition();
                bc.line = pos.line;
                bc.condition = ifStmt.getCondition().toString();
                bc.construct = "if";
                conditions.add(bc);
            })
        );

        cu.findAll(WhileStmt.class).forEach(whileStmt ->
            whileStmt.getBegin().ifPresent(pos -> {
                BranchCondition bc = new BranchCondition();
                bc.line = pos.line;
                bc.condition = whileStmt.getCondition().toString();
                bc.construct = "while";
                conditions.add(bc);
            })
        );

        cu.findAll(ForStmt.class).forEach(forStmt ->
            forStmt.getBegin().ifPresent(pos -> {
                BranchCondition bc = new BranchCondition();
                bc.line = pos.line;
                bc.condition = forStmt.getCompare()
                        .map(Object::toString).orElse("");
                bc.construct = "for";
                conditions.add(bc);
            })
        );

        cu.findAll(ForEachStmt.class).forEach(forEachStmt ->
            forEachStmt.getBegin().ifPresent(pos -> {
                BranchCondition bc = new BranchCondition();
                bc.line = pos.line;
                bc.condition = forEachStmt.getIterable().toString();
                bc.construct = "for-each";
                conditions.add(bc);
            })
        );

        cu.findAll(DoStmt.class).forEach(doStmt ->
            doStmt.getBegin().ifPresent(pos -> {
                BranchCondition bc = new BranchCondition();
                bc.line = pos.line;
                bc.condition = doStmt.getCondition().toString();
                bc.construct = "do-while";
                conditions.add(bc);
            })
        );

        conditions.sort((a, b) -> Integer.compare(a.line, b.line));
        return conditions;
    }

    /**
     * Get the condition expression for a specific branch line.
     *
     * @param sourceFile the source file
     * @param line the line number
     * @return condition string, or null if not found
     */
    public String getConditionAtLine(File sourceFile, int line) {
        for (BranchCondition bc : extractConditions(sourceFile)) {
            if (bc.line == line) {
                return bc.condition;
            }
        }
        return null;
    }

    /**
     * A branch condition extracted from source.
     */
    public static class BranchCondition {
        public int line;
        public String condition;
        public String construct;
    }
}
