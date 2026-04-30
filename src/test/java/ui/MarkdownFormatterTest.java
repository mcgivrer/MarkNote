package ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownFormatterTest {

    // ── applyHeading ──────────────────────────────────────────────────────────

    @Test
    void applyHeading_noExisting_addsPrefix() {
        assertEquals("# Hello", MarkdownFormatter.applyHeading("Hello", 1));
    }

    @Test
    void applyHeading_sameLevel_togglesOff() {
        assertEquals("Hello", MarkdownFormatter.applyHeading("# Hello", 1));
    }

    @Test
    void applyHeading_differentLevel_replacesPrefix() {
        assertEquals("## Hello", MarkdownFormatter.applyHeading("# Hello", 2));
    }

    @Test
    void applyHeading_level3_addsPrefix() {
        assertEquals("### World", MarkdownFormatter.applyHeading("World", 3));
    }

    @Test
    void applyHeading_sameLevel6_togglesOff() {
        assertEquals("Deep", MarkdownFormatter.applyHeading("###### Deep", 6));
    }

    @Test
    void applyHeading_emptyLine_addsPrefix() {
        assertEquals("# ", MarkdownFormatter.applyHeading("", 1));
    }

    // ── toggleWrap ────────────────────────────────────────────────────────────

    @Test
    void toggleWrap_bold_wraps() {
        assertEquals("**foo**", MarkdownFormatter.toggleWrap("foo", "**"));
    }

    @Test
    void toggleWrap_bold_unwraps() {
        assertEquals("foo", MarkdownFormatter.toggleWrap("**foo**", "**"));
    }

    @Test
    void toggleWrap_italic_wraps() {
        assertEquals("*bar*", MarkdownFormatter.toggleWrap("bar", "*"));
    }

    @Test
    void toggleWrap_italic_unwraps() {
        assertEquals("bar", MarkdownFormatter.toggleWrap("*bar*", "*"));
    }

    @Test
    void toggleWrap_markerOnly_doesNotUnwrap() {
        // "****" length == 2 * marker.length() (not strictly >) → wraps instead of unwrapping
        assertEquals("**" + "****" + "**", MarkdownFormatter.toggleWrap("****", "**"));
    }

    // ── buildLink ─────────────────────────────────────────────────────────────

    @Test
    void buildLink_formatsCorrectly() {
        assertEquals("[](https://example.com)", MarkdownFormatter.buildLink("https://example.com"));
    }

    @Test
    void buildLink_emptyUrl() {
        assertEquals("[]()", MarkdownFormatter.buildLink(""));
    }

    // ── buildImage ────────────────────────────────────────────────────────────

    @Test
    void buildImage_formatsCorrectly() {
        assertEquals("![](img.png)", MarkdownFormatter.buildImage("img.png"));
    }

    @Test
    void buildImage_emptyUrl() {
        assertEquals("![]()", MarkdownFormatter.buildImage(""));
    }

    // ── caret offsets ─────────────────────────────────────────────────────────

    @Test
    void linkCaretOffset_isOne() {
        assertEquals(1, MarkdownFormatter.linkCaretOffset());
    }

    @Test
    void imageCaretOffset_isTwo() {
        assertEquals(2, MarkdownFormatter.imageCaretOffset());
    }

    // ── buildCodeBlock ──────────────────────────────────────────────────

    @Test
    void buildCodeBlock_withContent() {
        assertEquals("```\nfoo\n```", MarkdownFormatter.buildCodeBlock("foo"));
    }

    @Test
    void buildCodeBlock_empty() {
        assertEquals("```\n\n```", MarkdownFormatter.buildCodeBlock(""));
    }

    @Test
    void codeBlockCaretOffset_isFour() {
        // "```\n" is 4 characters
        assertEquals(4, MarkdownFormatter.codeBlockCaretOffset());
    }
}
