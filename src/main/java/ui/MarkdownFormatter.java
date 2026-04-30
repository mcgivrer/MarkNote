package ui;

/**
 * Pure string-transformation helpers for Markdown inline formatting.
 *
 * <p>All methods are stateless and have no JavaFX dependency, which makes
 * them straightforward to unit-test without a running JavaFX runtime.</p>
 */
public final class MarkdownFormatter {

    private MarkdownFormatter() {}

    /**
     * Applies (or toggles off) a heading prefix on a raw paragraph line.
     *
     * <p>If the line already carries exactly {@code level} {@code #} signs,
     * the prefix is removed (toggle-off). Otherwise the existing prefix (if
     * any) is replaced by the requested one.</p>
     *
     * @param line  the current line text (no trailing newline)
     * @param level heading level 1–6
     * @return the transformed line
     */
    public static String applyHeading(String line, int level) {
        // Strip any existing heading prefix
        String stripped = line.replaceFirst("^#{1,6} ?", "");
        String prefix = "#".repeat(level);
        // Toggle off when the line already has exactly this level
        if (line.equals(prefix + " " + stripped) || line.equals(prefix + stripped)) {
            return stripped;
        }
        return prefix + " " + stripped;
    }

    /**
     * Wraps or unwraps a text fragment with a Markdown inline marker.
     *
     * <p>If {@code text} is already surrounded by {@code marker} on both
     * sides, the markers are removed (toggle-off). Otherwise {@code marker}
     * is prepended and appended.</p>
     *
     * @param text   the currently selected text
     * @param marker the delimiter (e.g. {@code "**"} for bold, {@code "*"} for italic)
     * @return the toggled text
     */
    public static String toggleWrap(String text, String marker) {
        if (text.startsWith(marker) && text.endsWith(marker)
                && text.length() > 2 * marker.length()) {
            return text.substring(marker.length(), text.length() - marker.length());
        }
        return marker + text + marker;
    }

    /**
     * Builds a Markdown link insertion string: {@code [](url)}.
     *
     * <p>After insertion the caret should be placed at
     * {@link #linkCaretOffset()} relative to the insertion start
     * (i.e. between {@code [} and {@code ]}).</p>
     *
     * @param url the text to use as the URL part
     * @return the link snippet, e.g. {@code "[](https://example.com)"}
     */
    public static String buildLink(String url) {
        return "[](" + url + ")";
    }

    /**
     * Builds a Markdown image insertion string: {@code ![](url)}.
     *
     * <p>After insertion the caret should be placed at
     * {@link #imageCaretOffset()} relative to the insertion start
     * (i.e. between {@code [} and {@code ]}).</p>
     *
     * @param url the text to use as the URL / path part
     * @return the image snippet, e.g. {@code "![](img.png)"}
     */
    public static String buildImage(String url) {
        return "![](" + url + ")";
    }

    /**
     * Returns the caret offset inside a link snippet built by
     * {@link #buildLink}: position 1 places the cursor between {@code [}
     * and {@code ]}.
     */
    public static int linkCaretOffset() {
        return 1;
    }

    /**
     * Returns the caret offset inside an image snippet built by
     * {@link #buildImage}: position 2 places the cursor between {@code [}
     * and {@code ]}.
     */
    public static int imageCaretOffset() {
        return 2;
    }

    /**
     * Builds a fenced Markdown code block wrapping the given text.
     *
     * <p>The result has the form:
     * <pre>
     * ```
     * text
     * ```
     * </pre>
     * When {@code text} is empty, an empty code block is produced and the
     * caret should be placed at {@link #codeBlockCaretOffset()} to land on
     * the blank line between the fences.</p>
     *
     * @param text the content to wrap (may be empty)
     * @return the fenced code block snippet
     */
    public static String buildCodeBlock(String text) {
        return "```\n" + text + "\n```";
    }

    /**
     * Returns the caret offset inside an empty code block built by
     * {@link #buildCodeBlock}: position 4 ({@code ```\n}) places the cursor
     * on the blank line between the fences.
     */
    public static int codeBlockCaretOffset() {
        return 4;
    }
}
