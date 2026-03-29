package org.openclover.core.instr.java.bytecode;

import org.openclover.core.api.instrumentation.ConcurrentInstrumentationException;
import org.openclover.core.api.instrumentation.InstrumentationSession;
import org.openclover.core.api.registry.ClassInfo;
import org.openclover.core.api.registry.FileInfo;
import org.openclover.core.api.registry.MethodInfo;
import org.openclover.core.api.registry.PackageInfo;
import org.openclover.core.api.registry.ProjectInfo;
import org.openclover.core.context.ContextSetImpl;
import org.openclover.core.instr.InstrumentationSessionImpl;
import org.openclover.core.registry.Clover2Registry;
import org.openclover.core.registry.FixedSourceRegion;
import org.openclover.core.registry.entities.MethodSignature;
import org.openclover.core.registry.entities.Modifiers;
import org.openclover.runtime.Logger;
import org.openclover.runtime.api.CloverException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
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

        // 5. Register only NEW classes (not in Phase 1) in registry for report visibility.
        //    Classes that already exist in Phase 1 are skipped to avoid duplicate entries.
        //    Their generated methods still get coverage via R.inc() with allocated indices.
        try {
            boolean registered = registerNewClassesInRegistry(registry, generatedMethods, phase1Methods, indexMap);
            if (registered) {
                registry.saveAndAppendToFile();
            }
        } catch (ConcurrentInstrumentationException e) {
            throw new CloverException("Phase 2 registry update failed", e);
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
     * Registers generated methods in the Clover registry via a Phase 2 instrumentation session.
     * Uses a unique checksum to avoid reusing Phase 1's data indices.
     * Returns a map of GeneratedMethod → session-allocated data index.
     */
    /**
     * Registers generated methods for NEW classes (not in Phase 1) in the registry.
     * Classes that already exist in Phase 1 are skipped to avoid duplicate entries.
     * Uses pre-allocated indices from Phase2IndexAllocator (not session-allocated).
     */
    private boolean registerNewClassesInRegistry(
            Clover2Registry registry, List<GeneratedMethod> generatedMethods,
            Map<String, Set<MethodSignatureKey>> phase1Methods,
            Map<GeneratedMethod, Integer> indexMap)
            throws CloverException, ConcurrentInstrumentationException {

        // Group by className
        Map<String, List<GeneratedMethod>> byClass = new HashMap<>();
        for (GeneratedMethod method : generatedMethods) {
            byClass.computeIfAbsent(method.getClassName(), k -> new ArrayList<>()).add(method);
        }

        // Only register classes NOT in Phase 1
        boolean hasNewClasses = false;
        for (String className : byClass.keySet()) {
            if (!phase1Methods.containsKey(className)) {
                hasNewClasses = true;
                break;
            }
        }

        if (!hasNewClasses) {
            return false;
        }

        InstrumentationSession session = registry.startInstr();
        long phase2Checksum = System.nanoTime();

        for (Map.Entry<String, List<GeneratedMethod>> entry : byClass.entrySet()) {
            String className = entry.getKey();
            List<GeneratedMethod> methods = entry.getValue();

            // Skip classes that already exist in Phase 1
            if (phase1Methods.containsKey(className)) {
                continue;
            }

            String packageName = className.contains("/")
                    ? className.substring(0, className.lastIndexOf('/')).replace('/', '.')
                    : "";
            String simpleClassName = className.contains("/")
                    ? className.substring(className.lastIndexOf('/') + 1)
                    : className;
            String sourceFileName = className.replace('/', File.separatorChar) + "$generated.java";

            session.enterFile(packageName, new File(sourceFileName), 0, 0, 0L, 0L, phase2Checksum++);
            session.enterClass(simpleClassName, new FixedSourceRegion(0, 0),
                    new Modifiers(), false, false, false);

            for (GeneratedMethod method : methods) {
                MethodSignature sig = new MethodSignature(method.getMethodName());
                ((InstrumentationSessionImpl) session).enterMethod(
                        new ContextSetImpl(),
                        new FixedSourceRegion(0, 0),
                        sig,
                        false);
                session.exitMethod(0, 0);
            }

            session.exitClass(0, 0);
            session.exitFile();
        }

        session.close();
        return true;
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
