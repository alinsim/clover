package org.openclover.core.instr.java;

/**
 * Legacy stub for ANTLR-generated code compatibility.
 * This class is no longer used for instrumentation (JavaParser handles that now).
 * Kept only so generated JavaRecognizer.java compiles (even though it's never executed).
 */
public class LambdaUtil {
    public static String generateLambdaNameWithId(Object obj, int id) {
        return "lambda$" + id;
    }
}
