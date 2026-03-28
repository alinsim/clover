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
