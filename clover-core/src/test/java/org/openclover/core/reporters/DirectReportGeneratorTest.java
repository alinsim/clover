package org.openclover.core.reporters;

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
import org.openclover.core.registry.entities.MethodSignature;
import org.openclover.core.registry.entities.Modifiers;
import org.openclover.runtime.api.CloverException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

/**
 * TDD tests for DirectReportGenerator — Ant-free report generation.
 */
public class DirectReportGeneratorTest {

    private static final String PACKAGE_NAME = "com.example";
    private static final String CLASS_NAME = "Calculator";
    private static final String SOURCE_FILE = "com/example/Calculator.java";
    private static final String TEST_CLASS_NAME = "CalculatorTest";
    private static final String TEST_SOURCE_FILE = "com/example/CalculatorTest.java";
    private static final String CLOVER_XML = "clover.xml";
    private static final String METHOD_ADD = "add";
    private static final String MSG_SUCCESS = "Report should succeed: ";
    private static final String MSG_XML_EXISTS = "XML file should exist";

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private File registryFile;
    private File outputDir;

    @Before
    public void setUp() throws IOException {
        File registryDir = tempDir.newFolder("registry");
        outputDir = tempDir.newFolder("report");
        registryFile = new File(registryDir, "clover.db");
    }

    @Test
    public void generateHtmlReportFromRegistry() throws Exception {
        createTestRegistry();

        DirectReportGenerator generator = new DirectReportGenerator();
        DirectReportGenerator.Result result = generator.generateHtmlReport(
                registryFile.getAbsolutePath(), outputDir);

        assertTrue(MSG_SUCCESS + result.getErrorMessage(), result.isSuccess());
        assertTrue("Output directory should have files",
                outputDir.listFiles() != null && outputDir.listFiles().length > 0);
    }

    @Test
    public void generateXmlReportFromRegistry() throws Exception {
        createTestRegistry();

        File xmlFile = new File(outputDir, CLOVER_XML);
        DirectReportGenerator generator = new DirectReportGenerator();
        DirectReportGenerator.Result result = generator.generateXmlReport(
                registryFile.getAbsolutePath(), xmlFile);

        assertTrue(MSG_SUCCESS + result.getErrorMessage(), result.isSuccess());
        assertTrue(MSG_XML_EXISTS, xmlFile.exists());
        assertTrue("XML file should have content", xmlFile.length() > 0);

        String xml = new String(Files.readAllBytes(xmlFile.toPath()));
        assertTrue("XML should contain project element", xml.contains("<project"));
        assertTrue("XML should contain metrics", xml.contains("methods="));
    }

    @Test
    public void generateReportWithTestMethodsInRegistry() throws Exception {
        createTestRegistryWithTests();

        File xmlFile = new File(outputDir, CLOVER_XML);
        DirectReportGenerator generator = new DirectReportGenerator();
        DirectReportGenerator.Result result = generator.generateXmlReport(
                registryFile.getAbsolutePath(), xmlFile);

        assertTrue(MSG_SUCCESS + result.getErrorMessage(), result.isSuccess());
        assertTrue(MSG_XML_EXISTS, xmlFile.exists());

        String xml = new String(Files.readAllBytes(xmlFile.toPath()));
        assertTrue("XML should contain test class", xml.contains(TEST_CLASS_NAME));
    }

    @Test
    public void generateReportFailsGracefullyForMissingDb() throws Exception {
        DirectReportGenerator generator = new DirectReportGenerator();
        DirectReportGenerator.Result result = generator.generateXmlReport(
                "/nonexistent/clover.db", new File(outputDir, CLOVER_XML));

        assertTrue("Should fail for missing db", !result.isSuccess());
    }

    private void createTestRegistry()
            throws CloverException, IOException, ConcurrentInstrumentationException {

        Clover2Registry registry = new Clover2Registry(registryFile, "test");
        InstrumentationSession session = registry.startInstr();

        session.enterFile(PACKAGE_NAME, new File(SOURCE_FILE), 10, 8,
                System.currentTimeMillis(), 200L, 12345L);
        session.enterClass(CLASS_NAME, new FixedSourceRegion(1, 0),
                new Modifiers(), false, false, false);

        ((InstrumentationSessionImpl) session).enterMethod(
                new ContextSetImpl(), new FixedSourceRegion(3, 0),
                new MethodSignature(METHOD_ADD), false);
        session.exitMethod(5, 0);

        session.exitClass(10, 0);
        session.exitFile();
        session.close();

        registry.saveAndOverwriteFile();
    }

    private void createTestRegistryWithTests()
            throws CloverException, IOException, ConcurrentInstrumentationException {

        Clover2Registry registry = new Clover2Registry(registryFile, "test");
        InstrumentationSession session = registry.startInstr();

        session.enterFile(PACKAGE_NAME, new File(SOURCE_FILE), 10, 8,
                System.currentTimeMillis(), 200L, 12345L);
        session.enterClass(CLASS_NAME, new FixedSourceRegion(1, 0),
                new Modifiers(), false, false, false);

        ((InstrumentationSessionImpl) session).enterMethod(
                new ContextSetImpl(), new FixedSourceRegion(3, 0),
                new MethodSignature(METHOD_ADD), false);
        session.exitMethod(5, 0);

        session.exitClass(10, 0);
        session.exitFile();

        session.enterFile(PACKAGE_NAME, new File(TEST_SOURCE_FILE), 15, 12,
                System.currentTimeMillis(), 300L, 67890L);
        session.enterClass(TEST_CLASS_NAME, new FixedSourceRegion(1, 0),
                new Modifiers(), false, false, false);

        ((InstrumentationSessionImpl) session).enterMethod(
                new ContextSetImpl(), new FixedSourceRegion(3, 0),
                new MethodSignature("testAdd"), true);
        session.exitMethod(8, 0);

        ((InstrumentationSessionImpl) session).enterMethod(
                new ContextSetImpl(), new FixedSourceRegion(10, 0),
                new MethodSignature("testSubtract"), true);
        session.exitMethod(14, 0);

        session.exitClass(15, 0);
        session.exitFile();

        session.close();
        registry.saveAndOverwriteFile();
    }
}
