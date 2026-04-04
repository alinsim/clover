package org.openclover.core.reporters.agent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates structured test target hints for uncovered branches.
 * Combines coverage data with source AST context to produce hints
 * that guide an AI agent in writing targeted tests.
 *
 * Example hint: "Method processOrder has uncovered false branch at line 45:
 * condition 'order.getStatus() == PENDING'. Test when order status is not PENDING."
 */
public class TestTargetHintGenerator {

    private static final String PREFIX_TEST_THE = "Test the ";
    private static final String PREFIX_TEST_WHEN = "Test when condition '";

    private final AgentCoverageQuery query;
    private final BranchConditionExtractor conditionExtractor;

    public TestTargetHintGenerator(AgentCoverageQuery query) {
        this.query = query;
        this.conditionExtractor = new BranchConditionExtractor();
    }

    /**
     * Generate test hints for uncovered branches in a file.
     *
     * @param classNameOrPath class name or path
     * @param sourceFile source file for condition extraction (nullable)
     * @param maxHints max hints to return
     * @return list of structured hints
     */
    public List<TestTargetHint> generateHints(String classNameOrPath, File sourceFile, int maxHints) {
        List<TestTargetHint> hints = new ArrayList<>();

        FileUncoveredResult uncovered = query.getFileUncovered(classNameOrPath);
        if (uncovered == null) {
            return hints;
        }

        List<BranchConditionExtractor.BranchCondition> conditions =
                sourceFile != null ? conditionExtractor.extractConditions(sourceFile) : new ArrayList<>();

        for (UncoveredBranch branch : uncovered.uncoveredBranches) {
            if (hints.size() >= maxHints) break;

            TestTargetHint hint = new TestTargetHint();
            hint.line = branch.line;
            hint.branchType = branch.type;
            hint.method = branch.method;

            // Find condition expression from AST
            String condition = findConditionForLine(conditions, branch.line);
            if (condition != null) {
                hint.condition = condition;
                hint.suggestion = buildSuggestion(branch.type, condition, branch.method);
            } else {
                hint.suggestion = PREFIX_TEST_THE + branch.type + " branch at line " + branch.line
                        + " in method " + branch.method;
            }

            hints.add(hint);
        }

        return hints;
    }

    private String findConditionForLine(List<BranchConditionExtractor.BranchCondition> conditions, int line) {
        for (BranchConditionExtractor.BranchCondition bc : conditions) {
            if (bc.line == line) {
                return bc.condition;
            }
        }
        return null;
    }

    private String buildSuggestion(String branchType, String condition, String method) {
        if ("false".equals(branchType)) {
            return PREFIX_TEST_WHEN + condition + "' is false in " + method;
        } else if ("true".equals(branchType)) {
            return PREFIX_TEST_WHEN + condition + "' is true in " + method;
        }
        return PREFIX_TEST_THE + branchType + " path for '" + condition + "' in " + method;
    }

    /**
     * A structured hint for a test target.
     */
    public static class TestTargetHint {
        public int line;
        public String branchType;
        public String method;
        public String condition;
        public String suggestion;
    }
}
