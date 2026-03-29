package org.openclover.core.recorder

import org.junit.Test
import org.openclover.runtime.recorder.FixedSizeCoverageRecorder
import org.openclover.runtime.recorder.NullRecorder
import org_openclover_runtime.CoverageRecorder

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotSame
import static org.junit.Assert.assertSame

class FixedSizeCoverageRecorderGrowthTest {

    @Test
    void withCapacityForGrowsInsteadOfReturningNullRecorder() {
        CoverageRecorder recorder = new FixedSizeCoverageRecorder("test", 0, 500, 0L)
        CoverageRecorder result = recorder.withCapacityFor(600)

        assertNotSame(NullRecorder.INSTANCE, result)
        assertSame(recorder, result)

        // Verify we can increment at the new index without exception
        recorder.inc(550)
    }

    @Test
    void withCapacityForPreservesExistingCoverageData() {
        FixedSizeCoverageRecorder recorder = new FixedSizeCoverageRecorder("test", 0, 500, 0L)

        // Record some coverage at index 10
        recorder.inc(10)
        recorder.inc(10)
        recorder.inc(10)

        // Grow the recorder
        recorder.withCapacityFor(600)

        // Verify coverage data preserved (iget increments before returning, so 3 + 1 = 4)
        assertEquals(4, recorder.iget(10))
    }

    @Test
    void withCapacityForNoOpWhenSufficient() {
        FixedSizeCoverageRecorder recorder = new FixedSizeCoverageRecorder("test", 0, 500, 0L)
        CoverageRecorder result = recorder.withCapacityFor(300)

        // Should return same instance without reallocation
        assertSame(recorder, result)
    }

    @Test
    void withCapacityForHandlesExactCapacity() {
        FixedSizeCoverageRecorder recorder = new FixedSizeCoverageRecorder("test", 0, 500, 0L)
        CoverageRecorder result = recorder.withCapacityFor(500)

        // Should return same instance when exact capacity requested
        assertSame(recorder, result)
    }

    @Test
    void withCapacityForAllowsIncrementAtNewIndices() {
        FixedSizeCoverageRecorder recorder = new FixedSizeCoverageRecorder("test", 0, 100, 0L)

        // Grow the recorder
        recorder.withCapacityFor(200)

        // Increment at new index
        recorder.inc(150)
        recorder.inc(150)
        recorder.inc(150)
        recorder.inc(150)
        recorder.inc(150)

        // Verify (5 from inc + 1 from iget = 6)
        assertEquals(6, recorder.iget(150))
    }

    @Test
    void incAutoGrowsWhenIndexExceedsCapacity() {
        // Reproduce: Phase 2 injects R.inc(14966) into a class whose recorder
        // was sized for Phase 1's 9169 slots. Nobody called withCapacityFor().
        FixedSizeCoverageRecorder recorder = new FixedSizeCoverageRecorder("test", 0, 100, 0L)

        // Phase 2 index far beyond Phase 1 capacity — should auto-grow, not throw AIOOBE
        recorder.inc(250)
        recorder.inc(250)

        assertEquals(3, recorder.iget(250))  // 2 from inc + 1 from iget
    }

    @Test
    void igetAutoGrowsWhenIndexExceedsCapacity() {
        FixedSizeCoverageRecorder recorder = new FixedSizeCoverageRecorder("test", 0, 100, 0L)

        // iget also needs to auto-grow
        assertEquals(1, recorder.iget(300))  // first call, auto-grows then increments
    }

    @Test
    void incAutoGrowPreservesExistingData() {
        FixedSizeCoverageRecorder recorder = new FixedSizeCoverageRecorder("test", 0, 100, 0L)

        // Record Phase 1 data
        recorder.inc(50)
        recorder.inc(50)
        recorder.inc(50)

        // Phase 2 inc triggers auto-grow
        recorder.inc(500)

        // Phase 1 data preserved
        assertEquals(4, recorder.iget(50))   // 3 + 1 from iget
        assertEquals(2, recorder.iget(500))  // 1 + 1 from iget
    }

    @Test
    void withCapacityForMultipleGrowths() {
        FixedSizeCoverageRecorder recorder = new FixedSizeCoverageRecorder("test", 0, 100, 0L)

        // Record coverage at index 50
        recorder.inc(50)
        recorder.inc(50)

        // First growth: 100 → 200
        recorder.withCapacityFor(200)
        recorder.inc(150)

        // Second growth: 200 → 500
        recorder.withCapacityFor(500)
        recorder.inc(400)

        // Verify data preserved across both growths
        assertEquals(3, recorder.iget(50))   // 2 from inc + 1 from iget
        assertEquals(2, recorder.iget(150))  // 1 from inc + 1 from iget
        assertEquals(2, recorder.iget(400))  // 1 from inc + 1 from iget
    }
}
