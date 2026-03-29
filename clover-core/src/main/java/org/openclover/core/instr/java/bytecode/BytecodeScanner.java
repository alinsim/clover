package org.openclover.core.instr.java.bytecode;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans .class files and identifies methods not present in Phase 1 source registry.
 */
public class BytecodeScanner {
    private final MethodFilter filter;

    public BytecodeScanner() {
        this(new MethodFilter());
    }

    public BytecodeScanner(MethodFilter filter) {
        this.filter = filter;
    }

    /**
     * Scans compiled .class files and identifies methods not present
     * in the Phase 1 source registry.
     *
     * @param classDir root directory containing .class files
     * @param phase1Methods map of className -> set of MethodSignatureKey from Phase 1
     * @return list of methods found in bytecode but not in Phase 1
     */
    public List<GeneratedMethod> findGeneratedMethods(
            File classDir,
            Map<String, Set<MethodSignatureKey>> phase1Methods) throws IOException {

        List<GeneratedMethod> generated = new ArrayList<>();
        scanDirectory(classDir, classDir, phase1Methods, generated);
        return generated;
    }

    private void scanDirectory(
            File dir,
            File rootDir,
            Map<String, Set<MethodSignatureKey>> phase1Methods,
            List<GeneratedMethod> generated) throws IOException {

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, rootDir, phase1Methods, generated);
            } else if (file.getName().endsWith(".class")) {
                scanClassFile(file, rootDir, phase1Methods, generated);
            }
        }
    }

    private void scanClassFile(
            File classFile,
            File rootDir,
            Map<String, Set<MethodSignatureKey>> phase1Methods,
            List<GeneratedMethod> generated) throws IOException {

        try (FileInputStream fis = new FileInputStream(classFile)) {
            ClassReader reader = new ClassReader(fis);
            String className = reader.getClassName(); // e.g., "com/example/User"

            // Skip classes not belonging to the project: if neither the class itself
            // nor its outer class (for inner classes like User$Builder) is in Phase 1
            // registry, it's a dependency class (e.g., shaded jar) — not our code.
            if (!isProjectClass(className, phase1Methods)) {
                return;
            }

            // Get the set of Phase 1 methods for this class (empty set if class not in Phase 1)
            Set<MethodSignatureKey> registeredMethods =
                phase1Methods.getOrDefault(className, Collections.emptySet());

            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions) {

                    // Apply filter first
                    if (!filter.shouldInstrument(access, name, descriptor)) {
                        return null;
                    }

                    // Check if method exists in Phase 1 registry
                    MethodSignatureKey key = MethodSignatureKey.fromBytecode(name, descriptor);
                    if (!registeredMethods.contains(key)) {
                        generated.add(new GeneratedMethod(
                            className,
                            name,
                            descriptor,
                            access,
                            null, // sourceFilePath - resolved later by Phase 2 session
                            classFile.getAbsolutePath()
                        ));
                    }

                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }

    /**
     * Returns true if the class belongs to the project (was seen by Phase 1).
     * A class belongs to the project if:
     * - It is directly in the Phase 1 registry, OR
     * - Its outer class (for inner classes like User$Builder) is in the registry
     */
    private boolean isProjectClass(String className, Map<String, Set<MethodSignatureKey>> phase1Methods) {
        if (phase1Methods.containsKey(className)) {
            return true;
        }
        // Check outer class for inner classes (className contains '$')
        int dollarIndex = className.indexOf('$');
        if (dollarIndex > 0) {
            String outerClass = className.substring(0, dollarIndex);
            return phase1Methods.containsKey(outerClass);
        }
        return false;
    }
}
