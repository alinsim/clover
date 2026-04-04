package org.openclover.core.reporters.agent;

import org.openclover.core.CloverDatabase;
import org.openclover.core.api.registry.ClassInfo;
import org.openclover.core.api.registry.FileInfo;
import org.openclover.core.api.registry.MethodInfo;
import org.openclover.core.api.registry.PackageInfo;
import org.openclover.core.registry.metrics.BlockMetrics;

import java.util.ArrayList;
import java.util.List;

/**
 * Scores methods by risk: high complexity combined with low coverage.
 * Risk = complexity * (1 - coveragePct/100). Methods with high complexity
 * and low coverage are the most dangerous — they have many code paths
 * but few are tested.
 *
 * This helps agents prioritize which methods to test first for maximum
 * risk reduction.
 */
public class ComplexityRiskScorer {

    private final CloverDatabase database;

    public ComplexityRiskScorer(CloverDatabase database) {
        this.database = database;
    }

    /**
     * Score all methods by complexity-weighted coverage risk.
     *
     * @param maxResults max number of results to return
     * @return methods sorted by risk score descending (riskiest first)
     */
    public List<MethodRiskScore> scoreAllMethods(int maxResults) {
        List<MethodRiskScore> scores = new ArrayList<>();

        for (PackageInfo pkg : database.getFullModel().getAllPackages()) {
            for (FileInfo file : pkg.getFiles()) {
                String fileName = file.getPhysicalFile() != null
                        ? file.getPhysicalFile().getName() : file.getName();
                for (ClassInfo classInfo : file.getClasses()) {
                    for (MethodInfo method : classInfo.getMethods()) {
                        BlockMetrics metrics = (BlockMetrics) method.getMetrics();
                        int complexity = metrics.getComplexity();
                        double coveragePct = metrics.getNumStatements() > 0
                                ? metrics.getNumCoveredStatements() * 100.0 / metrics.getNumStatements()
                                : 0.0;

                        double riskScore = complexity * (1.0 - coveragePct / 100.0);

                        if (riskScore > 0) {
                            MethodRiskScore mrs = new MethodRiskScore();
                            mrs.file = fileName;
                            mrs.className = classInfo.getName();
                            mrs.method = method.getSimpleName();
                            mrs.complexity = complexity;
                            mrs.coveragePct = Math.round(coveragePct * 10.0) / 10.0;
                            mrs.riskScore = Math.round(riskScore * 10.0) / 10.0;
                            scores.add(mrs);
                        }
                    }
                }
            }
        }

        scores.sort((a, b) -> Double.compare(b.riskScore, a.riskScore));

        if (scores.size() > maxResults) {
            return scores.subList(0, maxResults);
        }
        return scores;
    }

    /**
     * Method risk score combining complexity and coverage.
     */
    public static class MethodRiskScore {
        public String file;
        public String className;
        public String method;
        public int complexity;
        public double coveragePct;
        public double riskScore;
    }
}
