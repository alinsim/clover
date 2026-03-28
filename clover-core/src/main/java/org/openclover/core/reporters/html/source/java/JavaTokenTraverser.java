package org.openclover.core.reporters.html.source.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaToken;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.openclover.core.api.registry.FileInfo;
import org.openclover.core.reporters.html.source.SourceTraverser;

import java.io.Reader;

import static com.github.javaparser.GeneratedJavaParserConstants.CHARACTER_LITERAL;
import static com.github.javaparser.GeneratedJavaParserConstants.DOT;
import static com.github.javaparser.GeneratedJavaParserConstants.EOF;
import static com.github.javaparser.GeneratedJavaParserConstants.IDENTIFIER;
import static com.github.javaparser.GeneratedJavaParserConstants.IMPORT;
import static com.github.javaparser.GeneratedJavaParserConstants.JAVADOC_COMMENT;
import static com.github.javaparser.GeneratedJavaParserConstants.MULTI_LINE_COMMENT;
import static com.github.javaparser.GeneratedJavaParserConstants.OLD_MAC_EOL;
import static com.github.javaparser.GeneratedJavaParserConstants.PACKAGE;
import static com.github.javaparser.GeneratedJavaParserConstants.SEMICOLON;
import static com.github.javaparser.GeneratedJavaParserConstants.SINGLE_LINE_COMMENT;
import static com.github.javaparser.GeneratedJavaParserConstants.SPACE;
import static com.github.javaparser.GeneratedJavaParserConstants.STRING_LITERAL;
import static com.github.javaparser.GeneratedJavaParserConstants.TEXT_BLOCK_LITERAL;
import static com.github.javaparser.GeneratedJavaParserConstants.UNIX_EOL;
import static com.github.javaparser.GeneratedJavaParserConstants.WINDOWS_EOL;

/**
 * Traverses a Java token stream, informing a JavaSourceListener about
 * the interesting aspects of it - new lines, comments, keywords, identifiers, string literals, imports, packages
 */
public final class JavaTokenTraverser implements SourceTraverser<JavaSourceListener> {
    @Override
    public void traverse(Reader sourceReader, FileInfo fileInfo, JavaSourceListener listener) throws Exception {
        // Read source from Reader into String (JavaParser needs String input)
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = sourceReader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        String source = sb.toString();

        // Configure JavaParser
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        config.setStoreTokens(true);
        JavaParser parser = new JavaParser(config);

        // Parse source
        ParseResult<CompilationUnit> result = parser.parse(source);

        listener.onStartDocument();

        if (!result.getResult().isPresent()) {
            // Fall back to plain text rendering on parse failure
            String[] lines = source.split("\r\n|\r|\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    listener.onNewLine();
                }
                if (!lines[i].isEmpty()) {
                    listener.onChunk(lines[i]);
                }
            }
            listener.onEndDocument();
            return;
        }

        CompilationUnit cu = result.getResult().get();

        // State machine for package/import tracking
        StringBuilder accumName = new StringBuilder();
        boolean gatherPkgIdent = false;
        boolean gatherImportIdent = false;

        // Iterate tokens
        if (cu.getTokenRange().isPresent()) {
            for (JavaToken token : cu.getTokenRange().get()) {
                int kind = token.getKind();
                String text = token.getText();

                if (kind == EOF) {
                    break;
                }

                // Process token by kind
                if (kind == SPACE) {
                    listener.onChunk(text);
                } else if (kind == UNIX_EOL || kind == WINDOWS_EOL || kind == OLD_MAC_EOL) {
                    listener.onNewLine();
                } else if (kind == SINGLE_LINE_COMMENT) {
                    listener.onCommentChunk(text);
                } else if (kind == MULTI_LINE_COMMENT || kind == JAVADOC_COMMENT) {
                    processComment(text, listener);
                } else if (kind == STRING_LITERAL || kind == TEXT_BLOCK_LITERAL || kind == CHARACTER_LITERAL) {
                    listener.onStringLiteral(text);
                } else if (token.getCategory() == JavaToken.Category.KEYWORD) {
                    listener.onKeyword(text);
                    gatherPkgIdent = (kind == PACKAGE);
                    gatherImportIdent = (kind == IMPORT);
                } else if (kind == IDENTIFIER) {
                    if (gatherPkgIdent || gatherImportIdent) {
                        accumName.append(text);
                        if (gatherPkgIdent) {
                            listener.onPackageSegment(accumName.toString(), text);
                        } else {
                            listener.onImportSegment(accumName.toString(), text);
                        }
                    } else {
                        listener.onIdentifier(text);
                    }
                } else if (kind == DOT) {
                    if (gatherPkgIdent || gatherImportIdent) {
                        accumName.append(text);
                    }
                    listener.onChunk(text);
                } else if (kind == SEMICOLON) {
                    listener.onChunk(text);
                    if (gatherImportIdent) {
                        listener.onImport(accumName.toString());
                    }
                    accumName = new StringBuilder();
                    gatherPkgIdent = false;
                    gatherImportIdent = false;
                } else {
                    listener.onChunk(text);
                }
            }
        }

        listener.onEndDocument();
    }

    public static void processWhiteSpace(String whitespace, JavaSourceListener listener) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        while (i < whitespace.length()) {
            boolean atNewLine = false;
            char c1 = whitespace.charAt(i);
            char c2 = (i + 1 < whitespace.length() ? whitespace.charAt(i + 1) : 0);
            if (c1 == '\r' && c2 == '\n') {
                atNewLine = true;
                i++;
            }
            else if (c1 == '\r' || c1 == '\n') {
                atNewLine = true;
            }

            if (atNewLine) {
                if (b.length() > 0) {
                    listener.onChunk(b.toString());
                    b = new StringBuilder();
                }
                listener.onNewLine();
            }
            else {
                b.append(c1);
            }
            i++;
        }
        if (b.length() > 0) {
            listener.onChunk(b.toString());
        }
    }

    public static void processComment(String comment, JavaSourceListener listener) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        boolean inTag = false;
        while (i < comment.length()) {
            boolean atNewLine = false;
            char c1 = comment.charAt(i);
            char c2 = (i + 1 < comment.length() ? comment.charAt(i + 1) : 0);

            if (c1 == '\r' && c2 == '\n') {
                atNewLine = true;
                i++;
            }
            else if (c1 == '\r' || c1 == '\n') {
                atNewLine = true;
            }

            // look for javadoc tags
            if (!inTag && c1 == '@' && Character.isLetter(c2)) {
                inTag = true;
                listener.onCommentChunk(b.toString());
                b = new StringBuilder();
            }
            else if (inTag && (!Character.isLetter(c1))) {
                inTag = false;
                String tag = b.toString();
                if (JavadocTags.contains(tag.substring(1))) {
                    listener.onJavadocTag(tag);
                    b = new StringBuilder();
                }
            }

            if (atNewLine) {
                if (b.length() > 0) {
                    listener.onCommentChunk(b.toString());
                    b = new StringBuilder();
                }
                listener.onNewLine();
            }
            else {
                b.append(c1);
            }
            i++;
        }
        if (b.length() > 0) {
            String left = b.toString();
            if (inTag && JavadocTags.contains(left)) {
                listener.onJavadocTag(left);
            }
            else {
                listener.onCommentChunk(left);
            }
        }
    }
}
