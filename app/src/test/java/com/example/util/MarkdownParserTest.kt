package com.example.util

import com.example.util.markdown.Inline
import com.example.util.markdown.MarkdownBlock
import com.example.util.markdown.TableAlign
import com.example.util.markdown.parseInlines
import com.example.util.markdown.parseMarkdown
import com.example.util.markdown.referencedImageNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    // -------------------------------------------------------------------------
    // 1. Block types
    // -------------------------------------------------------------------------

    @Test
    fun headingLevelOneParsedCorrectly() {
        val blocks = parseMarkdown("# Title")
        assertEquals(1, blocks.size)
        val h = blocks[0] as MarkdownBlock.Heading
        assertEquals(1, h.level)
        assertEquals(listOf(Inline.Text("Title")), h.inlines)
    }

    @Test
    fun headingLevelThreeParsedCorrectly() {
        val blocks = parseMarkdown("### Section Title")
        assertEquals(1, blocks.size)
        val h = blocks[0] as MarkdownBlock.Heading
        assertEquals(3, h.level)
        assertEquals(listOf(Inline.Text("Section Title")), h.inlines)
    }

    @Test
    fun headingLevelSixParsedCorrectly() {
        val blocks = parseMarkdown("###### Deep Heading")
        assertEquals(1, blocks.size)
        val h = blocks[0] as MarkdownBlock.Heading
        assertEquals(6, h.level)
        assertEquals(listOf(Inline.Text("Deep Heading")), h.inlines)
    }

    @Test
    fun headingLevelSevenTreatedAsParagraph() {
        val blocks = parseMarkdown("####### Seven Hashes")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        val p = blocks[0] as MarkdownBlock.Paragraph
        assertEquals(listOf(Inline.Text("####### Seven Hashes")), p.inlines)
    }

    @Test
    fun paragraphSingleLineParsed() {
        val blocks = parseMarkdown("Simple paragraph text.")
        assertEquals(1, blocks.size)
        val p = blocks[0] as MarkdownBlock.Paragraph
        assertEquals(listOf(Inline.Text("Simple paragraph text.")), p.inlines)
    }

    @Test
    fun paragraphMultiLinePreservesNewlines() {
        val blocks = parseMarkdown("First line\nSecond line\nThird line")
        assertEquals(1, blocks.size)
        val p = blocks[0] as MarkdownBlock.Paragraph
        assertEquals(listOf(Inline.Text("First line\nSecond line\nThird line")), p.inlines)
    }

    @Test
    fun bulletItemWithDash() {
        val blocks = parseMarkdown("- Dash item")
        assertEquals(1, blocks.size)
        val b = blocks[0] as MarkdownBlock.BulletItem
        assertEquals(0, b.depth)
        assertEquals(listOf(Inline.Text("Dash item")), b.inlines)
    }

    @Test
    fun bulletItemWithAsterisk() {
        val blocks = parseMarkdown("* Asterisk item")
        assertEquals(1, blocks.size)
        val b = blocks[0] as MarkdownBlock.BulletItem
        assertEquals(0, b.depth)
        assertEquals(listOf(Inline.Text("Asterisk item")), b.inlines)
    }

    @Test
    fun bulletItemWithPlus() {
        val blocks = parseMarkdown("+ Plus item")
        assertEquals(1, blocks.size)
        val b = blocks[0] as MarkdownBlock.BulletItem
        assertEquals(0, b.depth)
        assertEquals(listOf(Inline.Text("Plus item")), b.inlines)
    }

    @Test
    fun numberedItemWithDot() {
        val blocks = parseMarkdown("1. First item")
        assertEquals(1, blocks.size)
        val n = blocks[0] as MarkdownBlock.NumberedItem
        assertEquals(0, n.depth)
        assertEquals(1, n.number)
        assertEquals(listOf(Inline.Text("First item")), n.inlines)
    }

    @Test
    fun numberedItemWithParen() {
        val blocks = parseMarkdown("2) Second item")
        assertEquals(1, blocks.size)
        val n = blocks[0] as MarkdownBlock.NumberedItem
        assertEquals(0, n.depth)
        assertEquals(2, n.number)
        assertEquals(listOf(Inline.Text("Second item")), n.inlines)
    }

    @Test
    fun numberedItemPreservesWrittenNumber() {
        val blocks = parseMarkdown("42. Meaning of life")
        assertEquals(1, blocks.size)
        val n = blocks[0] as MarkdownBlock.NumberedItem
        assertEquals(42, n.number)
        assertEquals(listOf(Inline.Text("Meaning of life")), n.inlines)
    }

    @Test
    fun checklistItemUnchecked() {
        val blocks = parseMarkdown("- [ ] Incomplete task")
        assertEquals(1, blocks.size)
        val c = blocks[0] as MarkdownBlock.ChecklistItem
        assertEquals(0, c.depth)
        assertEquals(false, c.checked)
        assertEquals(0, c.sourceLine)
        assertEquals(listOf(Inline.Text("Incomplete task")), c.inlines)
    }

    @Test
    fun checklistItemCheckedLowercase() {
        val blocks = parseMarkdown("- [x] Done task")
        assertEquals(1, blocks.size)
        val c = blocks[0] as MarkdownBlock.ChecklistItem
        assertEquals(true, c.checked)
        assertEquals(listOf(Inline.Text("Done task")), c.inlines)
    }

    @Test
    fun checklistItemCheckedUppercase() {
        val blocks = parseMarkdown("* [X] Done with capital X")
        assertEquals(1, blocks.size)
        val c = blocks[0] as MarkdownBlock.ChecklistItem
        assertEquals(true, c.checked)
        assertEquals(listOf(Inline.Text("Done with capital X")), c.inlines)
    }

    @Test
    fun blockQuoteSingleLine() {
        val blocks = parseMarkdown("> This is a quote")
        assertEquals(1, blocks.size)
        val q = blocks[0] as MarkdownBlock.Quote
        assertEquals(listOf(Inline.Text("This is a quote")), q.inlines)
    }

    @Test
    fun blockQuoteMultiLine() {
        val blocks = parseMarkdown("> Line 1\n> Line 2")
        assertEquals(1, blocks.size)
        val q = blocks[0] as MarkdownBlock.Quote
        assertEquals(listOf(Inline.Text("Line 1\nLine 2")), q.inlines)
    }

    @Test
    fun blockQuoteWithInlineFormatting() {
        val blocks = parseMarkdown("> Quote with **bold** text")
        assertEquals(1, blocks.size)
        val q = blocks[0] as MarkdownBlock.Quote
        assertEquals(
            listOf(
                Inline.Text("Quote with "),
                Inline.Bold(listOf(Inline.Text("bold"))),
                Inline.Text(" text")
            ),
            q.inlines
        )
    }

    @Test
    fun codeBlockWithLanguage() {
        val md = "```kotlin\nval x = 42\nprintln(x)\n```"
        val blocks = parseMarkdown(md)
        assertEquals(1, blocks.size)
        val cb = blocks[0] as MarkdownBlock.CodeBlock
        assertEquals("kotlin", cb.language)
        assertEquals("val x = 42\nprintln(x)", cb.text)
    }

    @Test
    fun codeBlockWithoutLanguage() {
        val md = "```\nplain text code\n```"
        val blocks = parseMarkdown(md)
        assertEquals(1, blocks.size)
        val cb = blocks[0] as MarkdownBlock.CodeBlock
        assertEquals("", cb.language)
        assertEquals("plain text code", cb.text)
    }

    @Test
    fun codeBlockPreservesInternalMarkdownLiterally() {
        val md = "```\n# Not a heading\n- Not a list\n```"
        val blocks = parseMarkdown(md)
        assertEquals(1, blocks.size)
        val cb = blocks[0] as MarkdownBlock.CodeBlock
        assertEquals("# Not a heading\n- Not a list", cb.text)
    }

    @Test
    fun horizontalRuleDash() {
        val blocks = parseMarkdown("---")
        assertEquals(1, blocks.size)
        assertEquals(MarkdownBlock.Rule, blocks[0])
    }

    @Test
    fun horizontalRuleAsterisk() {
        val blocks = parseMarkdown("***")
        assertEquals(1, blocks.size)
        assertEquals(MarkdownBlock.Rule, blocks[0])
    }

    @Test
    fun horizontalRuleUnderscore() {
        val blocks = parseMarkdown("___")
        assertEquals(1, blocks.size)
        assertEquals(MarkdownBlock.Rule, blocks[0])
    }

    @Test
    fun horizontalRuleWithSpaces() {
        val blocks = parseMarkdown("* * *")
        assertEquals(1, blocks.size)
        assertEquals(MarkdownBlock.Rule, blocks[0])
    }

    @Test
    fun imageBlockStandaloneLine() {
        val blocks = parseMarkdown("![Diagram](unitask-image:photo.png)")
        assertEquals(1, blocks.size)
        val img = blocks[0] as MarkdownBlock.Image
        assertEquals("Diagram", img.alt)
        assertEquals("unitask-image:photo.png", img.src)
    }

    // -------------------------------------------------------------------------
    // 2. Nesting depths
    // -------------------------------------------------------------------------

    @Test
    fun listNestingDepths() {
        val md = listOf(
            "- depth zero",
            "  - depth one",
            "    - depth two",
            "      - depth three",
            "        - depth capped at three"
        ).joinToString("\n")

        val blocks = parseMarkdown(md)
        assertEquals(5, blocks.size)
        assertEquals(0, (blocks[0] as MarkdownBlock.BulletItem).depth)
        assertEquals(1, (blocks[1] as MarkdownBlock.BulletItem).depth)
        assertEquals(2, (blocks[2] as MarkdownBlock.BulletItem).depth)
        assertEquals(3, (blocks[3] as MarkdownBlock.BulletItem).depth)
        assertEquals(3, (blocks[4] as MarkdownBlock.BulletItem).depth)
    }

    @Test
    fun listTabIndentCountsAsFourSpaces() {
        val md = "\t- tab indented item"
        val blocks = parseMarkdown(md)
        assertEquals(1, blocks.size)
        val item = blocks[0] as MarkdownBlock.BulletItem
        assertEquals(2, item.depth)
    }

    // -------------------------------------------------------------------------
    // 3. Checklist sourceLine
    // -------------------------------------------------------------------------

    @Test
    fun checklistSourceLinePreservedAccurately() {
        val md = listOf(
            "# Title",
            "",
            "- [ ] Task at line 2",
            "Paragraph at line 3",
            "- [x] Task at line 4"
        ).joinToString("\n")

        val blocks = parseMarkdown(md)
        assertEquals(4, blocks.size)
        val task1 = blocks[1] as MarkdownBlock.ChecklistItem
        val task2 = blocks[3] as MarkdownBlock.ChecklistItem
        assertEquals(2, task1.sourceLine)
        assertEquals(4, task2.sourceLine)
    }

    // -------------------------------------------------------------------------
    // 4. Tables
    // -------------------------------------------------------------------------

    @Test
    fun tableAlignmentsFromColons() {
        val md = listOf(
            "| Left | Center | Right | Default |",
            "| :--- | :---: | ---: | --- |",
            "| L | C | R | D |"
        ).joinToString("\n")

        val blocks = parseMarkdown(md)
        assertEquals(1, blocks.size)
        val tbl = blocks[0] as MarkdownBlock.Table
        assertEquals(
            listOf(TableAlign.LEFT, TableAlign.CENTER, TableAlign.RIGHT, TableAlign.LEFT),
            tbl.alignments
        )
    }

    @Test
    fun tableRowPaddedWhenFewerCellsThanHeader() {
        val md = listOf(
            "| Col 1 | Col 2 | Col 3 |",
            "| --- | --- | --- |",
            "| Only one |"
        ).joinToString("\n")

        val blocks = parseMarkdown(md)
        assertEquals(1, blocks.size)
        val tbl = blocks[0] as MarkdownBlock.Table
        assertEquals(1, tbl.rows.size)
        val row = tbl.rows[0]
        assertEquals(3, row.size)
        assertEquals(listOf(Inline.Text("Only one")), row[0])
        assertEquals(emptyList<Inline>(), row[1])
        assertEquals(emptyList<Inline>(), row[2])
    }

    @Test
    fun tableRowCutWhenMoreCellsThanHeader() {
        val md = listOf(
            "| Col 1 | Col 2 |",
            "| --- | --- |",
            "| A | B | C | D |"
        ).joinToString("\n")

        val blocks = parseMarkdown(md)
        assertEquals(1, blocks.size)
        val tbl = blocks[0] as MarkdownBlock.Table
        assertEquals(1, tbl.rows.size)
        val row = tbl.rows[0]
        assertEquals(2, row.size)
        assertEquals(listOf(Inline.Text("A")), row[0])
        assertEquals(listOf(Inline.Text("B")), row[1])
    }

    @Test
    fun tableEscapedPipeInsideCell() {
        val md = listOf(
            "| Symbol | Meaning |",
            "| --- | --- |",
            "| a \\| b | alternative |"
        ).joinToString("\n")

        val blocks = parseMarkdown(md)
        assertEquals(1, blocks.size)
        val tbl = blocks[0] as MarkdownBlock.Table
        val row = tbl.rows[0]
        assertEquals(listOf(Inline.Text("a | b")), row[0])
        assertEquals(listOf(Inline.Text("alternative")), row[1])
    }

    @Test
    fun lineWithPipesWithoutSeparatorParsedAsParagraph() {
        val md = "This line has a | pipe symbol | in text"
        val blocks = parseMarkdown(md)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
    }

    @Test
    fun lineWithPipesAndInvalidSeparatorParsedAsParagraph() {
        val md = listOf(
            "| Header 1 | Header 2 |",
            "| not dashes | not dashes |"
        ).joinToString("\n")

        val blocks = parseMarkdown(md)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
    }

    // -------------------------------------------------------------------------
    // 5. Inline styles and nesting
    // -------------------------------------------------------------------------

    @Test
    fun boldWithAsterisks() {
        val inlines = parseInlines("**bold text**")
        assertEquals(listOf(Inline.Bold(listOf(Inline.Text("bold text")))), inlines)
    }

    @Test
    fun boldWithUnderscores() {
        val inlines = parseInlines("__bold text__")
        assertEquals(listOf(Inline.Bold(listOf(Inline.Text("bold text")))), inlines)
    }

    @Test
    fun italicWithAsterisk() {
        val inlines = parseInlines("*italic text*")
        assertEquals(listOf(Inline.Italic(listOf(Inline.Text("italic text")))), inlines)
    }

    @Test
    fun italicWithUnderscore() {
        val inlines = parseInlines("_italic text_")
        assertEquals(listOf(Inline.Italic(listOf(Inline.Text("italic text")))), inlines)
    }

    @Test
    fun strikethrough() {
        val inlines = parseInlines("~~struck text~~")
        assertEquals(listOf(Inline.Strike(listOf(Inline.Text("struck text")))), inlines)
    }

    @Test
    fun inlineCode() {
        val inlines = parseInlines("Use `val x = 1` here")
        assertEquals(
            listOf(
                Inline.Text("Use "),
                Inline.Code("val x = 1"),
                Inline.Text(" here")
            ),
            inlines
        )
    }

    @Test
    fun linkSyntax() {
        val inlines = parseInlines("[UniTask](https://unitask.example.com)")
        assertEquals(
            listOf(
                Inline.Link(
                    children = listOf(Inline.Text("UniTask")),
                    url = "https://unitask.example.com"
                )
            ),
            inlines
        )
    }

    @Test
    fun boldContainingItalic() {
        val inlines = parseInlines("**_bold italic_**")
        assertEquals(
            listOf(
                Inline.Bold(
                    listOf(
                        Inline.Italic(listOf(Inline.Text("bold italic")))
                    )
                )
            ),
            inlines
        )
    }

    @Test
    fun tripleAsteriskBoldAndItalic() {
        val inlines = parseInlines("***bold and italic***")
        assertEquals(
            listOf(
                Inline.Bold(
                    listOf(
                        Inline.Italic(listOf(Inline.Text("bold and italic")))
                    )
                )
            ),
            inlines
        )
    }

    @Test
    fun boldContainingLink() {
        val inlines = parseInlines("**[click here](https://test.com)**")
        assertEquals(
            listOf(
                Inline.Bold(
                    listOf(
                        Inline.Link(
                            children = listOf(Inline.Text("click here")),
                            url = "https://test.com"
                        )
                    )
                )
            ),
            inlines
        )
    }

    @Test
    fun nestingCappedAtDepthFour() {
        // depth 0: bold, depth 1: italic, depth 2: strike, depth 3: link, depth 4: inside link italic stays plain
        val md = "**_~~[*plain*](https://test.com)~~_**"
        val inlines = parseInlines(md)
        assertEquals(1, inlines.size)
        val bold0 = inlines[0] as Inline.Bold
        val italic1 = bold0.children[0] as Inline.Italic
        val strike2 = italic1.children[0] as Inline.Strike
        val link3 = strike2.children[0] as Inline.Link
        assertEquals("https://test.com", link3.url)
        assertEquals(listOf(Inline.Text("*plain*")), link3.children)
    }

    @Test
    fun unmatchedMarkersStayPlainText() {
        val inlines = parseInlines("This is *unmatched and **also unmatched")
        assertEquals(listOf(Inline.Text("This is *unmatched and **also unmatched")), inlines)
    }

    // -------------------------------------------------------------------------
    // 6. snake_case not italic
    // -------------------------------------------------------------------------

    @Test
    fun snakeCaseNotParsedAsItalic() {
        val inlines = parseInlines("Look at my_variable_name in code")
        assertEquals(listOf(Inline.Text("Look at my_variable_name in code")), inlines)
    }

    @Test
    fun multipleUnderscoresInsideWordsRemainText() {
        val inlines = parseInlines("first_second_third_fourth")
        assertEquals(listOf(Inline.Text("first_second_third_fourth")), inlines)
    }

    // -------------------------------------------------------------------------
    // 7. Backslash escapes
    // -------------------------------------------------------------------------

    @Test
    fun backslashEscapesSupportedSymbols() {
        val inlines = parseInlines("\\* \\_ \\` \\[ \\] \\( \\) \\# \\\\ \\|")
        assertEquals(listOf(Inline.Text("* _ ` [ ] ( ) # \\ |")), inlines)
    }

    @Test
    fun loneBackslashAtEndHandledSafely() {
        val inlines = parseInlines("trailing backslash \\")
        assertEquals(listOf(Inline.Text("trailing backslash \\")), inlines)
    }

    // -------------------------------------------------------------------------
    // 8. Code fence
    // -------------------------------------------------------------------------

    @Test
    fun unclosedCodeFenceRunsToEndOfInput() {
        val md = "```kotlin\nval a = 1\nval b = 2"
        val blocks = parseMarkdown(md)
        assertEquals(1, blocks.size)
        val cb = blocks[0] as MarkdownBlock.CodeBlock
        assertEquals("kotlin", cb.language)
        assertEquals("val a = 1\nval b = 2", cb.text)
    }

    // -------------------------------------------------------------------------
    // 9. Bare URLs
    // -------------------------------------------------------------------------

    @Test
    fun bareHttpsUrlBecomesLink() {
        val inlines = parseInlines("Visit https://unitask.app today")
        assertEquals(
            listOf(
                Inline.Text("Visit "),
                Inline.Link(listOf(Inline.Text("https://unitask.app")), "https://unitask.app"),
                Inline.Text(" today")
            ),
            inlines
        )
    }

    @Test
    fun bareUrlStripsTrailingPunctuation() {
        val inlines = parseInlines("See http://example.com/page.")
        assertEquals(
            listOf(
                Inline.Text("See "),
                Inline.Link(listOf(Inline.Text("http://example.com/page")), "http://example.com/page"),
                Inline.Text(".")
            ),
            inlines
        )
    }

    // -------------------------------------------------------------------------
    // 10. Image block vs inline image
    // -------------------------------------------------------------------------

    @Test
    fun standaloneImageBlockParsedAsImage() {
        val blocks = parseMarkdown("![Cover](unitask-image:cover.png)")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Image)
        val img = blocks[0] as MarkdownBlock.Image
        assertEquals("Cover", img.alt)
        assertEquals("unitask-image:cover.png", img.src)
    }

    @Test
    fun inlineImageInsideParagraphBecomesLinkWithAltText() {
        val blocks = parseMarkdown("Inline ![cat](https://example.com/cat.png) photo here")
        assertEquals(1, blocks.size)
        val p = blocks[0] as MarkdownBlock.Paragraph
        assertEquals(
            listOf(
                Inline.Text("Inline "),
                Inline.Link(listOf(Inline.Text("cat")), "https://example.com/cat.png"),
                Inline.Text(" photo here")
            ),
            p.inlines
        )
    }

    @Test
    fun inlineImageWithEmptyAltUsesWordImage() {
        val blocks = parseMarkdown("Here is ![](https://example.com/empty.png) photo")
        assertEquals(1, blocks.size)
        val p = blocks[0] as MarkdownBlock.Paragraph
        assertEquals(
            listOf(
                Inline.Text("Here is "),
                Inline.Link(listOf(Inline.Text("image")), "https://example.com/empty.png"),
                Inline.Text(" photo")
            ),
            p.inlines
        )
    }

    @Test
    fun multipleImagesOnSingleLineStayParagraph() {
        val blocks = parseMarkdown("![img1](url1) and ![img2](url2)")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
    }

    // -------------------------------------------------------------------------
    // 11. referencedImageNames
    // -------------------------------------------------------------------------

    @Test
    fun referencedImageNamesFindsBlockAndInlineImages() {
        val md = listOf(
            "![Hero](unitask-image:hero.png)",
            "Some text with inline ![diagram](unitask-image:chart.jpg) and web ![icon](https://web.com/icon.png)",
            "Duplicate reference: ![hero again](unitask-image:hero.png)"
        ).joinToString("\n")

        val names = referencedImageNames(md)
        assertEquals(setOf("hero.png", "chart.jpg"), names)
    }

    @Test
    fun referencedImageNamesReturnsEmptyForNoUnitaskImages() {
        val md = "![web](https://site.org/pic.png) and plain [link](unitask-image:not-an-image)"
        val names = referencedImageNames(md)
        assertTrue(names.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 12. Robustness and performance cases
    // -------------------------------------------------------------------------

    @Test
    fun emptyStringYieldsEmptyList() {
        assertEquals(emptyList<MarkdownBlock>(), parseMarkdown(""))
        assertEquals(emptyList<Inline>(), parseInlines(""))
        assertTrue(referencedImageNames("").isEmpty())
    }

    @Test
    fun whitespaceOnlyYieldsEmptyList() {
        assertEquals(emptyList<MarkdownBlock>(), parseMarkdown("   \n\t  \n  "))
    }

    @Test
    fun loneSpecialCharactersDoNotThrow() {
        parseMarkdown("|")
        parseMarkdown("[")
        parseMarkdown("![")
        parseMarkdown("```")
        parseMarkdown("`")
        parseMarkdown("*")
        parseMarkdown("**")
        parseMarkdown("~~")
        parseMarkdown("(")
        parseMarkdown(")")
    }

    @Test
    fun deeplyNestedSquareBracketsFinishQuickly() {
        val text = "[".repeat(500) + "hello" + "]".repeat(500)
        val inlines = parseInlines(text)
        assertTrue(inlines.isNotEmpty())
    }

    @Test
    fun hundredThousandAsterisksFinishesQuickly() {
        val source = "*".repeat(100_000)
        val start = System.currentTimeMillis()
        val blocks = parseMarkdown(source)
        val duration = System.currentTimeMillis() - start
        assertTrue(blocks.isNotEmpty())
        assertTrue("Took $duration ms, expected under 1000ms", duration < 1000)
    }

    @Test
    fun hundredThousandOpeningBracketsFinishesQuickly() {
        val source = "[".repeat(100_000)
        val start = System.currentTimeMillis()
        val inlines = parseInlines(source)
        val duration = System.currentTimeMillis() - start
        assertTrue(inlines.isNotEmpty())
        assertTrue("Took $duration ms, expected under 1000ms", duration < 1000)
    }
}

