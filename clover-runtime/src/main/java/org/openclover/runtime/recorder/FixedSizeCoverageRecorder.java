package org.openclover.runtime.recorder;

import org.openclover.runtime.Logger;
import org.openclover.runtime.registry.RegistryFormatException;
import org.openclover.runtime.registry.format.RegAccessMode;
import org.openclover.runtime.registry.format.RegHeader;
import org.openclover.runtime.util.CloverBitSet;
import org_openclover_runtime.CoverageRecorder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

public final class FixedSizeCoverageRecorder extends BaseCoverageRecorder {
    private static final Set<String> TRUNC_WARNING_DBS = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> MERGE_WARNING_DBS = Collections.synchronizedSet(new HashSet<>());
    private static final String OPENCLOVER_DATABASE_PREFIX = "OpenClover database: '";
    private static final String COVERAGE_DATA_NOT_GATHERED_MSG = "Coverage data for some classes will not be gathered.";

    private volatile int[] elements;

    /**
     * Factory method. Use this to get an instance of the recorder. Do not call constructors directly
     * (they're not private only for the sake of unit tests).
     */
    public static CoverageRecorder createFor(final File dbFile, final long dbVersion, final int maxNumElements, final long cfgbits) throws IOException, RegistryFormatException {
        final RegHeader header = RegHeader.readFrom(dbFile);
        if (header.getAccessMode() == RegAccessMode.READWRITE) {
            final int numElementsInDb = header.getSlotCount();
            return recorderBigEnoughFor(
                    dbFile.getAbsolutePath(),
                    maxNumElements,
                    numElementsInDb,
                    new NewRecorderBlock() {
                        @Override
                        public CoverageRecorder call() {
                            return new FixedSizeCoverageRecorder(dbFile.getAbsolutePath(), dbVersion, numElementsInDb, cfgbits);
                        }
                    });
        } else {
            if (!MERGE_WARNING_DBS.contains(dbFile.getAbsolutePath())) {
                MERGE_WARNING_DBS.add(dbFile.getAbsolutePath());
                Logger.getInstance().warn(
                        OPENCLOVER_DATABASE_PREFIX + dbFile.getAbsolutePath() + "' can only be used for reporting because it is the result of a merge.");
                Logger.getInstance().warn(COVERAGE_DATA_NOT_GATHERED_MSG);
            }
            return NullRecorder.INSTANCE;
        }
    }

    /*private*/ FixedSizeCoverageRecorder(String dbName, long dbVersion, int numElements, long cfgbits) {
        this(dbName, dbVersion, numElements, cfgbits, GlobalRecordingWriteStrategy.WRITE_TO_FILE);
    }

    /*private*/ FixedSizeCoverageRecorder(String dbName, long dbVersion, int numElements, long cfgbits, GlobalRecordingWriteStrategy writeStrategy) {
        super(dbName, dbVersion, cfgbits, writeStrategy);
        this.elements = new int[numElements];
    }

    @Override
    public CloverBitSet compareCoverageWith(CoverageSnapshot before) {
        int[] e = elements;
        final int[] beforeElements = before.getCoverage()[0];
        for(int i = 0; i < beforeElements.length; i++) {
            beforeElements[i] = beforeElements[i] - e[i];
        }
        return CloverBitSet.forHits(beforeElements);
    }

    @Override
    public CloverBitSet createEmptyHitsMask() {
        int[] e = elements;
        return new CloverBitSet(e.length);
    }

    /**
     * Increment slot at index. Auto-grows the backing array if index exceeds capacity
     * (Phase 2 bytecode instrumentation may inject indices beyond Phase 1's allocation).
     */
    @Override
    public void inc(int index) {
        int[] e = elements;
        if (index >= e.length) {
            e = growTo(index + 1);
        }
        testCoverage.set(index);
        e[index]++;
    }

    /**
     * @return coverage for slot at index but increment by one before evaluation.
     * Auto-grows the backing array if index exceeds capacity.
     */
    @Override
    public int iget(int index) {
        int[] e = elements;
        if (index >= e.length) {
            e = growTo(index + 1);
        }
        testCoverage.set(index);
        return ++e[index];
    }

    private int[] growTo(int minCapacity) {
        synchronized (this) {
            if (minCapacity > elements.length) {
                int[] newElements = new int[minCapacity];
                System.arraycopy(elements, 0, newElements, 0, elements.length);
                elements = newElements;
            }
            return elements;
        }
    }

    @Override
    protected String write() throws IOException {
        return write(new int[][] {this.elements}, this.elements.length);
    }

    @Override
    public CoverageRecorder withCapacityFor(int maxNumElements) {
        if (maxNumElements > elements.length) {
            synchronized (this) {
                if (maxNumElements > elements.length) {
                    int[] newElements = new int[maxNumElements];
                    System.arraycopy(elements, 0, newElements, 0, elements.length);
                    elements = newElements;
                }
            }
        }
        return this;
    }

    @Override
    public CoverageSnapshot getCoverageSnapshot() {
        int[] e = elements;
        return new CoverageSnapshot(new int[][] {e.clone()});
    }

    private static CoverageRecorder recorderBigEnoughFor(String dbName, int numRequiredElements, int numAvailableElements, NewRecorderBlock recorderIfSufficient) {
        if (numRequiredElements > numAvailableElements) {
            logInsufficientCapacity(dbName, numRequiredElements, numAvailableElements);
            return NullRecorder.INSTANCE;
        } else {
            return recorderIfSufficient.call();
        }
    }

    private static void logInsufficientCapacity(String dbName, int numRequiredElements, int numAvailableElements) {
        if (!TRUNC_WARNING_DBS.contains(dbName)) {
            TRUNC_WARNING_DBS.add(dbName);
            Logger.getInstance().warn(
                OPENCLOVER_DATABASE_PREFIX + dbName + "' is no longer valid. Min required size for currently loading class: " +
                numRequiredElements + ", actual size: " + numAvailableElements);
            Logger.getInstance().warn(COVERAGE_DATA_NOT_GATHERED_MSG);
        }
    }

    private interface NewRecorderBlock extends Callable<CoverageRecorder> {
        @Override
        CoverageRecorder call();
    }

    ///CLOVER:OFF
    @Override
    public String toString() {
        int[] e = elements;
        return "FixedSizeCoverageRecorder[elements.length=" + e.length + "]";
    }
    ///CLOVER:ON
}
