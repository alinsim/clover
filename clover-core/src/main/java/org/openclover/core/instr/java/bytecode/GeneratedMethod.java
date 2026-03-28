package org.openclover.core.instr.java.bytecode;

/**
 * A method found in compiled bytecode that was not present in Phase 1
 * source instrumentation registry. Candidate for bytecode-level instrumentation.
 */
public final class GeneratedMethod {
    private final String className;      // internal name: "com/example/User"
    private final String methodName;     // "getName"
    private final String descriptor;     // "(Ljava/lang/String;)V"
    private final int access;            // ACC_PUBLIC etc.
    private final String sourceFilePath; // owning source file, may be null for generated-only classes
    private final String classFilePath;  // absolute path to .class file

    public GeneratedMethod(String className, String methodName, String descriptor, int access,
                          String sourceFilePath, String classFilePath) {
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.access = access;
        this.sourceFilePath = sourceFilePath;
        this.classFilePath = classFilePath;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public int getAccess() {
        return access;
    }

    public String getSourceFilePath() {
        return sourceFilePath;
    }

    public String getClassFilePath() {
        return classFilePath;
    }

    @Override
    public String toString() {
        return "GeneratedMethod{" +
            "className='" + className + '\'' +
            ", methodName='" + methodName + '\'' +
            ", descriptor='" + descriptor + '\'' +
            ", access=" + access +
            ", sourceFilePath='" + sourceFilePath + '\'' +
            ", classFilePath='" + classFilePath + '\'' +
            '}';
    }
}
