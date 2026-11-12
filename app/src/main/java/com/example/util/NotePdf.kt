package com.example.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import com.example.util.markdown.CodeHighlighter
import com.example.util.markdown.CodeTokenType
import com.example.util.markdown.Inline
import com.example.util.markdown.MarkdownBlock
import com.example.util.markdown.TableAlign
import com.example.util.markdown.parseMarkdown
import java.io.OutputStream

/**
 * Builds a multi-page PDF document that matches the note Markdown preview structure.
 * Safe to call from Dispatchers.IO.
 */
object NotePdf {

    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    const val MARGIN = 48
    const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2 // 499 points
    const val CONTENT_HEIGHT = PAGE_HEIGHT - MARGIN * 2 // 746 points
    const val TOP_MARGIN = MARGIN.toFloat() // 48f
    const val BOTTOM_MARGIN = (PAGE_HEIGHT - MARGIN).toFloat() // 794f
    const val LEFT_MARGIN = MARGIN.toFloat() // 48f
    const val RIGHT_MARGIN = (PAGE_WIDTH - MARGIN).toFloat() // 547f
    const val MAX_PAGES = 300

    // Pure layout helpers separated for easy unit testing without Android
    fun headingSize(level: Int): Float = when (level) {
        1 -> 20f
        2 -> 16f
        3 -> 13f
        else -> 11f
    }

    fun headingSpacingAbove(level: Int): Float = when (level) {
        1 -> 18f
        2 -> 14f
        3 -> 10f
        else -> 8f
    }

    fun listIndent(depth: Int): Float = maxOf(0, depth) * 16f

    fun tableColumnWidths(colCount: Int, totalWidth: Float = CONTENT_WIDTH.toFloat()): List<Float> {
        if (colCount <= 0) return emptyList()
        val width = totalWidth / colCount
        return List(colCount) { width }
    }

    fun blockFits(blockHeight: Float, currentY: Float, maxY: Float = BOTTOM_MARGIN): Boolean =
        (currentY + blockHeight) <= maxY

    fun headingNeedsNewPage(currentY: Float, maxY: Float = BOTTOM_MARGIN, threshold: Float = 60f): Boolean =
        (maxY - currentY) < threshold

    /**
     * Converts a list of markdown inlines into a SpannableStringBuilder with formatting spans.
     */
    fun inlinesToSpannable(inlines: List<Inline>): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        appendInlines(builder, inlines)
        return builder
    }

    private fun appendInlines(builder: SpannableStringBuilder, inlines: List<Inline>) {
        for (inline in inlines) {
            when (inline) {
                is Inline.Text -> builder.append(inline.text)
                is Inline.Bold -> {
                    val start = builder.length
                    appendInlines(builder, inline.children)
                    val end = builder.length
                    if (end > start) {
                        builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                is Inline.Italic -> {
                    val start = builder.length
                    appendInlines(builder, inline.children)
                    val end = builder.length
                    if (end > start) {
                        builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                is Inline.Strike -> {
                    val start = builder.length
                    appendInlines(builder, inline.children)
                    val end = builder.length
                    if (end > start) {
                        builder.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                is Inline.Code -> {
                    val start = builder.length
                    builder.append(inline.text)
                    val end = builder.length
                    if (end > start) {
                        builder.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        builder.setSpan(BackgroundColorSpan(0xFFEEEEEE.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                is Inline.Link -> {
                    val start = builder.length
                    appendInlines(builder, inline.children)
                    val end = builder.length
                    if (end > start) {
                        builder.setSpan(ForegroundColorSpan(0xFF1976D2.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        builder.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }
    }

    /**
     * Creates a StaticLayout with safe width and 1.25 line spacing.
     */
    fun createStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        lineSpacingMultiplier: Float = 1.25f
    ): StaticLayout {
        val safeWidth = maxOf(1, width)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(align)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setIncludePad(false)
            .build()
    }

    /** Colours for code on the PDF's light code background. */
    private fun codeTokenColor(type: CodeTokenType): Int = when (type) {
        CodeTokenType.KEYWORD -> 0xFF8E24AA.toInt()
        CodeTokenType.STRING -> 0xFF2E7D32.toInt()
        CodeTokenType.COMMENT -> 0xFF6B7280.toInt()
        CodeTokenType.NUMBER -> 0xFFB45309.toInt()
        CodeTokenType.TYPE -> 0xFF9A6700.toInt()
        CodeTokenType.FUNCTION -> 0xFF1565C0.toInt()
    }

    /** The text of a fenced code block with syntax colours; no language (or an unknown one) stays plain. */
    fun highlightedCode(code: String, language: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder(code)
        for (token in CodeHighlighter.tokenize(code, language)) {
            builder.setSpan(
                ForegroundColorSpan(codeTokenColor(token.type)),
                token.start, token.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (token.type == CodeTokenType.COMMENT) {
                builder.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    token.start, token.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return builder
    }

    /**
     * Writes the given note as a PDF document into the output stream.
     * Always closes the PdfDocument when finished.
     */
    fun write(context: Context, title: String, body: String, out: OutputStream) {
        val pdfDocument = PdfDocument()
        try {
            val renderer = Renderer(context, pdfDocument)
            renderer.render(title, body)
            pdfDocument.writeTo(out)
        } finally {
            pdfDocument.close()
        }
    }

    private class Renderer(
        private val context: Context,
        private val pdfDocument: PdfDocument
    ) {
        private var pageNumber = 1
        private var currentPage: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y: Float = TOP_MARGIN
        private var stopped = false

        // Paints for rendering elements
        private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1A1A.toInt()
            textSize = 11f
            typeface = Typeface.DEFAULT
        }

        private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1A1A.toInt()
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }

        private val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF555555.toInt()
            textSize = 11f
            typeface = Typeface.DEFAULT
        }

        private val quoteBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF888888.toInt()
            style = Paint.Style.FILL
        }

        private val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1A1A.toInt()
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
        }

        private val codeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF1F1F1.toInt()
            style = Paint.Style.FILL
        }

        private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCCCCCC.toInt()
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        private val tableGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCCCCCC.toInt()
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        private val tableHeaderBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFEEEEEE.toInt()
            style = Paint.Style.FILL
        }

        private val tableHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1A1A.toInt()
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
        }

        private val boxStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1A1A.toInt()
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        private val checkTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1A1A.toInt()
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1A1A.toInt()
            style = Paint.Style.FILL
        }

        private val imageFallbackPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF777777.toInt()
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }

        private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF888888.toInt()
            textSize = 9f
            textAlign = Paint.Align.CENTER
        }

        private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCCCCCC.toInt()
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        private fun headingPaint(level: Int): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1A1A.toInt()
            textSize = headingSize(level)
            typeface = Typeface.DEFAULT_BOLD
        }

        private fun ensurePage(): Canvas {
            val existing = canvas
            if (currentPage != null && existing != null) return existing
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            currentPage = page
            val pageCanvas = page.canvas
            canvas = pageCanvas
            y = TOP_MARGIN
            return pageCanvas
        }

        private fun finishCurrentPage() {
            val page = currentPage ?: return
            val cv = canvas ?: return
            // Draw "Page N" centered in the bottom margin
            cv.drawText("Page $pageNumber", PAGE_WIDTH / 2f, PAGE_HEIGHT - 24f, footerPaint)
            pdfDocument.finishPage(page)
            currentPage = null
            canvas = null
            pageNumber++
        }

        private fun newPage(): Boolean {
            if (pageNumber >= MAX_PAGES) {
                if (!stopped) {
                    val cv = ensurePage()
                    val cutoffText = "The rest of this note was cut off to keep the PDF small."
                    val cutoffSpannable = SpannableStringBuilder(cutoffText).apply {
                        setSpan(StyleSpan(Typeface.ITALIC), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(ForegroundColorSpan(0xFF777777.toInt()), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    val cutoffLayout = createStaticLayout(cutoffSpannable, imageFallbackPaint, CONTENT_WIDTH)
                    val drawY = minOf(y, BOTTOM_MARGIN - cutoffLayout.height.toFloat())
                    cv.save()
                    cv.translate(LEFT_MARGIN, drawY)
                    cutoffLayout.draw(cv)
                    cv.restore()
                    stopped = true
                }
                finishCurrentPage()
                return false
            }
            finishCurrentPage()
            ensurePage()
            return true
        }

        fun render(title: String, body: String) {
            ensurePage()

            if (title.isNotBlank()) {
                val titleLayout = createStaticLayout(title, titlePaint, CONTENT_WIDTH)
                drawLayoutWithSlicing(titleLayout, LEFT_MARGIN, CONTENT_WIDTH.toFloat())
                if (!stopped) {
                    y += 8f
                    val cv = ensurePage()
                    cv.drawLine(LEFT_MARGIN, y, RIGHT_MARGIN, y, separatorPaint)
                    y += 12f
                }
            }

            if (body.isNotEmpty() && !stopped) {
                val blocks = parseMarkdown(body)
                for (block in blocks) {
                    if (stopped) break
                    renderBlock(block)
                }
            }

            finishCurrentPage()
        }

        private fun renderBlock(block: MarkdownBlock) {
            when (block) {
                is MarkdownBlock.Heading -> renderHeading(block)
                is MarkdownBlock.Paragraph -> renderParagraph(block)
                is MarkdownBlock.BulletItem -> renderBulletItem(block)
                is MarkdownBlock.NumberedItem -> renderNumberedItem(block)
                is MarkdownBlock.ChecklistItem -> renderChecklistItem(block)
                is MarkdownBlock.Quote -> renderQuote(block)
                is MarkdownBlock.CodeBlock -> renderCodeBlock(block)
                is MarkdownBlock.Rule -> renderRule()
                is MarkdownBlock.Table -> renderTable(block)
                is MarkdownBlock.Image -> renderImage(block)
            }
        }

        private fun renderHeading(block: MarkdownBlock.Heading) {
            val text = inlinesToSpannable(block.inlines)
            val paint = headingPaint(block.level)
            val layout = createStaticLayout(text, paint, CONTENT_WIDTH)
            val spaceAbove = if (y > TOP_MARGIN) headingSpacingAbove(block.level) else 0f

            if (headingNeedsNewPage(y + spaceAbove) || !blockFits(layout.height.toFloat() + spaceAbove, y)) {
                if (!newPage()) return
            } else {
                y += spaceAbove
            }

            drawLayoutWithSlicing(layout, LEFT_MARGIN, CONTENT_WIDTH.toFloat())
            y += 6f
        }

        private fun renderParagraph(block: MarkdownBlock.Paragraph) {
            val text = inlinesToSpannable(block.inlines)
            if (text.isEmpty()) return
            val layout = createStaticLayout(text, bodyPaint, CONTENT_WIDTH)
            drawLayoutWithSlicing(layout, LEFT_MARGIN, CONTENT_WIDTH.toFloat())
            y += 6f
        }

        private fun renderBulletItem(block: MarkdownBlock.BulletItem) {
            val indent = listIndent(block.depth)
            val startX = LEFT_MARGIN + indent
            val textX = startX + 14f
            val textWidth = maxOf(1, (CONTENT_WIDTH - indent - 14f).toInt())

            val text = inlinesToSpannable(block.inlines)
            val layout = createStaticLayout(text, bodyPaint, textWidth)

            if (!blockFits(layout.height.toFloat(), y) && layout.height <= CONTENT_HEIGHT) {
                if (!newPage()) return
            }

            val cv = ensurePage()
            cv.drawCircle(startX + 4f, y + 7f, 2.2f, dotPaint)
            drawLayoutWithSlicing(layout, textX, textWidth.toFloat())
            y += 3f
        }

        private fun renderNumberedItem(block: MarkdownBlock.NumberedItem) {
            val indent = listIndent(block.depth)
            val startX = LEFT_MARGIN + indent
            val numText = "${block.number}."
            val numWidth = maxOf(16f, bodyPaint.measureText(numText) + 4f)
            val textX = startX + numWidth
            val textWidth = maxOf(1, (CONTENT_WIDTH - indent - numWidth).toInt())

            val text = inlinesToSpannable(block.inlines)
            val layout = createStaticLayout(text, bodyPaint, textWidth)

            if (!blockFits(layout.height.toFloat(), y) && layout.height <= CONTENT_HEIGHT) {
                if (!newPage()) return
            }

            val cv = ensurePage()
            cv.drawText(numText, startX, y + 11f, bodyPaint)
            drawLayoutWithSlicing(layout, textX, textWidth.toFloat())
            y += 3f
        }

        private fun renderChecklistItem(block: MarkdownBlock.ChecklistItem) {
            val indent = listIndent(block.depth)
            val startX = LEFT_MARGIN + indent
            val textX = startX + 16f
            val textWidth = maxOf(1, (CONTENT_WIDTH - indent - 16f).toInt())

            val text = inlinesToSpannable(block.inlines)
            val layout = createStaticLayout(text, bodyPaint, textWidth)

            if (!blockFits(layout.height.toFloat(), y) && layout.height <= CONTENT_HEIGHT) {
                if (!newPage()) return
            }

            val cv = ensurePage()
            val boxRect = RectF(startX, y + 2f, startX + 10f, y + 12f)
            cv.drawRect(boxRect, boxStrokePaint)

            if (block.checked) {
                cv.drawLine(startX + 2f, y + 7f, startX + 4.5f, y + 10f, checkTickPaint)
                cv.drawLine(startX + 4.5f, y + 10f, startX + 8.5f, y + 3.5f, checkTickPaint)
            }

            drawLayoutWithSlicing(layout, textX, textWidth.toFloat())
            y += 3f
        }

        private fun renderQuote(block: MarkdownBlock.Quote) {
            val text = inlinesToSpannable(block.inlines)
            val textX = LEFT_MARGIN + 12f
            val textWidth = maxOf(1, (CONTENT_WIDTH - 12f).toInt())
            val layout = createStaticLayout(text, quotePaint, textWidth)

            var startLine = 0
            val totalLines = layout.lineCount

            while (startLine < totalLines && !stopped) {
                val cv = ensurePage()
                val availableHeight = BOTTOM_MARGIN - y
                val lineTop0 = layout.getLineTop(startLine)
                val singleLineHeight = layout.getLineBottom(startLine) - lineTop0

                if (availableHeight < singleLineHeight) {
                    if (!newPage()) return
                    continue
                }

                var endLine = startLine + 1
                while (endLine < totalLines) {
                    val h = layout.getLineBottom(endLine) - lineTop0
                    if (h > availableHeight) break
                    endLine++
                }

                val topOffset = layout.getLineTop(startLine).toFloat()
                val bottomOffset = layout.getLineBottom(endLine - 1).toFloat()
                val sliceHeight = bottomOffset - topOffset

                cv.drawRect(LEFT_MARGIN, y, LEFT_MARGIN + 3f, y + sliceHeight, quoteBarPaint)

                cv.save()
                cv.clipRect(textX, y, textX + textWidth, y + sliceHeight)
                cv.translate(textX, y - topOffset)
                layout.draw(cv)
                cv.restore()

                y += sliceHeight
                startLine = endLine
                if (startLine < totalLines) {
                    if (!newPage()) return
                }
            }
            y += 6f
        }

        private fun renderCodeBlock(block: MarkdownBlock.CodeBlock) {
            val textX = LEFT_MARGIN + 8f
            val textWidth = maxOf(1, (CONTENT_WIDTH - 16f).toInt())
            val layout = createStaticLayout(
                highlightedCode(block.text, block.language),
                codePaint,
                textWidth,
                lineSpacingMultiplier = 1.2f
            )

            var startLine = 0
            val totalLines = layout.lineCount

            while (startLine < totalLines && !stopped) {
                val cv = ensurePage()
                val availableHeight = BOTTOM_MARGIN - y
                val lineTop0 = layout.getLineTop(startLine)
                val singleLineHeight = (layout.getLineBottom(startLine) - lineTop0) + 16f

                if (availableHeight < singleLineHeight) {
                    if (!newPage()) return
                    continue
                }

                var endLine = startLine + 1
                while (endLine < totalLines) {
                    val h = (layout.getLineBottom(endLine) - lineTop0) + 16f
                    if (h > availableHeight) break
                    endLine++
                }

                val topOffset = layout.getLineTop(startLine).toFloat()
                val bottomOffset = layout.getLineBottom(endLine - 1).toFloat()
                val sliceHeight = bottomOffset - topOffset
                val boxHeight = sliceHeight + 16f

                val bgRect = RectF(LEFT_MARGIN, y, RIGHT_MARGIN, y + boxHeight)
                cv.drawRoundRect(bgRect, 4f, 4f, codeBgPaint)

                cv.save()
                cv.clipRect(textX, y + 8f, textX + textWidth, y + 8f + sliceHeight)
                cv.translate(textX, y + 8f - topOffset)
                layout.draw(cv)
                cv.restore()

                y += boxHeight
                startLine = endLine
                if (startLine < totalLines) {
                    if (!newPage()) return
                }
            }
            y += 8f
        }

        private fun renderRule() {
            if (!blockFits(16f, y)) {
                if (!newPage()) return
            }
            y += 6f
            val cv = ensurePage()
            cv.drawLine(LEFT_MARGIN, y, RIGHT_MARGIN, y, rulePaint)
            y += 10f
        }

        private fun renderTable(block: MarkdownBlock.Table) {
            val colCount = block.header.size
            if (colCount == 0) return

            val colWidths = tableColumnWidths(colCount, CONTENT_WIDTH.toFloat())

            renderTableRow(block.header, block.alignments, colWidths, isHeader = true)

            for (row in block.rows) {
                if (stopped) break
                renderTableRow(row, block.alignments, colWidths, isHeader = false)
            }
            y += 8f
        }

        private fun renderTableRow(
            cells: List<List<Inline>>,
            alignments: List<TableAlign>,
            colWidths: List<Float>,
            isHeader: Boolean
        ) {
            val cellLayouts = cells.mapIndexed { colIdx, inlines ->
                val align = alignments.getOrElse(colIdx) { TableAlign.LEFT }
                val layoutAlign = when (align) {
                    TableAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
                    TableAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
                    TableAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                }
                val paint = if (isHeader) tableHeaderPaint else bodyPaint
                val text = inlinesToSpannable(inlines)
                val cellW = maxOf(1, (colWidths.getOrElse(colIdx) { colWidths[0] } - 8f).toInt())
                createStaticLayout(text, paint, cellW, layoutAlign)
            }

            val maxCellHeight = cellLayouts.maxOfOrNull { it.height.toFloat() } ?: 0f
            val rowHeight = maxCellHeight + 8f // 4 pt padding top and bottom

            if (!blockFits(rowHeight, y) && rowHeight <= CONTENT_HEIGHT) {
                if (!newPage()) return
            }

            val cv = ensurePage()

            if (isHeader) {
                val bgRect = RectF(LEFT_MARGIN, y, RIGHT_MARGIN, y + rowHeight)
                cv.drawRect(bgRect, tableHeaderBgPaint)
            }

            var currentX = LEFT_MARGIN
            for (colIdx in cells.indices) {
                val colW = colWidths.getOrElse(colIdx) { colWidths[0] }
                val layout = cellLayouts.getOrNull(colIdx)
                if (layout != null) {
                    cv.save()
                    cv.clipRect(currentX + 4f, y + 4f, currentX + colW - 4f, y + rowHeight - 4f)
                    cv.translate(currentX + 4f, y + 4f)
                    layout.draw(cv)
                    cv.restore()
                }
                currentX += colW
            }

            // Outer horizontal borders
            cv.drawLine(LEFT_MARGIN, y, RIGHT_MARGIN, y, tableGridPaint)
            cv.drawLine(LEFT_MARGIN, y + rowHeight, RIGHT_MARGIN, y + rowHeight, tableGridPaint)

            // Vertical borders
            var vertX = LEFT_MARGIN
            cv.drawLine(vertX, y, vertX, y + rowHeight, tableGridPaint)
            for (colW in colWidths) {
                vertX += colW
                cv.drawLine(vertX, y, vertX, y + rowHeight, tableGridPaint)
            }

            y += rowHeight
        }

        private fun renderImage(block: MarkdownBlock.Image) {
            var drawn = false
            try {
                if (block.src.startsWith(NoteImages.PREFIX)) {
                    val name = block.src.substring(NoteImages.PREFIX.length).trim()
                    if (NoteImages.isValidImageName(name)) {
                        val file = NoteImages.imageFile(context, name)
                        if (file != null && file.exists()) {
                            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
                            val outW = boundsOpts.outWidth
                            val outH = boundsOpts.outHeight
                            if (outW > 0 && outH > 0) {
                                val sampleSize = NoteImages.computeSampleSize(outW, outH, NoteImages.MAX_EDGE_PX)
                                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                                val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                                if (bitmap != null) {
                                    try {
                                        val maxWidth = CONTENT_WIDTH.toFloat()
                                        val maxHeight = PAGE_HEIGHT * 0.60f
                                        val scale = minOf(
                                            maxWidth / bitmap.width.toFloat(),
                                            maxHeight / bitmap.height.toFloat(),
                                            1f
                                        )
                                        val drawW = bitmap.width.toFloat() * scale
                                        val drawH = bitmap.height.toFloat() * scale

                                        if (!blockFits(drawH, y) && drawH <= CONTENT_HEIGHT) {
                                            if (!newPage()) return
                                        }

                                        val cv = ensurePage()
                                        val drawX = LEFT_MARGIN + (CONTENT_WIDTH - drawW) / 2f
                                        val dstRect = RectF(drawX, y, drawX + drawW, y + drawH)
                                        cv.drawBitmap(bitmap, null, dstRect, null)
                                        y += drawH + 8f
                                        drawn = true
                                    } finally {
                                        bitmap.recycle()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                drawn = false
            }

            if (!drawn) {
                val fallbackText = if (block.alt.isNotBlank()) "Image: ${block.alt}" else "Image"
                val fallbackSpannable = SpannableStringBuilder(fallbackText).apply {
                    setSpan(StyleSpan(Typeface.ITALIC), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(ForegroundColorSpan(0xFF777777.toInt()), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val layout = createStaticLayout(fallbackSpannable, imageFallbackPaint, CONTENT_WIDTH)
                if (!blockFits(layout.height.toFloat(), y) && layout.height <= CONTENT_HEIGHT) {
                    if (!newPage()) return
                }
                drawLayoutWithSlicing(layout, LEFT_MARGIN, CONTENT_WIDTH.toFloat())
                y += 6f
            }
        }

        private fun drawLayoutWithSlicing(
            layout: StaticLayout,
            x: Float,
            width: Float
        ) {
            var startLine = 0
            val totalLines = layout.lineCount

            while (startLine < totalLines && !stopped) {
                val cv = ensurePage()
                val availableHeight = BOTTOM_MARGIN - y
                val lineTop0 = layout.getLineTop(startLine)
                val singleLineHeight = layout.getLineBottom(startLine) - lineTop0

                if (availableHeight < singleLineHeight) {
                    if (!newPage()) return
                    continue
                }

                var endLine = startLine + 1
                while (endLine < totalLines) {
                    val h = layout.getLineBottom(endLine) - lineTop0
                    if (h > availableHeight) break
                    endLine++
                }

                val topOffset = layout.getLineTop(startLine).toFloat()
                val bottomOffset = layout.getLineBottom(endLine - 1).toFloat()
                val sliceHeight = bottomOffset - topOffset

                cv.save()
                cv.clipRect(x, y, x + width, y + sliceHeight)
                cv.translate(x, y - topOffset)
                layout.draw(cv)
                cv.restore()

                y += sliceHeight
                startLine = endLine
                if (startLine < totalLines) {
                    if (!newPage()) return
                }
            }
        }
    }
}
