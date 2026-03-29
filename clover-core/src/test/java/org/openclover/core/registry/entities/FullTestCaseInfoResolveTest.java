package org.openclover.core.registry.entities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openclover.core.api.instrumentation.ConcurrentInstrumentationException;
import org.openclover.core.api.instrumentation.InstrumentationSession;
import org.openclover.core.context.ContextSetImpl;
import org.openclover.core.instr.InstrumentationSessionImpl;
import org.openclover.core.registry.Clover2Registry;
import org.openclover.core.registry.FixedSourceRegion;
import org.openclover.runtime.api.CloverException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import static org.junit.Assert.assertTrue;

/**
 * TDD test for FullTestCaseInfo.resolve() — test identity matching between
 * per-test recordings and the registry's test methods.
 */
public class FullTestCaseInfoResolveTest {

    private static final String PACKAGE_NAME = "com.example";
    private static final String TEST_CLASS = "FooTest";
    private static final String FQ_CLASS = PACKAGE_NAME + "." + TEST_CLASS;
    private static final String TEST_METHOD = "testSomething";
    private static final String TEST_FILE = "com/example/FooTest.java";

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private Clover2Registry registry;

    @Before
    public void setUp() throws IOException, CloverException, ConcurrentInstrumentationException {
        File registryFile = new File(tempDir.newFolder("reg"), "clover.db");
        registry = new Clover2Registry(registryFile, "test");
        InstrumentationSession session = registry.startInstr();

        session.enterFile(PACKAGE_NAME, new File(TEST_FILE), 10, 8,
                System.currentTimeMillis(), 200L, 12345L);
        session.enterClass(TEST_CLASS, new FixedSourceRegion(1, 0),
                new Modifiers(), false, false, false);

        ((InstrumentationSessionImpl) session).enterMethod(
                new ContextSetImpl(), new FixedSourceRegion(3, 0),
                new MethodSignature(TEST_METHOD), true);
        session.exitMethod(5, 0);

        session.exitClass(10, 0);
        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();
    }

    @Test
    public void resolveWithSimpleClassNameInSourceMethodName() {
        // Reproduces: globalSliceEnd passes "FooTest.testSomething" (simple class name).
        // Resolve splits on last dot: srcClassname="FooTest", methodName="testSomething".
        // rtClassname (FQ) != srcClassname (simple) → findClass(simple) → null.
        // Must fall back to runtimeType (which WAS found via FQ name).
        // Use reflection to access the private constructor that takes runtimeTypeName + sourceMethodName
        FullTestCaseInfo tci;
        try {
            @SuppressWarnings("JavaReflectionMemberAccess")
            Constructor<FullTestCaseInfo> ctor = FullTestCaseInfo.class.getDeclaredConstructor(
                    long.class, long.class, double.class, String.class, String.class, String.class);
            ctor.setAccessible(true);
            tci = ctor.newInstance(
                    0L, 0L, 0.0,
                    FQ_CLASS,                          // runtimeTypeName
                    TEST_CLASS + "." + TEST_METHOD,    // sourceMethodName: "FooTest.testSomething"
                    null);                             // runtimeTestName
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        boolean resolved = tci.resolve(registry.getProject());

        assertTrue("Test should resolve even with simple class name in sourceMethodName", resolved);
    }
}
