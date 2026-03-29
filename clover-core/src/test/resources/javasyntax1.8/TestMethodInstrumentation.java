import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Test cases for globalSliceStart/End wrapper compilation.
 * Each method pattern must instrument, compile, and run correctly.
 */
public class TestMethodInstrumentation {

    // Fake @Test annotation for Clover detection (no JUnit dependency needed)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Test {}

    // === Case 1: Simple test method ===
    @Test
    public void simpleTest() {
        int x = 1;
        if (x != 1) throw new RuntimeException("simpleTest failed");
    }

    // === Case 2: Test method with return statement ===
    @Test
    public void testWithReturn() {
        if (true) return;
        throw new RuntimeException("unreachable"); // unreachable but must compile
    }

    // === Case 3: Test method that throws ===
    @Test
    public void testThatThrows() throws Exception {
        if (false) throw new Exception("test");
    }

    // === Case 4: Test method with try-catch ===
    @Test
    public void testWithTryCatch() {
        try {
            int x = 1;
        } catch (Exception e) {
            // swallow
        }
    }

    // === Case 5: Test method with try-finally ===
    @Test
    public void testWithTryFinally() {
        int x = 0;
        try {
            x = 1;
        } finally {
            x = 2;
        }
        if (x != 2) throw new RuntimeException("testWithTryFinally failed");
    }

    // === Case 6: Test method with try-with-resources ===
    @Test
    public void testWithTryWithResources() {
        try (AutoCloseable ac = () -> {}) {
            int x = 1;
        } catch (Exception e) {
            // swallow
        }
    }

    // === Case 7: Test method with multiple returns ===
    @Test
    public void testMultipleReturns() {
        int x = (int)(Math.random() * 10);
        if (x < 5) return;
        if (x >= 5) return;
    }

    // === Case 8: Test method with loop ===
    @Test
    public void testWithLoop() {
        for (int i = 0; i < 3; i++) {
            int x = i * 2;
        }
    }

    // === Case 9: Test method with switch ===
    @Test
    public void testWithSwitch() {
        int x = 1;
        switch (x) {
            case 1: break;
            case 2: break;
            default: break;
        }
    }

    // === Case 10: Test method with lambda ===
    @Test
    public void testWithLambda() {
        Runnable r = () -> {
            int x = 1;
        };
        r.run();
    }

    // === Case 11: Test method with nested class ===
    @Test
    public void testWithNestedClass() {
        class Local {
            int value() { return 42; }
        }
        if (new Local().value() != 42) throw new RuntimeException("testWithNestedClass failed");
    }

    // === Case 12: Test method with assertion ===
    @Test
    public void testWithAssertion() {
        if (1 + 1 != 2) throw new RuntimeException("testWithAssertion failed");
    }

    // === Case 13: Braceless if in test method ===
    @Test
    public void testBracelessIf() {
        int x = 1;
        if (x == 1) x = 2;
        if (x != 2) throw new RuntimeException("testBracelessIf failed");
    }

    // === Case 14: Test method with synchronized block ===
    @Test
    public void testWithSynchronized() {
        Object lock = new Object();
        synchronized (lock) {
            int x = 1;
        }
    }

    // === Case 15: Empty test method ===
    @Test
    public void emptyTest() {
    }

    // === Non-test method (should NOT have globalSlice wrapper) ===
    public void notATest() {
        int x = 1;
    }

    // === Main method to exercise all tests ===
    public static void main(String[] args) {
        TestMethodInstrumentation t = new TestMethodInstrumentation();
        t.simpleTest();
        t.testWithReturn();
        try { t.testThatThrows(); } catch (Exception e) {}
        t.testWithTryCatch();
        t.testWithTryFinally();
        t.testWithTryWithResources();
        t.testMultipleReturns();
        t.testWithLoop();
        t.testWithSwitch();
        t.testWithLambda();
        t.testWithNestedClass();
        t.testWithAssertion();
        t.testBracelessIf();
        t.testWithSynchronized();
        t.emptyTest();
        t.notATest();
        System.out.println("All test method patterns executed successfully");
    }
}
