package org.openclover.core.instr.java;

import java.io.File;

/**
 * Holds file-level information for Java source instrumentation.
 */
public class FileStructureInfo {

    private String packageName = "";
    private File file = null;
    private boolean suppressFallthroughWarnings = false;

    public FileStructureInfo(File file) {
        this.file = file;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public File getFile() {
        return file;
    }

    public boolean isSuppressFallthroughWarnings() {
        return suppressFallthroughWarnings;
    }

    public void setSuppressFallthroughWarnings(boolean suppressFallthroughWarnings) {
        this.suppressFallthroughWarnings = suppressFallthroughWarnings;
    }
}
