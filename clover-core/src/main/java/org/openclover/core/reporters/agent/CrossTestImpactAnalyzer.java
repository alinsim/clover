package org.openclover.core.reporters.agent;

import org.openclover.core.CloverDatabase;
import org.openclover.core.CoverageData;
import org.openclover.core.api.registry.ClassInfo;
import org.openclover.core.api.registry.FileInfo;
import org.openclover.core.api.registry.MethodInfo;
import org.openclover.core.api.registry.PackageInfo;
import org.openclover.core.api.registry.StatementInfo;
import org.openclover.core.api.registry.TestCaseInfo;
import org.openclover.core.registry.entities.FullFileInfo;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Identifies which tests are affected when production code changes.
 * Given a set of changed lines in a file, returns the tests that cover those lines.
 * This enables targeted test execution — only re-run tests affected by the change.
 */
public class CrossTestImpactAnalyzer {

    private final CloverDatabase database;

    public CrossTestImpactAnalyzer(CloverDatabase database) {
        this.database = database;
    }

    /**
     * Find all tests that cover any of the changed lines in a file.
     *
     * @param classNameOrPath class name or file path
     * @param changedLines set of changed line numbers (1-based)
     * @return list of affected test names
     */
    public List<String> getAffectedTests(String classNameOrPath, Set<Integer> changedLines) {
        CoverageData coverageData = database.getCoverageData();
        if (coverageData == null || changedLines.isEmpty()) {
            return Collections.emptyList();
        }

        FullFileInfo fileInfo = findFile(classNameOrPath);
        if (fileInfo == null) {
            return Collections.emptyList();
        }

        // Get data indices for the changed lines
        Set<Integer> changedIndices = new HashSet<>();
        for (ClassInfo classInfo : fileInfo.getClasses()) {
            for (MethodInfo method : classInfo.getMethods()) {
                for (StatementInfo stmt : method.getStatements()) {
                    if (changedLines.contains(stmt.getStartLine())) {
                        changedIndices.add(stmt.getDataIndex());
                    }
                }
            }
        }

        if (changedIndices.isEmpty()) {
            return Collections.emptyList();
        }

        // Find tests that cover any of the changed indices
        Set<String> affectedTests = new HashSet<>();
        Map<TestCaseInfo, BitSet> testCoverage = coverageData.mapTestsAndCoverageForFile(fileInfo);
        for (Map.Entry<TestCaseInfo, BitSet> entry : testCoverage.entrySet()) {
            BitSet hits = entry.getValue();
            for (int idx : changedIndices) {
                if (hits.get(idx)) {
                    affectedTests.add(entry.getKey().getQualifiedName());
                    break;
                }
            }
        }

        List<String> result = new ArrayList<>(affectedTests);
        Collections.sort(result);
        return result;
    }

    private FullFileInfo findFile(String classNameOrPath) {
        for (PackageInfo pkg : database.getFullModel().getAllPackages()) {
            for (FileInfo file : pkg.getFiles()) {
                if (file instanceof FullFileInfo) {
                    String name = file.getPhysicalFile() != null ? file.getPhysicalFile().getName() : file.getName();
                    if (name.equals(classNameOrPath) || name.endsWith(classNameOrPath)
                            || name.replace(".java", "").endsWith(classNameOrPath)) {
                        return (FullFileInfo) file;
                    }
                }
            }
        }
        return null;
    }
}
