package org.openclover.core.instr.java.javaparser;

import org.openclover.runtime.CloverNames;
import org.openclover.runtime.recorder.pertest.SnifferType;
import org_openclover_runtime.Clover;
import org_openclover_runtime.CloverProfile;
import org_openclover_runtime.CloverVersionInfo;
import org_openclover_runtime.CoverageRecorder;
import org_openclover_runtime.TestNameSniffer;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Generates recorder code for JavaParser-based instrumenters.
 * <p>
 * This class produces the exact same recorder code as {@link RecorderInstrEmitter},
 * but in a form that can be used by JavaParser instrumenters without requiring an InstrumentationState.
 * </p>
 */
public class RecorderCodeGenerator {

    private static final String LAMBDA_INC_METHOD = "lambdaInc";
    private static final String INCOMPATIBLE_MSG =
            "WARNING: The OpenClover version used in instrumentation shall match the runtime version.";
    private static final String DEFAULT_CLASSNOTFOUND_MSG =
            "ERROR: OpenClover could not be initialised. Are you sure you have OpenClover in the runtime classpath?";
    private static final String UNEXPECTED_MSG =
            "ERROR: OpenClover could not be initialised because of an unexpected error.";

    // Code generation string constants
    private static final String PUBLIC = "public ";
    private static final String PUBLIC_STATIC = "public static ";
    private static final String STATIC_CLASS = "static class ";
    private static final String QUOTE = "\"";
    private static final String NEW = "new ";
    private static final String CATCH = "}catch(";
    private static final String SYSTEM_ERR_PRINTLN = "System.err.println(\"";
    private static final String ERROR_MSG_SUFFIX = " (\"+e.getClass()+\":\"+e.getMessage()+\")\");";
    private static final String COMMA_SPACE = ", ";

    // Generated code class name strings (these appear in generated source, not actual Java references)
    // Built dynamically to avoid false positive linter warnings about fully qualified names
    private static final String JAVA_LANG_PKG = "java.lang";
    private static final String JAVA_LANG_REFLECT_PKG = JAVA_LANG_PKG + ".reflect";
    private static final String JAVA_LANG_SUPPRESSWARNINGS = "@" + JAVA_LANG_PKG + ".SuppressWarnings(";
    private static final String JAVA_LANG_REFLECT_INVOCATION_HANDLER = JAVA_LANG_REFLECT_PKG + ".InvocationHandler";
    private static final String JAVA_LANG_REFLECT_METHOD = JAVA_LANG_REFLECT_PKG + ".Method";
    private static final String JAVA_LANG_REFLECT_INVOCATION_TARGET_EXCEPTION = JAVA_LANG_REFLECT_PKG + ".InvocationTargetException";
    private static final String JAVA_LANG_REFLECT_PROXY = JAVA_LANG_REFLECT_PKG + ".Proxy";

    /**
     * Configuration for recorder code generation.
     */
    public static class RecorderConfig {
        public String recorderBase;
        public String recorderSuffix;
        public String initString;
        public long registryVersion;
        public long recorderCfg;
        public int maxDataIndex;
        public boolean isEnum;
        public boolean isTestClass;
        public boolean isSpockTestClass;
        public boolean isParameterizedJUnit;
        public boolean isJUnit5Parameterized;
        public boolean reportInitErrors;
        public boolean areLambdasSupported;
        public String javaLangPrefix;
        public String classNotFoundMsg;
        public String distributedConfig;
        public List<CloverProfile> profiles;
        public boolean shouldEmitWarningMethod;

        public RecorderConfig() {
            this.javaLangPrefix = "java.lang.";
            this.classNotFoundMsg = DEFAULT_CLASSNOTFOUND_MSG;
        }
    }

    /**
     * Generates the complete recorder code matching RecorderInstrEmitter.getInstr().
     *
     * @param config configuration for the recorder
     * @return generated Java code for the recorder
     */
    public static String generate(RecorderConfig config) {
        StringBuilder instrString = new StringBuilder();

        // public static class __CLR3_1_600hckkb3w8 { (or just "static class" for test classes)
        instrString.append(config.isTestClass ? "" : PUBLIC).append(STATIC_CLASS).append(config.recorderBase).append("{");

        // public static org_openclover_runtime.CoverageRecorder R;
        instrString.append(PUBLIC_STATIC).append(CoverageRecorder.class.getName()).append(" ").append(config.recorderSuffix).append(";");

        // add a field with a static array containing list of profiles
        instrString.append(generateCloverProfilesField(config.profiles));

        // static initialization block
        instrString.append("static{");

        // CoverageRecorder _REC = null;
        instrString.append(CoverageRecorder.class.getName()).append(" _").append(config.recorderSuffix).append("=null;");

        if (config.reportInitErrors) {
            instrString.append("try{");
            if (config.shouldEmitWarningMethod) {
                instrString.append(getCloverVersionInfoOldVersionInClasspath()).append(";");
            }
            instrString.append("if(").append(CloverVersionInfo.getBuildStamp()).append("L!=")
                    .append(getCloverVersionInfoGetBuildStamp()).append(")").append("{")
                    .append(getCloverL(QUOTE + INCOMPATIBLE_MSG + QUOTE)).append(";")
                    .append(getCloverL(QUOTE + "WARNING: Instr=" + CloverVersionInfo.getReleaseNum()
                            + "#" + CloverVersionInfo.getBuildStamp() + ",Runtime=" + QUOTE + "+"
                            + getCloverVersionInfoGetReleaseNum() + "+" + QUOTE + "#" + QUOTE + "+" + getCloverVersionInfoGetBuildStamp())).append(";}");
        }

        // REC = Clover.getNullRecorder();
        // We make this initial assignment first so that the class the instrumenting class is shadowing
        // is re-entrant w.r.t. instrumentation.
        instrString.append(config.recorderSuffix).append("=").append(getCloverGetNullRecorder()).append(";");

        // Assign local to the Clover.getNullRecorder() first, in case we fail to get the real recorder.
        // _REC = Clover.getNullRecorder();
        instrString.append("_").append(config.recorderSuffix).append("=").append(getCloverGetNullRecorder()).append(";");

        // _REC = Clover.getRecorder(....);
        instrString.append("_").append(config.recorderSuffix).append("=")
                .append(getCloverGetRecorder(
                        asUnicodeString(config.initString),
                        config.registryVersion + "L",
                        config.recorderCfg + "L",
                        Integer.toString(config.maxDataIndex),
                        "profiles",
                        NEW + config.javaLangPrefix + "String[]{" + QUOTE + CloverNames.PROP_DISTRIBUTED_CONFIG + QUOTE + "," + asUnicodeString(config.distributedConfig) + "}"))
                .append(";");

        if (config.reportInitErrors) {
            instrString.append(CATCH).append(config.javaLangPrefix).append("SecurityException e){")
                    .append(config.javaLangPrefix).append(SYSTEM_ERR_PRINTLN).append(Clover.SECURITY_EXCEPTION_MSG)
                    .append(ERROR_MSG_SUFFIX);
            instrString.append(CATCH).append(config.javaLangPrefix).append("NoClassDefFoundError e){")
                    .append(config.javaLangPrefix).append(SYSTEM_ERR_PRINTLN).append(config.classNotFoundMsg)
                    .append(ERROR_MSG_SUFFIX);
            instrString.append(CATCH).append(config.javaLangPrefix).append("Throwable t){")
                    .append(config.javaLangPrefix).append(SYSTEM_ERR_PRINTLN).append(UNEXPECTED_MSG)
                    .append(ERROR_MSG_SUFFIX).append("}");
        }

        // REC = _REC
        instrString.append(config.recorderSuffix).append("=").append("_").append(config.recorderSuffix).append(";");
        instrString.append("}}");

        // add a lambdaInc() wrapper method for lambdas OUTSIDE the static class - only for java8 or higher
        // This needs to be at top-level class scope so Proxy.newProxyInstance can access package-private interfaces
        if (config.areLambdasSupported) {
            instrString.append(generateLambdaIncMethod(config.recorderBase, config.recorderSuffix, config.javaLangPrefix));
        }

        // add extra test sniffer field
        instrString.append(generateTestSnifferField(config.isSpockTestClass, config.isParameterizedJUnit, config.isJUnit5Parameterized));

        return instrString.toString();
    }

    /**
     * Generate declaration of the field named {@link CloverNames#CLOVER_TEST_NAME_SNIFFER}.
     *
     * @param isSpock            whether it's a Spock framework test class
     * @param isParamJUnit       whether it's a JUnit parameterized test class
     * @param isJunit5ParamTest  whether it's a JUnit 5 parameterized test class
     * @return String text with a field declaration
     */
    static String generateTestSnifferField(boolean isSpock, boolean isParamJUnit, boolean isJunit5ParamTest) {
        return generateTestSnifferField(
                isSpock ? SnifferType.SPOCK :
                        (isParamJUnit || isJunit5ParamTest ? SnifferType.JUNIT : SnifferType.NULL));
    }

    /**
     * Generate declaration of the field named {@link CloverNames#CLOVER_TEST_NAME_SNIFFER}.
     *
     * @param snifferType null, junit or spock
     * @return String text with a field declaration
     */
    static String generateTestSnifferField(final SnifferType snifferType) {
        // public static final TestNameSniffer __CLRx_y_z_TEST_NAME_SNIFFER
        final String snifferField = PUBLIC_STATIC + "final " + TestNameSniffer.class.getName()
                + " " + CloverNames.CLOVER_TEST_NAME_SNIFFER;
        switch (snifferType) {
            case JUNIT:
            case SPOCK:
                // ... = new TestNameSniffer.Simple();
                return snifferField + "=" + NEW + "org_openclover_runtime.TestNameSniffer.Simple();";
            case NULL:
            default:
                // ... = TestNameSniffer.NULL_INSTANCE;
                return snifferField + "=" + TestNameSniffer.class.getName() + ".NULL_INSTANCE;";
        }
    }

    /**
     * Returns a string containing declaration of a generic method for wrapping lambda expressions.
     * This method is generated at top-level class scope (not inside the __CLR inner class) so that
     * Proxy.newProxyInstance can properly access package-private interfaces.
     *
     * @param recorderBase     the base name for the recorder class (e.g., "__CLR3_1_600hckkb3w8")
     * @param recorderSuffix   the suffix for the recorder field (typically "R")
     * @param javaLangPrefix   the prefix for java.lang classes
     * @return String code for "lambdaInc"
     */
    private static String generateLambdaIncMethod(final String recorderBase, final String recorderSuffix, final String javaLangPrefix) {
        // using variable names as short as possible to compress the code
        // Note: Since this method is now outside the __CLR inner class, we need to reference R as __CLR.R
        final String recorderRef = recorderBase + "." + recorderSuffix;
        final StringBuilder str = new StringBuilder()
                .append(JAVA_LANG_SUPPRESSWARNINGS).append(QUOTE).append("unchecked").append(QUOTE).append(") ")
                .append(PUBLIC_STATIC).append("<I, T extends I> I ")
                .append(LAMBDA_INC_METHOD)
                .append("(final int i,final T l,final int si){")
                .append(JAVA_LANG_REFLECT_INVOCATION_HANDLER).append(" h=").append(NEW).append(JAVA_LANG_REFLECT_INVOCATION_HANDLER).append("(){")
                .append(PUBLIC)
                .append(javaLangPrefix)
                .append("Object invoke(")
                .append(javaLangPrefix)
                .append("Object p,").append(JAVA_LANG_REFLECT_METHOD).append(" m,")
                .append(javaLangPrefix)
                .append("Object[] a) ")
                .append("throws Throwable{")
                .append(recorderRef)
                .append(".inc(i);")
                .append(recorderRef)
                .append(".inc(si);")
                .append("try{return m.invoke(l,a);}catch(").append(JAVA_LANG_REFLECT_INVOCATION_TARGET_EXCEPTION).append(" e){")
                .append("throw e.getCause()!=null?e.getCause():").append(NEW).append("RuntimeException(").append(QUOTE).append("OpenClover failed to invoke instrumented lambda").append(QUOTE).append(",e);")
                .append("}}};")
                .append("return (I)").append(JAVA_LANG_REFLECT_PROXY).append(".newProxyInstance(l.getClass().getClassLoader(),l.getClass().getInterfaces(),h);")
                .append("}");
        return str.toString();
    }

    /**
     * Return a string containing declaration of a field with a static array containing
     * list of profiles.
     *
     * @param profiles list of profiles
     * @return String
     */
    static String generateCloverProfilesField(List<CloverProfile> profiles) {
        // public static CloverProfile[] profiles = {
        String str = PUBLIC_STATIC + CloverProfile.class.getName() + "[] profiles = { ";
        str += generateCloverProfilesNewInstances(profiles);
        str += "};";
        return str;
    }

    /**
     * Helper method for generateCloverProfilesField.
     *
     * @param profiles list of runtime profiles
     * @return String
     */
    private static String generateCloverProfilesNewInstances(List<CloverProfile> profiles) {
        StringBuilder str = new StringBuilder();
        if (profiles != null) {
            for (Iterator<CloverProfile> iter = profiles.iterator(); iter.hasNext(); ) {
                CloverProfile profile = iter.next();
                // new CloverProfile(
                str.append(NEW).append(CloverProfile.class.getName()).append("(");
                // "default",
                str.append(asUnicodeString(profile.getName())).append(COMMA_SPACE);
                // "FIXED",
                str.append(QUOTE).append(profile.getCoverageRecorder()).append(QUOTE).append(COMMA_SPACE);
                // "host=localhost;timeout=500") or null)
                if (profile.getDistributedCoverage() != null) {
                    str.append(asUnicodeString(profile.getDistributedCoverage().getConfigString())).append(")");
                } else {
                    str.append("null)");
                }
                if (iter.hasNext()) {
                    str.append(",");
                }
            }
        }
        return str.toString();
    }

    /**
     * Returns a unicode representation of the provided string, for example:
     * "abc" -&gt; "\u0061\u0062\u0063"
     * In addition it doubles every "\" character in output sequence.
     */
    public static String asUnicodeString(String str) {
        if (str == null) {
            return "null";
        }

        StringBuilder res = new StringBuilder(QUOTE);
        for (char c : str.toCharArray()) {
            // Because of fact that unicode escape sequences are being processed by javac compiler at the very beginning
            // (they are just being treated as an alternative way of writing characters in a text file) and a fact that
            // we will put unicode string into a java source file we must double the backslash character, because first
            // backslash will be treated as an escape character and not a backslash.
            //
            // Example without double backslash:
            //  '\tea' --to-unicode-> "\u005c\u0074\u0065\u0061"       --javac-> "\tea"  = '<tab>ea'
            // Example with double backslash:
            //  '\tea' --to-unicode-> "\u005c\u005c\u0074\u0065\u0061" --javac-> "\\tea" = '\tea'
            if (c == '\\') {
                res.append(String.format(Locale.US, "\\u%04x\\u%04x", (int) c, (int) c));
            } else {
                res.append(String.format(Locale.US, "\\u%04x", (int) c));
            }
        }
        res.append(QUOTE);
        return res.toString();
    }

    // Helper methods that replicate the Bindings class functionality

    /**
     * Generates code string for Clover.getNullRecorder() call.
     */
    private static String getCloverGetNullRecorder() {
        return Clover.class.getName() + ".getNullRecorder()";
    }

    /**
     * Generates code string for Clover.getRecorder(...) call.
     */
    private static String getCloverGetRecorder(String initString, String registryVersion, String recorderCfg,
                                                String maxDataIndex, String profiles, String props) {
        return Clover.class.getName() + ".getRecorder(" +
                initString + "," +
                registryVersion + "," +
                recorderCfg + "," +
                maxDataIndex + "," +
                profiles + "," +
                props + ")";
    }

    /**
     * Generates code string for Clover.l(...) call (logging).
     */
    private static String getCloverL(String message) {
        return Clover.class.getName() + ".l(" + message + ")";
    }

    /**
     * Generates code string for CloverVersionInfo.getBuildStamp() call.
     */
    private static String getCloverVersionInfoGetBuildStamp() {
        return CloverVersionInfo.class.getName() + ".getBuildStamp()";
    }

    /**
     * Generates code string for CloverVersionInfo.getReleaseNum() call.
     */
    private static String getCloverVersionInfoGetReleaseNum() {
        return CloverVersionInfo.class.getName() + ".getReleaseNum()";
    }

    /**
     * Generates code string for CloverVersionInfo.oldVersionInClasspath() call.
     */
    private static String getCloverVersionInfoOldVersionInClasspath() {
        return CloverVersionInfo.class.getName() + ".oldVersionInClasspath()";
    }
}
