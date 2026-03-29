package org.openclover.core.instr.java.bytecode;

import org.openclover.core.api.registry.ClassInfo;
import org.openclover.core.api.registry.FileInfo;
import org.openclover.core.api.registry.MethodInfo;
import org.openclover.core.api.registry.PackageInfo;
import org.openclover.core.api.registry.ProjectInfo;
import org.openclover.core.context.ContextSetImpl;
import org.openclover.core.registry.Clover2Registry;
import org.openclover.core.registry.FixedSourceRegion;
import org.openclover.core.registry.entities.BasicElementInfo;
import org.openclover.core.registry.entities.FullClassInfo;
import org.openclover.core.registry.entities.FullFileInfo;
import org.openclover.core.registry.entities.FullMethodInfo;
import org.openclover.core.registry.entities.MethodSignature;
import org.openclover.core.spi.lang.LanguageConstruct;
import org.openclover.runtime.Logger;
import org.openclover.runtime.api.CloverException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * End-to-end hybrid instrumentation pipeline: scan → allocate → instrument.
 *
 * <p>Phase 2 of hybrid instrumentation. Scans compiled .class files to find
 * methods not present in Phase 1's source registry, assigns coverage indices,
 * and modifies bytecode to inject R.inc(N) at method entry.</p>
 */
public class HybridInstrumenter {

    private final BytecodeScanner scanner;
    private final MethodFilter filter;

    public HybridInstrumenter() {
        this.filter = new MethodFilter();
        this.scanner = new BytecodeScanner(filter);
    }

    public HybridInstrumenter(BytecodeScanner scanner) {
        this.filter = new MethodFilter();
        this.scanner = scanner;
    }

    /**
     * Result of hybrid instrumentation.
     */
    public static class Result {
        private final int generatedMethodsFound;
        private final int classFilesModified;
        private final int totalMaxDataIndex;

        public Result(int generatedMethodsFound, int classFilesModified, int totalMaxDataIndex) {
            this.generatedMethodsFound = generatedMethodsFound;
            this.classFilesModified = classFilesModified;
            this.totalMaxDataIndex = totalMaxDataIndex;
        }

        public int getGeneratedMethodsFound() {
            return generatedMethodsFound;
        }

        public int getClassFilesModified() {
            return classFilesModified;
        }

        public int getTotalMaxDataIndex() {
            return totalMaxDataIndex;
        }
    }

    /**
     * Runs the full hybrid instrumentation pipeline on a compiled classes directory.
     *
     * @param classDir     compiled classes directory (e.g., target/classes)
     * @param registryFile Clover registry file from Phase 1
     * @param dbVersion    registry version (from Phase 1 config)
     * @param cfgBits      recorder config bits (from Phase 1 config)
     * @return result with counts of what was found and modified
     */
    public Result instrument(File classDir, File registryFile, long dbVersion, long cfgBits)
            throws IOException, CloverException {

        if (!classDir.isDirectory()) {
            Logger.getInstance().verbose("Hybrid instrumentation: class directory does not exist: " + classDir);
            return new Result(0, 0, 0);
        }

        // 1. Open Phase 1 registry
        Clover2Registry registry = Clover2Registry.fromFile(registryFile);
        if (registry == null) {
            Logger.getInstance().warn("Hybrid instrumentation: registry file not found: " + registryFile);
            return new Result(0, 0, 0);
        }

        // 2. Build Phase 1 method set from registry
        Map<String, Set<MethodSignatureKey>> phase1Methods = buildPhase1MethodSet(registry);

        // 3. Scan .class files for generated methods
        List<GeneratedMethod> generatedMethods = scanner.findGeneratedMethods(classDir, phase1Methods);

        if (generatedMethods.isEmpty()) {
            Logger.getInstance().verbose("Hybrid instrumentation: no generated methods found");
            return new Result(0, 0, registry.getProject().getDataLength());
        }

        Logger.getInstance().info("Hybrid instrumentation: found " + generatedMethods.size()
                + " generated methods in " + classDir);

        // 4. Allocate indices for ALL generated methods
        int phase1DataLength = registry.getProject().getDataLength();
        Phase2IndexAllocator allocator = new Phase2IndexAllocator(phase1DataLength);
        Map<GeneratedMethod, Integer> indexMap = allocator.allocateIndices(generatedMethods);
        int totalMaxIndex = allocator.getTotalMaxIndex();

        // 5. Roll up generated methods to their parent classes in the registry.
        //    Inner class methods (User$UserBuilder) are added to the parent class (User).
        //    If no parent exists in Phase 1, the methods are skipped (orphan handling).
        try {
            boolean updated = rollupGeneratedMethods(registry, generatedMethods, indexMap);
            if (updated) {
                registry.saveAndOverwriteFile();
            }
        } catch (Exception e) {
            throw new CloverException("Phase 2 registry rollup failed", e);
        }

        // 6. Create instrumenter with recorder config
        BytecodeInstrumenter.RecorderConfig recorderConfig = new BytecodeInstrumenter.RecorderConfig(
                registryFile.getAbsolutePath(),
                dbVersion,
                cfgBits,
                totalMaxIndex
        );
        BytecodeInstrumenter instrumenter = new BytecodeInstrumenter(recorderConfig);

        // 6. Group by class file and instrument
        Map<String, Map<String, Integer>> byClassFile = groupByClassFile(generatedMethods, indexMap);
        int classFilesModified = 0;

        for (Map.Entry<String, Map<String, Integer>> entry : byClassFile.entrySet()) {
            String classFilePath = entry.getKey();
            Map<String, Integer> methodIndices = entry.getValue();

            File classFile = new File(classFilePath);
            byte[] originalBytes = Files.readAllBytes(classFile.toPath());

            byte[] instrumentedBytes = instrumenter.instrument(originalBytes, methodIndices);
            if (instrumentedBytes != null) {
                Files.write(classFile.toPath(), instrumentedBytes);
                classFilesModified++;

                if (Logger.isDebug()) {
                    Logger.getInstance().debug("  Instrumented " + methodIndices.size()
                            + " methods in " + classFile.getName());
                }
            }
        }

        Logger.getInstance().info("Hybrid instrumentation: modified " + classFilesModified
                + " class files, total coverage slots: " + totalMaxIndex);

        return new Result(generatedMethods.size(), classFilesModified, totalMaxIndex);
    }

    /**
     * Builds the Phase 1 method lookup: className -> Set of MethodSignatureKey.
     */
    private Map<String, Set<MethodSignatureKey>> buildPhase1MethodSet(Clover2Registry registry) {
        Map<String, Set<MethodSignatureKey>> result = new HashMap<>();
        ProjectInfo project = registry.getProject();

        for (PackageInfo pkg : project.getAllPackages()) {
            for (FileInfo file : pkg.getFiles()) {
                for (ClassInfo clazz : file.getClasses()) {
                    collectMethodKeys(clazz, result);
                }
            }
        }
        return result;
    }

    private void collectMethodKeys(ClassInfo clazz, Map<String, Set<MethodSignatureKey>> result) {
        String className = clazz.getQualifiedName().replace('.', '/');
        Set<MethodSignatureKey> keys = result.computeIfAbsent(className, k -> new HashSet<>());
        for (MethodInfo method : clazz.getMethods()) {
            keys.add(MethodSignatureKey.fromMethodInfo(method));
        }
        for (ClassInfo inner : clazz.getClasses()) {
            collectMethodKeys(inner, result);
        }
    }

    /**
     * Rolls up generated methods to their parent classes in the registry.
     * For inner classes (e.g., User$UserBuilder), methods are added to the top-level parent (User).
     * If no parent exists in Phase 1, the methods are skipped (orphan handling).
     * Uses pre-allocated indices from Phase2IndexAllocator.
     */
    private boolean rollupGeneratedMethods(
            Clover2Registry registry,
            List<GeneratedMethod> generatedMethods,
            Map<GeneratedMethod, Integer> indexMap) {

        if (generatedMethods.isEmpty()) {
            return false;
        }

        boolean updated = false;

        for (GeneratedMethod genMethod : generatedMethods) {
            String className = genMethod.getClassName();

            // Resolve parent class name (strip inner class suffix)
            String parentClassName = resolveTopLevelParent(className);

            // Find parent class in registry
            FullClassInfo parentClass = findClass(registry, parentClassName);

            if (parentClass == null) {
                // Orphan - no parent in Phase 1, skip
                if (Logger.isDebug()) {
                    Logger.getInstance().debug("Skipping orphan generated method: " + className + "." + genMethod.getMethodName());
                }
                continue;
            }

            // Get the file info to calculate relative offset
            FullFileInfo fileInfo = (FullFileInfo) parentClass.getContainingFile();

            if (fileInfo == null) {
                Logger.getInstance().warn("No file info for parent class: " + parentClassName);
                continue;
            }

            // Calculate indices
            int absoluteIndex = indexMap.get(genMethod);
            int fileBaseIndex = fileInfo.getDataIndex();
            int relativeOffset = absoluteIndex - fileBaseIndex;

            // Create method info
            BasicElementInfo elementInfo = new BasicElementInfo(
                new FixedSourceRegion(0, 0),
                relativeOffset,
                1,
                LanguageConstruct.Builtin.METHOD);

            MethodSignature sig = new MethodSignature(genMethod.getMethodName());
            FullMethodInfo fullMethod = new FullMethodInfo(
                parentClass, sig, new ContextSetImpl(), elementInfo, false, null, false);

            fullMethod.setGenerated(true);
            parentClass.addMethod(fullMethod);

            // Update file data length to accommodate the gap
            int newLength = Math.max(fileInfo.getDataLength(), relativeOffset + 1);
            fileInfo.setDataLength(newLength);

            updated = true;
        }

        return updated;
    }

    /**
     * Resolves the top-level parent class name from an inner class name.
     * Example: "com/example/User$UserBuilder" -> "com/example/User"
     */
    private String resolveTopLevelParent(String className) {
        int firstDollar = className.indexOf('$');
        return firstDollar > 0 ? className.substring(0, firstDollar) : className;
    }

    /**
     * Finds a class in the registry by its fully qualified name (with / separators).
     */
    private FullClassInfo findClass(Clover2Registry registry, String classNameWithSlashes) {
        String fqName = classNameWithSlashes.replace('/', '.');

        for (PackageInfo pkg : registry.getProject().getAllPackages()) {
            for (FileInfo file : pkg.getFiles()) {
                for (ClassInfo clazz : file.getClasses()) {
                    if (fqName.equals(clazz.getQualifiedName())) {
                        return (FullClassInfo) clazz;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Groups generated methods by class file path, building the methodIndices map
     * that BytecodeInstrumenter expects (methodName+descriptor -> index).
     */
    private Map<String, Map<String, Integer>> groupByClassFile(
            List<GeneratedMethod> methods, Map<GeneratedMethod, Integer> indexMap) {

        Map<String, Map<String, Integer>> result = new HashMap<>();
        for (GeneratedMethod method : methods) {
            String classFilePath = method.getClassFilePath();
            Map<String, Integer> methodIndices = result.computeIfAbsent(classFilePath, k -> new HashMap<>());
            String key = method.getMethodName() + method.getDescriptor();
            methodIndices.put(key, indexMap.get(method));
        }
        return result;
    }
}
