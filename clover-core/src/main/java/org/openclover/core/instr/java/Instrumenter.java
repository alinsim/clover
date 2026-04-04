package org.openclover.core.instr.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openclover.core.api.instrumentation.ConcurrentInstrumentationException;
import org.openclover.core.api.instrumentation.InstrumentationSession;
import org.openclover.core.api.registry.PackageInfo;
import org.openclover.core.cfg.instr.java.JavaInstrumentationConfig;
import org.openclover.core.instr.java.javaparser.AstInstrumenter;
import org.openclover.core.registry.Clover2Registry;
import org.openclover.core.registry.entities.FullFileInfo;
import org.openclover.core.registry.metrics.FileMetrics;
import org.openclover.core.util.CloverUtils;
import org.openclover.core.util.FileUtils;
import org.openclover.runtime.Logger;
import org.openclover.runtime.api.CloverException;
import org.openclover.runtime.util.Formatting;
import org.openclover.runtime.util.IOStreamUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Set;

import static org.openclover.core.util.Sets.newHashSet;

public class Instrumenter {
    private static final String SPACE_OPEN_PAREN = " (";
    private static final String COMMA_ID_EQUALS = ", id=";

    private final JavaInstrumentationConfig config;
    private final Logger log;

    private Clover2Registry registry;
    private InstrumentationSession session;
    private int numFiles;
    private int numClasses;
    private Set<String> packages;
    private int numMethods;
    private int numTestMethods;
    private int loc;
    private int ncloc;

    public Instrumenter(JavaInstrumentationConfig config) {
        this(Logger.getInstance(), config);
    }

    public Instrumenter(Logger log, JavaInstrumentationConfig config) {
        this.log = log;
        this.config = config;
    }

    public JavaInstrumentationConfig getConfig() {
        return config;
    }

    public void startInstrumentation() throws CloverException {
        try {
            final Clover2Registry clover2Registry = Clover2Registry.createOrLoad(config.getRegistryFile(), config.getProjectName());
            if (clover2Registry == null) {
                throw new CloverException("Unable to create or load clover registry located at: " + config.getRegistryFile());
            }
            startInstrumentation(clover2Registry);
        }
        catch (IOException e) {
            throw new CloverException(e);
        }
    }

    public void startInstrumentation(Clover2Registry reg) throws CloverException {
        resetStatistics();

        registry = reg;
        session = registry.startInstr(config.getEncoding());

        log.info("Processing files at " + config.getSourceLevel() + " source level.");
    }

    /**
     * reads the srcFile and produces an instrumented version of that file at the package path rooted at destRoot,
     * creating parent dirs as needed.
     * @param srcFile the file to instrument
     * @param destRoot the destination root dir
     * @param fileEncoding encoding of the file being instrumented, if null then a global setting from the
     *                     <code>config.getEncoding()</code> will be used
     * @return file reference to the instrumented version
     * @throws CloverException if something goes wrong
     */
    public File instrument(@NotNull final File srcFile,
                           @NotNull final File destRoot,
                           @Nullable final String fileEncoding) throws CloverException {
        if (registry == null) {
            throw new IllegalStateException("Instrumenter not initialized.");
        }

        File instrTmp = null;
        Writer out = null;
        try {
            // perform instrumentation to a tmp file
            instrTmp = File.createTempFile("clover", ".java");
            final String currentFileEncoding = fileEncoding != null ? fileEncoding : config.getEncoding();
            if (currentFileEncoding != null) {
                out = new OutputStreamWriter(Files.newOutputStream(instrTmp.toPath()), currentFileEncoding);
            } else {
                out = new FileWriter(instrTmp);
            }

            final InstrumentationSource fileSource = new FileInstrumentationSource(srcFile, currentFileEncoding);
            final FileStructureInfo structInfo = instrument(fileSource, out, currentFileEncoding);

            // copy file into dest
            File destDir = destRoot;
            String pkgName = structInfo.getPackageName();
            if (!PackageInfo.isDefaultName(pkgName)) {
                destDir = new File(destRoot, CloverUtils.packageNameToPath(pkgName, false));
            }

            if (!destDir.isDirectory() && !destDir.mkdirs()) {
                throw new CloverException("Failed to create destination path " + destDir);
            }


            String srcFileName = srcFile.getName();

            if (srcFileName.indexOf('.') > -1) {
                srcFileName =
                    srcFileName.substring(
                        0,
                        srcFile.getName().lastIndexOf('.'))
                    + '.' + config.getInstrFileExtension();
            }

            File instr = new File(destDir, srcFileName);
            FileUtils.fileCopy(instrTmp, instr);
            log.verbose("Processed '" + srcFile + "' to '" + instr + "'");
            return instr;
        } catch (UnsupportedEncodingException e) {
            log.error(e.getMessage());
            throw new CloverException(e);
        } catch (IOException e) {
            log.error("Error processing " + srcFile);
            log.error(e.getMessage());
            throw new CloverException(e);
        } finally {
            if (instrTmp != null) {
                instrTmp.delete();
            }
            IOStreamUtils.close(out);
        }
    }

    /**
     * registers orig and produces an instrumented version of in to out
     *
     * @param in  the input file to instrument
     * @param out the destination stream
     * @param fileEncoding encoding of the file being instrumented, a <code>null</code> value means undefined
     * @return FileStructureInfo - file reference to the instrumented version
     */
    public FileStructureInfo instrument(final @NotNull InstrumentationSource in, final @NotNull Writer out,
                                        final @Nullable String fileEncoding)
            throws IOException, CloverException {
        // JavaParser-based instrumentation (replaces ANTLR pipeline)
        return instrumentWithJavaParser(in, out, fileEncoding);
    }

    /**
     * Reads the <code>charSequence</code> and returns an instrumented version of it.
     *
     * @param orig         represents a source file location associated the input <code>charSequence</code>
     * @param charSequence source text to be instrumented
     * @param fileEncoding original file encodng, a <code>null</code> value means undefined
     * @return CharSequence instrumented version of sources
     */
    public CharSequence instrument(final @NotNull File orig, final @NotNull CharSequence charSequence,
                                   final @Nullable String fileEncoding)
            throws IOException, CloverException {

        final StringWriter stringWriter = new StringWriter();
        final InstrumentationSource charSequenceSource = new CharSequenceInstrumentationSource(orig, charSequence);
        // ignoring FileStructureInfo return value
        instrument(charSequenceSource, stringWriter, fileEncoding);
        return stringWriter.toString();
    }

    /**
     * Instruments a source file using JavaParser instead of ANTLR.
     * This method is called when config.isUseJavaParser() returns true.
     *
     * @param in the input source to instrument
     * @param out the destination writer
     * @param fileEncoding encoding of the file being instrumented, a <code>null</code> value means undefined
     * @return FileStructureInfo - file reference to the instrumented version
     * @throws IOException if reading or writing fails
     * @throws CloverException if instrumentation fails
     */
    private FileStructureInfo instrumentWithJavaParser(final @NotNull InstrumentationSource in,
                                                       final @NotNull Writer out,
                                                       final @Nullable String fileEncoding)
            throws IOException, CloverException {

        // Set source encoding for the session
        session.setSourceEncoding(fileEncoding);

        final FileStructureInfo fileStructureInfo = AstInstrumenter.instrument(
                in, out, session, config, fileEncoding, registry.getContextStore());

        // Update statistics to match ANTLR path behavior
        final FullFileInfo fileInfo = (FullFileInfo) session.getCurrentFile();
        if (fileInfo != null) {
            updateStatistics(fileInfo);
        }

        // Exit the file after statistics have been collected
        session.exitFile();

        return fileStructureInfo;
    }

    public Clover2Registry endInstrumentation() throws CloverException {
        return endInstrumentation(false);
    }

    public Clover2Registry endInstrumentation(boolean append) throws CloverException {
        try {
            finishAndApply(session);
            if (append) {
                registry.saveAndAppendToFile();
            } else {
                registry.saveAndOverwriteFile();
            }
            double secs = (double)(session.getEndTS() - session.getStartTs())/1000;

            int pkgs = packages.size();
            log.info("OpenClover instrumented "
                    + numFiles + Formatting.pluralizedWord(numFiles, " file")
                    + SPACE_OPEN_PAREN + pkgs + Formatting.pluralizedWord(pkgs, " package")
                    + ").");

            if (numTestMethods > 0) {
                log.info(numTestMethods + " test method" + (numTestMethods != 1 ? "s" : "") + " detected.");
            }
            log.debug("Elapsed time = " + Formatting.format3d(secs) + " secs." +
                    (secs > 0 ? SPACE_OPEN_PAREN + Formatting.format3d((double)numFiles / secs) + " files/sec, " +
                            Formatting.format3d((double)loc/secs)+" srclines/sec)" : ""));
            return registry;
        }
        catch (IOException e) {
            log.error("Error finalising instrumentation: ", e);
            throw new CloverException(e);
        }
    }

    protected void finishAndApply(InstrumentationSession session) throws ConcurrentInstrumentationException {
        session.close();
    }

    private void resetStatistics() {
        numFiles = 0;
        numClasses = 0;
        packages = newHashSet();
        numMethods = 0;
        numTestMethods = 0;
        loc = 0;
        ncloc = 0;
    }

    private void updateStatistics(FullFileInfo finfo) {
        numFiles++;
        FileMetrics metrics = (FileMetrics)finfo.getMetrics();
        numClasses += metrics.getNumClasses();
        packages.add(finfo.getContainingPackage().getName());
        numMethods += metrics.getNumMethods();
        numTestMethods += metrics.getNumTestMethods();
        loc += metrics.getLineCount();
        ncloc += metrics.getNcLineCount();
    }

    public InstrumentationSession getSession() {
        return session;
    }

}
