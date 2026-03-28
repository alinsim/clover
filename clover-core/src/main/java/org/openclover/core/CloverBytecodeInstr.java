package org.openclover.core;

import org.openclover.core.instr.java.bytecode.HybridInstrumenter;
import org.openclover.runtime.Logger;
import org.openclover.runtime.api.CloverException;

import java.io.File;
import java.io.IOException;

/**
 * CLI entry point for Phase 2 hybrid bytecode instrumentation.
 * Scans compiled .class files for generated methods (Lombok, annotation processors,
 * compiler-generated) and instruments them with coverage recording calls.
 *
 * <p>This runs AFTER compilation, complementing Phase 1 source instrumentation
 * which runs BEFORE compilation.</p>
 *
 * <p>Usage: CloverBytecodeInstr -i &lt;registry&gt; -c &lt;classDir&gt; [-t &lt;testClassDir&gt;]</p>
 */
public class CloverBytecodeInstr {

    private static final String FLAG_INITSTRING = "-i";
    private static final String FLAG_CLASSDIR = "-c";
    private static final String FLAG_TESTCLASSDIR = "-t";
    private static final String FLAG_DB_VERSION = "--dbVersion";
    private static final String FLAG_CFG_BITS = "--cfgBits";
    private static final String PHASE2_FAILED_PREFIX = "Phase 2 bytecode instrumentation failed: ";

    /**
     * @return 0 on success, non-zero on failure
     */
    public static int mainImpl(String[] args) {
        String initString = null;
        String classDir = null;
        String testClassDir = null;
        long dbVersion = 0;
        long cfgBits = 0;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case FLAG_INITSTRING:
                    initString = args[++i];
                    break;
                case FLAG_CLASSDIR:
                    classDir = args[++i];
                    break;
                case FLAG_TESTCLASSDIR:
                    testClassDir = args[++i];
                    break;
                case FLAG_DB_VERSION:
                    dbVersion = Long.parseLong(args[++i]);
                    break;
                case FLAG_CFG_BITS:
                    cfgBits = Long.parseLong(args[++i]);
                    break;
                default:
                    break;
            }
        }

        if (initString == null || classDir == null) {
            Logger.getInstance().error("Usage: CloverBytecodeInstr -i <registry> -c <classDir> [-t <testClassDir>]");
            return 1;
        }

        return run(initString, classDir, testClassDir, dbVersion, cfgBits);
    }

    /**
     * Programmatic entry point for Maven plugin and other integrations.
     */
    public static int run(String registryPath, String classDir, String testClassDir,
                          long dbVersion, long cfgBits) {
        try {
            HybridInstrumenter hybrid = new HybridInstrumenter();
            File registryFile = new File(registryPath);

            // Instrument main classes
            HybridInstrumenter.Result mainResult = hybrid.instrument(
                    new File(classDir), registryFile, dbVersion, cfgBits);

            // Instrument test classes if specified
            HybridInstrumenter.Result testResult = null;
            if (testClassDir != null) {
                testResult = hybrid.instrument(
                        new File(testClassDir), registryFile, dbVersion, cfgBits);
            }

            int totalFound = mainResult.getGeneratedMethodsFound()
                    + (testResult != null ? testResult.getGeneratedMethodsFound() : 0);
            int totalModified = mainResult.getClassFilesModified()
                    + (testResult != null ? testResult.getClassFilesModified() : 0);

            if (totalFound > 0) {
                Logger.getInstance().info("Phase 2 bytecode instrumentation complete: "
                        + totalFound + " generated methods found, "
                        + totalModified + " class files modified");
            }

            return 0;
        } catch (IOException e) {
            Logger.getInstance().error(PHASE2_FAILED_PREFIX + e.getMessage(), e);
            return 1;
        } catch (CloverException e) {
            Logger.getInstance().error(PHASE2_FAILED_PREFIX + e.getMessage(), e);
            return 1;
        }
    }
}
