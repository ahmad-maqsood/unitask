package com.example.ui

import com.example.R
import com.example.ui.screens.markdownLessons
import com.example.util.markdown.Inline
import com.example.util.markdown.MarkdownBlock
import com.example.util.markdown.parseMarkdown
import org.junit.Assert.assertTrue
import org.junit.Test

/** The guide must only teach what the app's own preview can show. */
class MarkdownGuideTest {

    private fun blocksFor(titleRes: Int): List<MarkdownBlock> =
        parseMarkdown(markdownLessons.first { it.titleRes == titleRes }.example)

    private inline fun <reified T : MarkdownBlock> assertHas(titleRes: Int) {
        assertTrue("No ${T::class.simpleName}", blocksFor(titleRes).any { it is T })
    }

    private fun paragraphInlines(titleRes: Int): List<Inline> =
        blocksFor(titleRes).filterIsInstance<MarkdownBlock.Paragraph>().flatMap { it.inlines }

    @Test
    fun blockExamplesRenderAsTheirTopic() {
        assertHas<MarkdownBlock.Heading>(R.string.markdown_guide_headings_title)
        assertHas<MarkdownBlock.BulletItem>(R.string.markdown_guide_lists_title)
        assertHas<MarkdownBlock.NumberedItem>(R.string.markdown_guide_lists_title)
        assertHas<MarkdownBlock.ChecklistItem>(R.string.markdown_guide_checklist_title)
        assertHas<MarkdownBlock.Quote>(R.string.markdown_guide_quote_title)
        assertHas<MarkdownBlock.CodeBlock>(R.string.markdown_guide_code_title)
        assertHas<MarkdownBlock.Table>(R.string.markdown_guide_tables_title)
        assertHas<MarkdownBlock.Rule>(R.string.markdown_guide_divider_title)
    }

    @Test
    fun inlineExamplesRenderAsTheirTopic() {
        val emphasis = paragraphInlines(R.string.markdown_guide_emphasis_title)
        assertTrue(emphasis.any { it is Inline.Bold })
        assertTrue(emphasis.any { it is Inline.Italic })
        assertTrue(emphasis.any { it is Inline.Strike })
        assertTrue(paragraphInlines(R.string.markdown_guide_code_title).any { it is Inline.Code })
        assertTrue(paragraphInlines(R.string.markdown_guide_links_title).count { it is Inline.Link } == 2)
    }
}
