package org.openclover.core.reporters.agent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openclover.core.api.instrumentation.InstrumentationSession;
import org.openclover.core.api.registry.ClassInfo;
import org.openclover.core.api.registry.FileInfo;
import org.openclover.core.api.registry.HasMetricsFilter;
import org.openclover.core.api.registry.MethodInfo;
import org.openclover.core.cfg.instr.java.JavaInstrumentationConfig;
import org.openclover.core.instr.java.StringInstrumentationSource;
import org.openclover.core.instr.java.javaparser.AstInstrumenter;
import org.openclover.core.registry.Clover2Registry;
import org.openclover.core.registry.entities.FullTestCaseInfo;
import org.openclover.core.util.FileUtils;

import java.io.File;
import java.io.StringWriter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestNameResolutionTest {

    private static final String UTF_8 = "UTF-8";
    private static final String TEST_PROJECT = "test-name-project";
    private static final String TEST_CLASS = "CalculatorTest";
    private static final String TEST_METHOD = "testAdd";
    private static final String NULL_SUFFIX = ".null";
    private static final String QUOTE = "\"";
    private static final String DOT_JAVA = ".java";
    private static final String QUALIFIED_METHOD = TEST_CLASS + "." + TEST_METHOD;
    private static final String TEST_SOURCE =
            "import org.junit.jupiter.api.Test;\npublic class " + TEST_CLASS
            + " {\n    @Test\n    public void " + TEST_METHOD
            + "() {\n        int result = 1 + 2;\n    }\n}";

    private File workingDir;
    private File registryFile;

    @Before
    public void setUp() throws Exception {
        workingDir = File.createTempFile(getClass().getName(), ".tmp");
        workingDir.delete();
        workingDir.mkdir();
        registryFile = new File(workingDir, "clover.db");
    }

    @After
    public void tearDown() throws Exception {
        if (workingDir != null && workingDir.exists()) {
            FileUtils.deltree(workingDir);
        }
        FullTestCaseInfo.Factory.reset();
    }

    @Test
    public void instrumentedTestMethodHasQualifiedMethodNameInGlobalSlice() throws Exception {
        InstrumentResult result = instrumentTestSource();
        assertTrue("globalSliceEnd should contain qualified ClassName.methodName",
                result.instrumented.contains(QUOTE + QUALIFIED_METHOD + QUOTE));
    }

    @Test
    public void registryTestMethodHasStaticTestName() throws Exception {
        InstrumentResult result = instrumentTestSource();
        result.registry.saveAndOverwriteFile();
        FileInfo fileInfo = result.registry.getProject().getFiles(HasMetricsFilter.ACCEPT_ALL).get(0);
        MethodInfo testMethod = null;
        for (ClassInfo classInfo : fileInfo.getClasses()) {
            for (MethodInfo method : classInfo.getMethods()) {
                if (method.getSimpleName().equals(TEST_METHOD)) {
                    testMethod = method;
                }
            }
        }
        assertNotNull("Test method should exist in registry", testMethod);
        assertTrue("Method should be marked as test", testMethod.isTest());
        assertNotNull("staticTestName should not be null", testMethod.getStaticTestName());
    }

    @Test
    public void resolvedTestCaseHasNonNullTestName() throws Exception {
        InstrumentResult result = instrumentTestSource();
        result.registry.saveAndOverwriteFile();
        FileInfo fileInfo = result.registry.getProject().getFiles(HasMetricsFilter.ACCEPT_ALL).get(0);
        ClassInfo classInfo = fileInfo.getClasses().get(0);
        MethodInfo sourceMethod = null;
        for (MethodInfo m : classInfo.getMethods()) {
            if (m.getSimpleName().equals(TEST_METHOD)) {
                sourceMethod = m;
            }
        }
        assertNotNull("Source method must be in registry", sourceMethod);
        FullTestCaseInfo testCase = new FullTestCaseInfo(1, classInfo, sourceMethod, null);
        String testName = testCase.getTestName();
        assertNotNull("getTestName() should not be null", testName);
        String qualifiedName = testCase.getQualifiedName();
        assertFalse("Qualified name should not end with .null: " + qualifiedName,
                qualifiedName != null && qualifiedName.endsWith(NULL_SUFFIX));
    }

    private InstrumentResult instrumentTestSource() throws Exception {
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, TEST_PROJECT);
        InstrumentationSession session = registry.startInstr(UTF_8);
        StringWriter output = new StringWriter();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setEncoding(UTF_8);
        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, TEST_CLASS + DOT_JAVA), TEST_SOURCE);
        AstInstrumenter.instrument(source, output, session, config, null, null);
        session.exitFile();
        session.close();
        return new InstrumentResult(registry, session, output.toString());
    }

    private static class InstrumentResult {
        final Clover2Registry registry;
        final InstrumentationSession session;
        final String instrumented;
        InstrumentResult(Clover2Registry registry, InstrumentationSession session, String instrumented) {
            this.registry = registry;
            this.session = session;
            this.instrumented = instrumented;
        }
    }
}
