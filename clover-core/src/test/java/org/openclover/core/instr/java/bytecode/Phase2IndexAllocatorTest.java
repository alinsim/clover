package org.openclover.core.instr.java.bytecode;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Phase2IndexAllocatorTest {

    private static final String CLASS_USER = "com/example/User";
    private static final String CLASS_FILE_USER = "/tmp/User.class";
    private static final String DESC_VOID = "()V";
    private static final String CLASS_FILE_A = "/tmp/A.class";
    private static final String CLASS_FILE_X = "/tmp/X.class";
    private static final String CLASS_FILE_Y = "/tmp/Y.class";

    @Test
    public void allocateIndicesStartsFromPhase1Max() {
        Phase2IndexAllocator allocator = new Phase2IndexAllocator(500);
        List<GeneratedMethod> methods = Arrays.asList(
            new GeneratedMethod(CLASS_USER, "getName", "()Ljava/lang/String;", 1, null, CLASS_FILE_USER),
            new GeneratedMethod(CLASS_USER, "setName", "(Ljava/lang/String;)V", 1, null, CLASS_FILE_USER)
        );

        Map<GeneratedMethod, Integer> indexMap = allocator.allocateIndices(methods);

        assertEquals(2, indexMap.size());
        assertEquals(Integer.valueOf(500), indexMap.get(methods.get(0)));
        assertEquals(Integer.valueOf(501), indexMap.get(methods.get(1)));
    }

    @Test
    public void getTotalMaxIndexReflectsAllAllocations() {
        Phase2IndexAllocator allocator = new Phase2IndexAllocator(100);
        List<GeneratedMethod> methods = Arrays.asList(
            new GeneratedMethod("A", "a", DESC_VOID, 1, null, CLASS_FILE_A),
            new GeneratedMethod("A", "b", DESC_VOID, 1, null, CLASS_FILE_A),
            new GeneratedMethod("A", "c", DESC_VOID, 1, null, CLASS_FILE_A)
        );

        allocator.allocateIndices(methods);

        assertEquals(103, allocator.getTotalMaxIndex());
        assertEquals(3, allocator.getPhase2Count());
    }

    @Test
    public void emptyMethodListAllocatesNothing() {
        Phase2IndexAllocator allocator = new Phase2IndexAllocator(250);

        Map<GeneratedMethod, Integer> indexMap = allocator.allocateIndices(Collections.emptyList());

        assertTrue(indexMap.isEmpty());
        assertEquals(250, allocator.getTotalMaxIndex());
        assertEquals(0, allocator.getPhase2Count());
    }

    @Test
    public void phase1MaxIndexIsPreserved() {
        Phase2IndexAllocator allocator = new Phase2IndexAllocator(42);

        assertEquals(42, allocator.getPhase1MaxIndex());
    }

    @Test
    public void indicesAreContiguousAndOrdered() {
        Phase2IndexAllocator allocator = new Phase2IndexAllocator(1000);
        List<GeneratedMethod> methods = Arrays.asList(
            new GeneratedMethod("X", "m1", DESC_VOID, 1, null, CLASS_FILE_X),
            new GeneratedMethod("X", "m2", DESC_VOID, 1, null, CLASS_FILE_X),
            new GeneratedMethod("X", "m3", DESC_VOID, 1, null, CLASS_FILE_X),
            new GeneratedMethod("Y", "m4", DESC_VOID, 1, null, CLASS_FILE_Y),
            new GeneratedMethod("Y", "m5", DESC_VOID, 1, null, CLASS_FILE_Y)
        );

        Map<GeneratedMethod, Integer> indexMap = allocator.allocateIndices(methods);

        int expectedIndex = 1000;
        for (GeneratedMethod method : methods) {
            assertEquals(Integer.valueOf(expectedIndex++), indexMap.get(method));
        }
        assertEquals(1005, allocator.getTotalMaxIndex());
    }
}
