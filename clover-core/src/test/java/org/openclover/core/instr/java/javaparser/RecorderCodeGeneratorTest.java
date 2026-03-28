package org.openclover.core.instr.java.javaparser;

import org.junit.Test;
import org.openclover.core.instr.java.javaparser.RecorderCodeGenerator.RecorderConfig;
import org.openclover.runtime.CloverNames;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link RecorderCodeGenerator}.
 */
public class RecorderCodeGeneratorTest {

    private static final String PUBLIC_STATIC_CLASS_CLR_TEST = "public static class __CLR_TEST{";
    private static final String CLOVER_VERSION_INFO_GET_BUILD_STAMP = "CloverVersionInfo.getBuildStamp()";
    private static final String SHOULD_INSTANTIATE_TEST_NAME_SNIFFER_SIMPLE = "Should instantiate TestNameSniffer.Simple";
    private static final String NEW_TEST_NAME_SNIFFER_SIMPLE = "new org_openclover_runtime.TestNameSniffer.Simple()";
    private static final String CATCH_PREFIX = "}catch(";

    @Test
    public void generatesStaticRecorderClassDeclaration() {
        RecorderConfig config = createMinimalConfig();
        String code = RecorderCodeGenerator.generate(config);

        assertTrue("Should contain static class declaration",
                code.contains(PUBLIC_STATIC_CLASS_CLR_TEST));
    }

    @Test
    public void generatesStaticRecorderClassDeclarationForTestClass() {
        RecorderConfig config = createMinimalConfig();
        config.isTestClass = true;
        String code = RecorderCodeGenerator.generate(config);

        assertTrue("Should contain static class without public modifier for test classes",
                code.contains("static class __CLR_TEST{"));
        assertFalse("Should not have public modifier before static class",
                code.contains(PUBLIC_STATIC_CLASS_CLR_TEST));
    }

    @Test
    public void generatesCoverageRecorderField() {
        RecorderConfig config = createMinimalConfig();
        String code = RecorderCodeGenerator.generate(config);

        assertTrue("Should contain CoverageRecorder field",
                code.contains("public static org_openclover_runtime.CoverageRecorder R;"));
    }

    @Test
    public void generatesStaticInitializer() {
        RecorderConfig config = createMinimalConfig();
        String code = RecorderCodeGenerator.generate(config);

        assertTrue("Should contain static initializer", code.contains("static{"));
        assertTrue("Should initialize recorder variable",
                code.contains("org_openclover_runtime.CoverageRecorder _R=null;"));
    }

    @Test
    public void generatesVersionCheckWhenReportInitErrorsEnabled() {
        RecorderConfig config = createMinimalConfig();
        config.reportInitErrors = true;
        String code = RecorderCodeGenerator.generate(config);

        assertTrue("Should contain CloverVersionInfo.getBuildStamp() check",
                code.contains(CLOVER_VERSION_INFO_GET_BUILD_STAMP));
    }

    @Test
    public void doesNotGenerateVersionCheckWhenReportInitErrorsDisabled() {
        RecorderConfig config = createMinimalConfig();
        config.reportInitErrors = false;
        String code = RecorderCodeGenerator.generate(config);

        assertFalse("Should not contain version check when reportInitErrors is false",
                code.contains(CLOVER_VERSION_INFO_GET_BUILD_STAMP));
    }

    @Test
    public void generatesLambdaIncMethodWhenLambdasSupported() {
        RecorderConfig config = createMinimalConfig();
        config.areLambdasSupported = true;
        String code = RecorderCodeGenerator.generate(config);

        assertTrue("Should contain lambdaInc method declaration",
                code.contains("public static <I, T extends I> I lambdaInc"));
        String proxyNewProxyInstance = config.javaLangPrefix + "reflect.Proxy.newProxyInstance";
        assertTrue("Should use Proxy.newProxyInstance",
                code.contains(proxyNewProxyInstance));
    }

    @Test
    public void doesNotGenerateLambdaIncMethodWhenLambdasNotSupported() {
        RecorderConfig config = createMinimalConfig();
        config.areLambdasSupported = false;
        String code = RecorderCodeGenerator.generate(config);

        assertFalse("Should not contain lambdaInc method when lambdas not supported",
                code.contains("lambdaInc"));
    }

    @Test
    public void generatesProperErrorHandlingCatchBlocks() {
        RecorderConfig config = createMinimalConfig();
        config.reportInitErrors = true;
        String code = RecorderCodeGenerator.generate(config);

        String securityExceptionCatch = CATCH_PREFIX + config.javaLangPrefix + "SecurityException e){";
        String noClassDefFoundErrorCatch = CATCH_PREFIX + config.javaLangPrefix + "NoClassDefFoundError e){";
        String throwableCatch = CATCH_PREFIX + config.javaLangPrefix + "Throwable t){";

        assertTrue("Should catch SecurityException", code.contains(securityExceptionCatch));
        assertTrue("Should catch NoClassDefFoundError", code.contains(noClassDefFoundErrorCatch));
        assertTrue("Should catch Throwable", code.contains(throwableCatch));
    }

    @Test
    public void usesUnicodeEncodingForInitString() {
        RecorderConfig config = createMinimalConfig();
        config.initString = "test";
        String code = RecorderCodeGenerator.generate(config);

        // Unicode encoded "test" should be present
        assertTrue("Should use unicode encoding for init string",
                code.contains("\\u0074\\u0065\\u0073\\u0074"));
    }

    @Test
    public void generatesTestSnifferSimpleForSpockTests() {
        RecorderConfig config = createMinimalConfig();
        config.isSpockTestClass = true;
        String code = RecorderCodeGenerator.generate(config);

        assertTrue("Should generate Simple test sniffer for Spock",
                code.contains(CloverNames.CLOVER_TEST_NAME_SNIFFER));
        assertTrue(SHOULD_INSTANTIATE_TEST_NAME_SNIFFER_SIMPLE,
                code.contains(NEW_TEST_NAME_SNIFFER_SIMPLE));
    }

    @Test
    public void generatesTestSnifferSimpleForJUnitParameterizedTests() {
        RecorderConfig config = createMinimalConfig();
        config.isParameterizedJUnit = true;
        String code = RecorderCodeGenerator.generate(config);

        assertTrue("Should generate Simple test sniffer for parameterized JUnit",
                code.contains(CloverNames.CLOVER_TEST_NAME_SNIFFER));
        assertTrue(SHOULD_INSTANTIATE_TEST_NAME_SNIFFER_SIMPLE,
                code.contains(NEW_TEST_NAME_SNIFFER_SIMPLE));
    }

    @Test
    public void generatesTestSnifferSimpleForJUnit5ParameterizedTests() {
        RecorderConfig config = createMinimalConfig();
        config.isJUnit5Parameterized = true;
        String code = RecorderCodeGenerator.generate(config);

        assertTrue("Should generate Simple test sniffer for JUnit 5 parameterized",
                code.contains(CloverNames.CLOVER_TEST_NAME_SNIFFER));
        assertTrue(SHOULD_INSTANTIATE_TEST_NAME_SNIFFER_SIMPLE,
                code.contains(NEW_TEST_NAME_SNIFFER_SIMPLE));
    }

    @Test
    public void generatesTestSnifferNullInstanceForNonTestClasses() {
        RecorderConfig config = createMinimalConfig();
        config.isSpockTestClass = false;
        config.isParameterizedJUnit = false;
        config.isJUnit5Parameterized = false;
        String code = RecorderCodeGenerator.generate(config);

        assertTrue("Should generate NULL_INSTANCE sniffer for non-test classes",
                code.contains(CloverNames.CLOVER_TEST_NAME_SNIFFER));
        assertTrue("Should use TestNameSniffer.NULL_INSTANCE",
                code.contains("TestNameSniffer.NULL_INSTANCE"));
    }

    @Test
    public void generatedCodeIsSyntacticallyValid() {
        RecorderConfig config = createMinimalConfig();
        config.reportInitErrors = true;
        config.areLambdasSupported = true;
        String code = RecorderCodeGenerator.generate(config);

        // Basic syntax checks
        assertTrue("Should start with class declaration", code.startsWith("public static class"));
        assertTrue("Should contain test sniffer field", code.contains("_TEST_NAME_SNIFFER"));
        assertTrue("Should have balanced braces", countOccurrences(code, '{') == countOccurrences(code, '}'));
    }

    private RecorderConfig createMinimalConfig() {
        RecorderConfig config = new RecorderConfig();
        config.recorderBase = "__CLR_TEST";
        config.recorderSuffix = "R";
        config.initString = "/tmp/clover.db";
        config.registryVersion = 1L;
        config.recorderCfg = 0L;
        config.maxDataIndex = 100;
        config.isEnum = false;
        config.isTestClass = false;
        config.isSpockTestClass = false;
        config.isParameterizedJUnit = false;
        config.isJUnit5Parameterized = false;
        config.reportInitErrors = false;
        config.areLambdasSupported = false;
        config.javaLangPrefix = "java.lang.";
        config.classNotFoundMsg = "ERROR: OpenClover could not be initialised.";
        config.distributedConfig = null;
        config.profiles = null;
        config.shouldEmitWarningMethod = false;
        return config;
    }

    private int countOccurrences(String str, char ch) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == ch) {
                count++;
            }
        }
        return count;
    }
}
