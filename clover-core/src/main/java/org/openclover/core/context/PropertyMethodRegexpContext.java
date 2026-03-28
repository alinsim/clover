package org.openclover.core.context;

import java.util.regex.Pattern;

/**
 * A regular expression based context searching for getXyz(), setXyz(), isXyz() methods.
 */
public class PropertyMethodRegexpContext extends MethodRegexpContext {
    public PropertyMethodRegexpContext(int index, String name) {
        super(index, name, Pattern.compile("(.* )?public .*(get|set|is)[A-Z0-9].*"), 1, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }
}
