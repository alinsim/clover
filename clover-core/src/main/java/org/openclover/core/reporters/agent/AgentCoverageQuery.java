package org.openclover.core.reporters.agent;

import org.openclover.core.CloverDatabase;
import org.openclover.core.CoverageData;
import org.openclover.core.api.registry.BranchInfo;
import org.openclover.core.api.registry.ClassInfo;
import org.openclover.core.api.registry.ElementVisitor;
import org.openclover.core.api.registry.FileInfo;
import org.openclover.core.api.registry.HasMetricsFilter;
import org.openclover.core.api.registry.MethodInfo;
import org.openclover.core.api.registry.PackageInfo;
import org.openclover.core.api.registry.ProjectInfo;
import org.openclover.core.api.registry.StatementInfo;
import org.openclover.core.api.registry.TestCaseInfo;
import org.openclover.core.registry.entities.FullFileInfo;
import org.openclover.core.registry.metrics.BlockMetrics;
import org.openclover.core.registry.metrics.ClassMetrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Facade for querying agent coverage data from CloverDatabase.
 * Converts internal Clover data structures (BitSets, data indices) into
 * line-number-oriented results suitable for JSON serialization and AI agent consumption.
 */
public class AgentCoverageQuery {

    private static final String STATUS_PASS = "pass";
    private static final String STATUS_ERROR = "error";
    private static final String STATUS_FAIL = "fail";
    private static final String STATUS_UNKNOWN = "unknown";
    private static final String BRANCH_TRUE = "true";
    private static final String BRANCH_FALSE = "false";
    private static final String JAVA_SUFFIX = ".java";
    private static final long MS_PER_SECOND = 1000L;

    private final CloverDatabase database;

    public AgentCoverageQuery(CloverDatabase database) {
        this.database = database;
    }

    /**
     * Get coverage for a specific test.
     * @param testQualifiedName qualified test name like "MyTest.testFoo" (with package prefix)
     * @return test coverage result, or null if test not found
     */
    public TestCoverageResult getTestCoverage(String testQualifiedName) {
        CoverageData coverageData = database.getCoverageData();

        TestCaseInfo tci = findTestByQualifiedName(coverageData, testQualifiedName);
        if (tci == null) {
            return null;
        }

        TestCoverageResult result = new TestCoverageResult();
        result.testName = testQualifiedName;
        result.status = getTestStatus(tci);
        result.durationMs = (long) (tci.getDuration() * MS_PER_SECOND);
        result.coverage = new HashMap<>();
        result.uniqueCoverage = new HashMap<>();

        BitSet hits = coverageData.getHitsFor(tci);
        BitSet uniqueHits = coverageData.getUniqueHitsFor(tci);

        for (FullFileInfo fileInfo : getAllFiles()) {
            FileCoverage fileCov = extractFileCoverage(fileInfo, hits);
            if (!fileCov.linesCovered.isEmpty() || !fileCov.branchesCovered.isEmpty()) {
                result.coverage.put(fileInfo.getPackagePath(), fileCov);
            }

            List<Integer> uniqueLines = extractCoveredLines(fileInfo, uniqueHits);
            if (!uniqueLines.isEmpty()) {
                result.uniqueCoverage.put(fileInfo.getPackagePath(), uniqueLines);
            }
        }

        return result;
    }

    /**
     * Get uncovered elements for a specific file.
     * @param classNameOrPath simple class name or package path
     * @return file uncovered result, or null if file not found
     */
    public FileUncoveredResult getFileUncovered(String classNameOrPath) {
        FullFileInfo fileInfo = findFileByName(classNameOrPath);
        if (fileInfo == null) {
            return null;
        }

        FileUncoveredResult result = new FileUncoveredResult();
        result.file = fileInfo.getPackagePath();
        result.totalLines = fileInfo.getLineCount();

        BlockMetrics metrics = (BlockMetrics) fileInfo.getMetrics();
        result.coverage = createCoverageMetrics(metrics);

        result.uncoveredLines = extractUncoveredLines(fileInfo);
        result.uncoveredBranches = extractUncoveredBranches(fileInfo);
        result.coveredBy = extractCoveredByMapping(fileInfo);

        return result;
    }

    /**
     * Get project-level coverage summary (combined app + test).
     */
    public CoverageSummary getProjectSummary() {
        ProjectInfo project = database.getRegistry().getProject();
        BlockMetrics metrics = (BlockMetrics) project.getMetrics();

        CoverageSummary summary = new CoverageSummary();
        summary.metrics = createCoverageMetrics(metrics);

        return summary;
    }

    /**
     * Get project coverage split into app (production), test, and combined sections.
     * This prevents the common mistake of inflating coverage by including test source coverage.
     */
    public SplitCoverageSummary getProjectSummarySplit() {
        SplitCoverageSummary split = new SplitCoverageSummary();

        ProjectInfo appModel = database.getAppOnlyModel();
        ProjectInfo testModel = database.getTestOnlyModel();
        ProjectInfo fullModel = database.getFullModel();

        split.app = createCoverageMetrics(
                appModel != null ? (BlockMetrics) appModel.getMetrics() : null);
        split.test = createCoverageMetrics(
                testModel != null ? (BlockMetrics) testModel.getMetrics() : null);
        split.combined = createCoverageMetrics(
                fullModel != null ? (BlockMetrics) fullModel.getMetrics() : null);

        return split;
    }

    /**
     * Get all tests with their basic statistics.
     */
    public List<TestSummary> getAllTests() {
        CoverageData coverageData = database.getCoverageData();
        List<TestSummary> summaries = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        for (TestCaseInfo tci : coverageData.getTests()) {
            String qualifiedName = tci.getQualifiedName();

            // Bug 1: Skip tests with null qualified names
            if (qualifiedName == null) {
                continue;
            }

            // Bug 1: Deduplicate by qualified name
            if (seenNames.contains(qualifiedName)) {
                continue;
            }
            seenNames.add(qualifiedName);

            TestSummary summary = new TestSummary();
            summary.name = qualifiedName;
            summary.status = getTestStatus(tci);
            // Bug 4: Use Math.round() instead of cast to avoid truncating sub-millisecond durations to 0
            summary.durationMs = Math.round(tci.getDuration() * MS_PER_SECOND);

            BitSet hits = coverageData.getHitsFor(tci);
            BitSet uniqueHits = coverageData.getUniqueHitsFor(tci);

            // Count distinct source lines, not raw slot count
            summary.linesCovered = countDistinctCoveredLines(hits);
            summary.uniqueLinesCovered = countDistinctCoveredLines(uniqueHits);

            summaries.add(summary);
        }

        return summaries;
    }

    // --- Private helpers ---

    private List<FullFileInfo> getAllFiles() {
        ProjectInfo project = database.getRegistry().getProject();
        List<FileInfo> files = project.getFiles(HasMetricsFilter.ACCEPT_ALL);
        List<FullFileInfo> result = new ArrayList<>(files.size());
        for (FileInfo fi : files) {
            result.add((FullFileInfo) fi);
        }
        return result;
    }

    private TestCaseInfo findTestByQualifiedName(CoverageData coverageData, String qualifiedName) {
        for (TestCaseInfo tci : coverageData.getTests()) {
            if (qualifiedName.equals(tci.getQualifiedName())) {
                return tci;
            }
        }
        return null;
    }

    private FullFileInfo findFileByName(String nameOrPath) {
        for (FullFileInfo fullFileInfo : getAllFiles()) {
            String fileName = fullFileInfo.getName();
            if (fileName != null) {
                String baseName = fileName.endsWith(JAVA_SUFFIX)
                        ? fileName.substring(0, fileName.length() - JAVA_SUFFIX.length())
                        : fileName;
                if (baseName.equalsIgnoreCase(nameOrPath)
                        || nameOrPath.equalsIgnoreCase(fileName)) {
                    return fullFileInfo;
                }
            }

            if (fullFileInfo.getPackagePath() != null
                    && fullFileInfo.getPackagePath().equals(nameOrPath)) {
                return fullFileInfo;
            }
        }

        return null;
    }

    private String getTestStatus(TestCaseInfo tci) {
        if (tci.isSuccess()) {
            return STATUS_PASS;
        } else if (tci.isError()) {
            return STATUS_ERROR;
        } else if (tci.isFailure()) {
            return STATUS_FAIL;
        }
        return STATUS_UNKNOWN;
    }

    private FileCoverage extractFileCoverage(FullFileInfo fileInfo, BitSet hits) {
        FileCoverage coverage = new FileCoverage();
        coverage.linesCovered = extractCoveredLines(fileInfo, hits);
        coverage.branchesCovered = extractCoveredBranches(fileInfo, hits);
        return coverage;
    }

    /**
     * Convert BitSet hits to sorted, deduplicated source line numbers for a file.
     */
    private List<Integer> extractCoveredLines(FullFileInfo fileInfo, BitSet hits) {
        Set<Integer> lineSet = new HashSet<>();
        int dataStart = fileInfo.getDataIndex();
        int dataEnd = dataStart + fileInfo.getDataLength();

        fileInfo.visitElements(new ElementVisitor() {
            @Override
            public void visitClass(ClassInfo info) { }

            @Override
            public void visitMethod(MethodInfo info) { }

            @Override
            public void visitStatement(StatementInfo element) {
                int idx = element.getDataIndex();
                if (idx >= dataStart && idx < dataEnd && hits.get(idx)) {
                    lineSet.add(element.getStartLine());
                }
            }

            @Override
            public void visitBranch(BranchInfo element) {
                int idx = element.getDataIndex();
                if (idx >= dataStart && idx < dataEnd) {
                    if (hits.get(idx) || hits.get(idx + 1)) {
                        lineSet.add(element.getStartLine());
                    }
                }
            }
        });

        List<Integer> lines = new ArrayList<>(lineSet);
        lines.sort(Integer::compareTo);
        return lines;
    }

    /**
     * Extract branch coverage detail from a BitSet for a file.
     */
    private List<BranchCoverage> extractCoveredBranches(FullFileInfo fileInfo, BitSet hits) {
        List<BranchCoverage> branches = new ArrayList<>();
        int dataStart = fileInfo.getDataIndex();
        int dataEnd = dataStart + fileInfo.getDataLength();

        fileInfo.visitElements(new ElementVisitor() {
            @Override
            public void visitClass(ClassInfo info) { }

            @Override
            public void visitMethod(MethodInfo info) { }

            @Override
            public void visitStatement(StatementInfo element) { }

            @Override
            public void visitBranch(BranchInfo element) {
                int idx = element.getDataIndex();
                if (idx >= dataStart && idx < dataEnd) {
                    if (hits.get(idx)) {
                        BranchCoverage bc = new BranchCoverage();
                        bc.line = element.getStartLine();
                        bc.type = BRANCH_TRUE;
                        branches.add(bc);
                    }
                    if (hits.get(idx + 1)) {
                        BranchCoverage bc = new BranchCoverage();
                        bc.line = element.getStartLine();
                        bc.type = BRANCH_FALSE;
                        branches.add(bc);
                    }
                }
            }
        });

        return branches;
    }

    /**
     * Find uncovered source lines (statements with hitCount == 0).
     */
    private List<Integer> extractUncoveredLines(FullFileInfo fileInfo) {
        Set<Integer> uncoveredSet = new HashSet<>();

        fileInfo.visitElements(new ElementVisitor() {
            @Override
            public void visitClass(ClassInfo info) { }

            @Override
            public void visitMethod(MethodInfo info) { }

            @Override
            public void visitStatement(StatementInfo element) {
                if (element.getHitCount() == 0) {
                    uncoveredSet.add(element.getStartLine());
                }
            }

            @Override
            public void visitBranch(BranchInfo element) {
                if (element.getTrueHitCount() == 0 && element.getFalseHitCount() == 0) {
                    uncoveredSet.add(element.getStartLine());
                }
            }
        });

        List<Integer> uncovered = new ArrayList<>(uncoveredSet);
        uncovered.sort(Integer::compareTo);
        return uncovered;
    }

    /**
     * Find uncovered branches with detail (line, true/false, enclosing method).
     */
    private List<UncoveredBranch> extractUncoveredBranches(FullFileInfo fileInfo) {
        List<UncoveredBranch> branches = new ArrayList<>();

        for (ClassInfo classInfo : fileInfo.getClasses()) {
            for (MethodInfo methodInfo : classInfo.getMethods()) {
                String methodName = methodInfo.getSimpleName();
                for (BranchInfo branch : methodInfo.getBranches()) {
                    if (branch.getTrueHitCount() == 0) {
                        UncoveredBranch ub = new UncoveredBranch();
                        ub.line = branch.getStartLine();
                        ub.type = BRANCH_TRUE;
                        ub.method = methodName;
                        branches.add(ub);
                    }
                    if (branch.getFalseHitCount() == 0) {
                        UncoveredBranch ub = new UncoveredBranch();
                        ub.line = branch.getStartLine();
                        ub.type = BRANCH_FALSE;
                        ub.method = methodName;
                        branches.add(ub);
                    }
                }
            }
        }

        return branches;
    }

    /**
     * Build coveredBy mapping: line range -> list of test names covering that range.
     * Uses per-test coverage data from the database.
     */
    private Map<String, List<String>> extractCoveredByMapping(FullFileInfo fileInfo) {
        Map<String, List<String>> coveredBy = new HashMap<>();

        Map<TestCaseInfo, BitSet> testCoverage = database.getCoverageData()
                .mapTestsAndCoverageForFile(fileInfo);

        if (testCoverage == null || testCoverage.isEmpty()) {
            return coveredBy;
        }

        // For each test, find which lines it covers in this file
        Map<Integer, Set<String>> lineToTests = new HashMap<>();
        for (Map.Entry<TestCaseInfo, BitSet> entry : testCoverage.entrySet()) {
            String testName = entry.getKey().getQualifiedName();
            List<Integer> lines = extractCoveredLines(fileInfo, entry.getValue());
            for (Integer line : lines) {
                lineToTests.computeIfAbsent(line, k -> new HashSet<>()).add(testName);
            }
        }

        // Group contiguous lines with the same test set into ranges
        List<Integer> sortedLines = new ArrayList<>(lineToTests.keySet());
        sortedLines.sort(Integer::compareTo);

        if (sortedLines.isEmpty()) {
            return coveredBy;
        }

        int rangeStart = sortedLines.get(0);
        int rangePrev = rangeStart;
        Set<String> rangeTests = lineToTests.get(rangeStart);

        for (int i = 1; i < sortedLines.size(); i++) {
            int line = sortedLines.get(i);
            Set<String> tests = lineToTests.get(line);

            if (line == rangePrev + 1 && tests.equals(rangeTests)) {
                rangePrev = line;
            } else {
                String rangeKey = rangeStart == rangePrev
                        ? String.valueOf(rangeStart)
                        : rangeStart + "-" + rangePrev;
                coveredBy.put(rangeKey, new ArrayList<>(rangeTests));
                rangeStart = line;
                rangePrev = line;
                rangeTests = tests;
            }
        }

        // Final range
        String rangeKey = rangeStart == rangePrev
                ? String.valueOf(rangeStart)
                : rangeStart + "-" + rangePrev;
        coveredBy.put(rangeKey, new ArrayList<>(rangeTests));

        return coveredBy;
    }

    /**
     * Count distinct source lines covered by a BitSet across all files.
     * This avoids overcounting when multiple statements are on the same line.
     */
    private int countDistinctCoveredLines(BitSet hits) {
        Set<String> fileLineKeys = new HashSet<>();
        for (FullFileInfo fileInfo : getAllFiles()) {
            List<Integer> lines = extractCoveredLines(fileInfo, hits);
            for (Integer line : lines) {
                fileLineKeys.add(fileInfo.getPackagePath() + ":" + line);
            }
        }
        return fileLineKeys.size();
    }

    private CoverageMetrics createCoverageMetrics(BlockMetrics metrics) {
        CoverageMetrics coverage = new CoverageMetrics();

        if (metrics == null) {
            coverage.statements = new MetricPair();
            coverage.branches = new MetricPair();
            coverage.methods = new MetricPair();
            return coverage;
        }

        coverage.statements = new MetricPair();
        coverage.statements.covered = metrics.getNumCoveredStatements();
        coverage.statements.total = metrics.getNumStatements();
        coverage.statements.pct = metrics.getPcCoveredStatements() * 100.0;

        coverage.branches = new MetricPair();
        coverage.branches.covered = metrics.getNumCoveredBranches();
        coverage.branches.total = metrics.getNumBranches();
        coverage.branches.pct = metrics.getPcCoveredBranches() * 100.0;

        coverage.methods = new MetricPair();
        if (metrics instanceof ClassMetrics) {
            ClassMetrics classMetrics = (ClassMetrics) metrics;
            coverage.methods.covered = classMetrics.getNumCoveredMethods();
            coverage.methods.total = classMetrics.getNumMethods();
            coverage.methods.pct = classMetrics.getPcCoveredMethods() * 100.0;
        }

        return coverage;
    }

    /**
     * Get method-level test suggestions for a file, ranked by uncovered line count.
     *
     * @param classNameOrPath class name or path to query
     * @param maxSuggestions max number of suggestions
     * @return ranked list of methods with uncovered lines/branches
     */
    public List<MethodSuggestion> getMethodSuggestions(String classNameOrPath, int maxSuggestions) {
        List<MethodSuggestion> suggestions = new ArrayList<>();
        FullFileInfo fileInfo = findFileByName(classNameOrPath);
        if (fileInfo == null) {
            return suggestions;
        }

        CoverageData coverageData = database.getCoverageData();
        int rank = 0;

        for (ClassInfo classInfo : fileInfo.getClasses()) {
            for (MethodInfo method : classInfo.getMethods()) {
                List<Integer> uncoveredLines = new ArrayList<>();
                List<UncoveredBranch> uncoveredBranches = new ArrayList<>();

                for (StatementInfo stmt : method.getStatements()) {
                    if (coverageData != null && coverageData.getHitCount(stmt.getDataIndex()) == 0) {
                        uncoveredLines.add(stmt.getStartLine());
                    }
                }

                for (BranchInfo branch : method.getBranches()) {
                    if (branch.getTrueHitCount() == 0) {
                        UncoveredBranch ub = new UncoveredBranch();
                        ub.line = branch.getStartLine();
                        ub.type = BRANCH_TRUE;
                        ub.method = method.getSimpleName();
                        uncoveredBranches.add(ub);
                    }
                    if (branch.getFalseHitCount() == 0) {
                        UncoveredBranch ub = new UncoveredBranch();
                        ub.line = branch.getStartLine();
                        ub.type = BRANCH_FALSE;
                        ub.method = method.getSimpleName();
                        uncoveredBranches.add(ub);
                    }
                }

                if (!uncoveredLines.isEmpty() || !uncoveredBranches.isEmpty()) {
                    MethodSuggestion ms = new MethodSuggestion();
                    ms.method = method.getSimpleName();
                    ms.uncoveredLines = uncoveredLines;
                    ms.uncoveredBranches = uncoveredBranches;
                    ms.complexity = method.getMetrics().getComplexity();
                    BlockMetrics metrics = (BlockMetrics) method.getMetrics();
                    ms.coveredPct = metrics.getNumStatements() > 0
                            ? (metrics.getNumCoveredStatements() * 100.0 / metrics.getNumStatements())
                            : 0.0;
                    suggestions.add(ms);
                }
            }
        }

        // Sort by uncovered line count descending
        suggestions.sort((a, b) -> Integer.compare(b.uncoveredLines.size(), a.uncoveredLines.size()));

        // Assign ranks and trim
        List<MethodSuggestion> result = new ArrayList<>();
        for (MethodSuggestion ms : suggestions) {
            if (rank >= maxSuggestions) break;
            ms.rank = ++rank;
            result.add(ms);
        }
        return result;
    }

    // ==================== ENRICHED FEEDBACK QUERIES ====================

    private static final Set<String> UNTESTABLE_METHODS = new HashSet<>(
            Arrays.asList("main", "start", "stop", "deploy", "undeploy", "init", "destroy"));

    /**
     * Get enriched uncovered files for the feedback file.
     * Each entry contains everything an agent needs to act — no follow-up queries required:
     * method breakdown, existing tests, branchesOnCoveredLines, quickWinScore, testability.
     *
     * @param maxFiles max files to return
     * @return enriched entries sorted by quickWinScore descending (best targets first)
     */
    public List<EnrichedFileUncovered> getEnrichedUncoveredFiles(int maxFiles) {
        List<EnrichedFileUncovered> results = new ArrayList<>();
        CoverageData coverageData = database.getCoverageData();

        ProjectInfo appModel = database.getAppOnlyModel();
        if (appModel == null) {
            return results;
        }

        for (PackageInfo pkg : appModel.getAllPackages()) {
            for (FileInfo file : pkg.getFiles()) {
                if (!(file instanceof FullFileInfo)) continue;
                FullFileInfo fullFile = (FullFileInfo) file;

                List<Integer> uncoveredLines = extractUncoveredLines(fullFile);
                List<UncoveredBranch> uncoveredBranches = extractUncoveredBranches(fullFile);
                if (uncoveredLines.isEmpty() && uncoveredBranches.isEmpty()) continue;

                EnrichedFileUncovered entry = new EnrichedFileUncovered();
                entry.file = fullFile.getPackagePath();
                entry.coverage = createCoverageMetrics((BlockMetrics) fullFile.getMetrics());
                entry.uncoveredLines = uncoveredLines;
                entry.uncoveredBranches = uncoveredBranches;

                // Method-level breakdown
                entry.uncoveredMethods = buildMethodSuggestions(fullFile, coverageData);

                // Existing test classes from coveredBy mapping
                entry.existingTests = extractExistingTestClasses(fullFile, coverageData);
                entry.hasExistingTests = !entry.existingTests.isEmpty();

                // Branches on covered lines — the quick-win signal
                entry.branchesOnCoveredLines = countBranchesOnCoveredLines(fullFile, coverageData);

                // Average complexity of uncovered methods
                entry.avgComplexity = computeAvgComplexity(entry.uncoveredMethods);

                // Testability: are the uncovered methods likely testable?
                entry.likelyTestable = assessTestability(fullFile, coverageData);

                // Quick-win score: higher = better target for agent
                entry.quickWinScore = computeQuickWinScore(entry);

                results.add(entry);
            }
        }

        results.sort((a, b) -> Double.compare(b.quickWinScore, a.quickWinScore));

        if (results.size() > maxFiles) {
            return results.subList(0, maxFiles);
        }
        return results;
    }

    /**
     * Get files sorted by quickWinScore — the "where should I start?" list.
     */
    public List<EnrichedFileUncovered> getQuickWins(int maxFiles) {
        List<EnrichedFileUncovered> all = getEnrichedUncoveredFiles(maxFiles * 3);
        List<EnrichedFileUncovered> quickWins = new ArrayList<>();

        for (EnrichedFileUncovered entry : all) {
            // Quick wins: has existing tests, has branches on covered lines, testable
            if (entry.hasExistingTests && entry.branchesOnCoveredLines > 0 && entry.likelyTestable) {
                quickWins.add(entry);
                if (quickWins.size() >= maxFiles) break;
            }
        }

        return quickWins;
    }

    /**
     * Get all tests sorted by uniqueLinesCovered descending.
     */
    public List<TestSummary> getAllTestsSorted() {
        List<TestSummary> tests = getAllTests();
        tests.sort((a, b) -> Integer.compare(b.uniqueLinesCovered, a.uniqueLinesCovered));
        return tests;
    }

    // ==================== ENRICHMENT HELPERS ====================

    private List<MethodSuggestion> buildMethodSuggestions(FullFileInfo fileInfo, CoverageData coverageData) {
        List<MethodSuggestion> suggestions = new ArrayList<>();
        int rank = 0;

        for (ClassInfo classInfo : fileInfo.getClasses()) {
            for (MethodInfo method : classInfo.getMethods()) {
                List<Integer> uncLines = new ArrayList<>();
                List<UncoveredBranch> uncBranches = new ArrayList<>();

                for (StatementInfo stmt : method.getStatements()) {
                    if (coverageData != null && coverageData.getHitCount(stmt.getDataIndex()) == 0) {
                        uncLines.add(stmt.getStartLine());
                    }
                }
                for (BranchInfo branch : method.getBranches()) {
                    if (branch.getTrueHitCount() == 0) {
                        UncoveredBranch ub = new UncoveredBranch();
                        ub.line = branch.getStartLine();
                        ub.type = BRANCH_TRUE;
                        ub.method = method.getSimpleName();
                        uncBranches.add(ub);
                    }
                    if (branch.getFalseHitCount() == 0) {
                        UncoveredBranch ub = new UncoveredBranch();
                        ub.line = branch.getStartLine();
                        ub.type = BRANCH_FALSE;
                        ub.method = method.getSimpleName();
                        uncBranches.add(ub);
                    }
                }

                if (!uncLines.isEmpty() || !uncBranches.isEmpty()) {
                    MethodSuggestion ms = new MethodSuggestion();
                    ms.rank = ++rank;
                    ms.method = method.getSimpleName();
                    ms.uncoveredLines = uncLines;
                    ms.uncoveredBranches = uncBranches;
                    ms.complexity = method.getMetrics().getComplexity();
                    BlockMetrics metrics = (BlockMetrics) method.getMetrics();
                    ms.coveredPct = metrics.getNumStatements() > 0
                            ? (metrics.getNumCoveredStatements() * 100.0 / metrics.getNumStatements())
                            : 0.0;
                    suggestions.add(ms);
                }
            }
        }

        suggestions.sort((a, b) -> Integer.compare(b.uncoveredLines.size(), a.uncoveredLines.size()));
        rank = 0;
        for (MethodSuggestion ms : suggestions) {
            ms.rank = ++rank;
        }
        return suggestions;
    }

    private List<String> extractExistingTestClasses(FullFileInfo fileInfo, CoverageData coverageData) {
        Set<String> testClasses = new LinkedHashSet<>();
        if (coverageData == null) return new ArrayList<>(testClasses);

        Map<TestCaseInfo, BitSet> testCoverage = coverageData.mapTestsAndCoverageForFile(fileInfo);
        for (TestCaseInfo tci : testCoverage.keySet()) {
            String qn = tci.getQualifiedName();
            if (qn != null) {
                // Extract class name from "com.example.MyTest.testMethod"
                int lastDot = qn.lastIndexOf('.');
                String className = lastDot > 0 ? qn.substring(0, lastDot) : qn;
                // Simplify to just the class name (not FQ)
                int pkgDot = className.lastIndexOf('.');
                testClasses.add(pkgDot > 0 ? className.substring(pkgDot + 1) : className);
            }
        }
        return new ArrayList<>(testClasses);
    }

    private int countBranchesOnCoveredLines(FullFileInfo fileInfo, CoverageData coverageData) {
        if (coverageData == null) return 0;

        // Build set of covered lines
        Set<Integer> coveredLines = new HashSet<>();
        for (ClassInfo classInfo : fileInfo.getClasses()) {
            for (MethodInfo method : classInfo.getMethods()) {
                for (StatementInfo stmt : method.getStatements()) {
                    if (coverageData.getHitCount(stmt.getDataIndex()) > 0) {
                        coveredLines.add(stmt.getStartLine());
                    }
                }
            }
        }

        // Count uncovered branches on covered lines
        int count = 0;
        for (ClassInfo classInfo : fileInfo.getClasses()) {
            for (MethodInfo method : classInfo.getMethods()) {
                for (BranchInfo branch : method.getBranches()) {
                    int line = branch.getStartLine();
                    if (coveredLines.contains(line)) {
                        if (branch.getTrueHitCount() == 0) count++;
                        if (branch.getFalseHitCount() == 0) count++;
                    }
                }
            }
        }
        return count;
    }

    private double computeAvgComplexity(List<MethodSuggestion> methods) {
        if (methods.isEmpty()) return 0.0;
        double sum = 0;
        for (MethodSuggestion ms : methods) {
            sum += ms.complexity;
        }
        return sum / methods.size();
    }

    private boolean assessTestability(FullFileInfo fileInfo, CoverageData coverageData) {
        // A file is "likely testable" if it has at least one non-infrastructure method
        for (ClassInfo classInfo : fileInfo.getClasses()) {
            for (MethodInfo method : classInfo.getMethods()) {
                String name = method.getSimpleName();
                if (!UNTESTABLE_METHODS.contains(name) && !name.startsWith("<")) {
                    return true;
                }
            }
        }
        return false;
    }

    private double computeQuickWinScore(EnrichedFileUncovered entry) {
        double score = 0;

        // Existing tests = can extend existing test classes (major boost)
        if (entry.hasExistingTests) score += 30;

        // Branches on covered lines = reachable code needing one more test case
        score += entry.branchesOnCoveredLines * 5;

        // Partial coverage = not starting from scratch
        if (entry.coverage != null && entry.coverage.statements != null) {
            double pct = entry.coverage.statements.pct;
            if (pct > 30 && pct < 90) score += 20;
        }

        // Low complexity = easier to test
        if (entry.avgComplexity > 0 && entry.avgComplexity <= 5) score += 15;
        else if (entry.avgComplexity <= 10) score += 5;

        // Testable code
        if (entry.likelyTestable) score += 10;

        return score;
    }
}
