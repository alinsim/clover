package org.openclover.core.instr.java.bytecode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Allocates data indices for Phase 2 (bytecode-discovered) methods,
 * continuing from where Phase 1 (source instrumentation) left off.
 *
 * <p>Phase 1 allocates indices 0..N-1 (where N = project.getDataLength()).
 * Phase 2 allocates indices N..N+M-1 (where M = number of generated methods).
 * Both phases share the same coverage recorder array, which grows as needed.</p>
 */
public class Phase2IndexAllocator {
    private final int phase1MaxIndex;
    private int nextIndex;

    /**
     * @param phase1DataLength the total data slots used by Phase 1
     *                         (from registry.getProject().getDataLength())
     */
    public Phase2IndexAllocator(int phase1DataLength) {
        this.phase1MaxIndex = phase1DataLength;
        this.nextIndex = phase1DataLength;
    }

    /**
     * Assigns indices to all generated methods and returns the mapping.
     * Each method gets exactly one data slot (for method-entry hit counting).
     *
     * @param methods list of generated methods from BytecodeScanner
     * @return ordered map of GeneratedMethod → allocated data index
     */
    public Map<GeneratedMethod, Integer> allocateIndices(List<GeneratedMethod> methods) {
        Map<GeneratedMethod, Integer> indexMap = new LinkedHashMap<>();
        for (GeneratedMethod method : methods) {
            indexMap.put(method, nextIndex++);
        }
        return indexMap;
    }

    /** Total data slots needed (Phase 1 + Phase 2 combined). */
    public int getTotalMaxIndex() {
        return nextIndex;
    }

    /** Number of Phase 2 slots allocated so far. */
    public int getPhase2Count() {
        return nextIndex - phase1MaxIndex;
    }

    /** The index where Phase 1 ends and Phase 2 begins. */
    public int getPhase1MaxIndex() {
        return phase1MaxIndex;
    }
}
