package com.example.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.util.markdown.Inline
import com.example.util.markdown.MarkdownBlock
import com.example.util.markdown.TableAlign
import com.example.util.markdown.isSafeLinkUrl
import com.example.util.markdown.parseMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders parsed markdown text inside a scrollable LazyColumn wrapped in SelectionContainer.
 * Heavy documents (> 20,000 characters) are parsed on a background thread to prevent UI stutter.
 */
@Composable
fun MarkdownPreview(
    markdown: String,
    onToggleChecklist: (lineIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    imageContent: (@Composable (src: String, alt: String) -> Unit)? = null
) {
    val context = LocalContext.current

    // Background thread parsing for large text, immediate parse for short text
    var parsedBlocks by remember {
        mutableStateOf(if (markdown.length <= 20_000) parseMarkdown(markdown) else emptyList())
    }

    LaunchedEffect(markdown) {
        if (markdown.length > 20_000) {
            val result = withContext(Dispatchers.Default) {
                parseMarkdown(markdown)
            }
            parsedBlocks = result
        } else {
            parsedBlocks = parseMarkdown(markdown)
        }
    }

    SelectionContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(parsedBlocks.size) { index ->
                val block = parsedBlocks[index]
                RenderMarkdownBlock(
                    block = block,
                    onToggleChecklist = onToggleChecklist,
                    imageContent = imageContent,
                    context = context
                )
            }
        }
    }
}

@Composable
private fun RenderMarkdownBlock(
    block: MarkdownBlock,
    onToggleChecklist: (lineIndex: Int) -> Unit,
    imageContent: (@Composable (src: String, alt: String) -> Unit)?,
    context: Context
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)

    when (block) {
        is MarkdownBlock.Heading -> {
            val headingStyle = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                3 -> MaterialTheme.typography.titleSmall
                else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            }.copy(color = MaterialTheme.colorScheme.onSurface)

            RenderInlineText(
                inlines = block.inlines,
                baseStyle = headingStyle,
                primaryColor = primaryColor,
                codeBgColor = codeBgColor,
                context = context,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp)
            )
        }

        is MarkdownBlock.Paragraph -> {
            RenderInlineText(
                inlines = block.inlines,
                baseStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                primaryColor = primaryColor,
                codeBgColor = codeBgColor,
                context = context,
                modifier = Modifier.fillMaxWidth()
            )
        }

        is MarkdownBlock.BulletItem -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (block.depth * 16).dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "\u2022",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 8.dp)
                )
                RenderInlineText(
                    inlines = block.inlines,
                    baseStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    primaryColor = primaryColor,
                    codeBgColor = codeBgColor,
                    context = context,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }

        is MarkdownBlock.NumberedItem -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (block.depth * 16).dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "${block.number}.",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 8.dp)
                )
                RenderInlineText(
                    inlines = block.inlines,
                    baseStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    primaryColor = primaryColor,
                    codeBgColor = codeBgColor,
                    context = context,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }

        is MarkdownBlock.ChecklistItem -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (block.depth * 16).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = block.checked,
                    onCheckedChange = { onToggleChecklist(block.sourceLine) },
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val annotated = remember(block.inlines, primaryColor, codeBgColor, context) {
                    buildAnnotatedInlines(block.inlines, primaryColor, codeBgColor) { url ->
                        openLinkSafely(context, url)
                    }
                }
                val checkStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (block.checked) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None
                )
                // Links open themselves; a tap anywhere else on the text ticks the item
                Text(
                    text = annotated,
                    style = checkStyle,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { onToggleChecklist(block.sourceLine) }
                )
            }
        }

        is MarkdownBlock.Quote -> {
            val barColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val strokeWidth = 4.dp.toPx()
                        drawLine(
                            color = barColor,
                            start = Offset(strokeWidth / 2, 0f),
                            end = Offset(strokeWidth / 2, size.height),
                            strokeWidth = strokeWidth
                        )
                    }
                    .padding(start = 14.dp, top = 2.dp, bottom = 2.dp)
            ) {
                RenderInlineText(
                    inlines = block.inlines,
                    baseStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    ),
                    primaryColor = primaryColor,
                    codeBgColor = codeBgColor,
                    context = context,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        is MarkdownBlock.CodeBlock -> {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = block.text,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is MarkdownBlock.Rule -> {
            // outlineVariant is almost the same colour as the background in dark mode,
            // so the line used to disappear. outline is visible in both themes.
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                thickness = 1.5.dp
            )
        }

        is MarkdownBlock.Image -> {
            if (imageContent != null) {
                imageContent(block.src, block.alt)
            } else {
                MarkdownImageFallback(src = block.src, alt = block.alt)
            }
        }

        is MarkdownBlock.Table -> {
            RenderTableBlock(
                block = block,
                primaryColor = primaryColor,
                codeBgColor = codeBgColor,
                context = context
            )
        }
    }
}

@Composable
private fun RenderTableBlock(
    block: MarkdownBlock.Table,
    primaryColor: Color,
    codeBgColor: Color,
    context: Context
) {
    val colCount = block.header.size
    if (colCount == 0) return

    val colWidths = remember(block) {
        (0 until colCount).map { c ->
            val headerLen = inlinesPlainLength(block.header.getOrElse(c) { emptyList() })
            val maxRowLen = block.rows.fold(0) { max, row ->
                maxOf(max, inlinesPlainLength(row.getOrElse(c) { emptyList() }))
            }
            val maxLen = maxOf(headerLen, maxRowLen)
            (maxLen * 8).coerceIn(72, 220).dp
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        Column {
            // Header row
            Row(
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                block.header.forEachIndexed { colIdx, cellInlines ->
                    val colWidth = colWidths[colIdx]
                    val align = block.alignments.getOrElse(colIdx) { TableAlign.LEFT }
                    val textAlign = when (align) {
                        TableAlign.LEFT -> TextAlign.Start
                        TableAlign.CENTER -> TextAlign.Center
                        TableAlign.RIGHT -> TextAlign.End
                    }
                    Box(
                        modifier = Modifier
                            .width(colWidth)
                            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            .padding(8.dp)
                    ) {
                        RenderInlineText(
                            inlines = cellInlines,
                            baseStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = textAlign
                            ),
                            primaryColor = primaryColor,
                            codeBgColor = codeBgColor,
                            context = context,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Data rows
            block.rows.forEach { rowCells ->
                Row {
                    for (colIdx in 0 until colCount) {
                        val colWidth = colWidths[colIdx]
                        val cellInlines = rowCells.getOrElse(colIdx) { emptyList() }
                        val align = block.alignments.getOrElse(colIdx) { TableAlign.LEFT }
                        val textAlign = when (align) {
                            TableAlign.LEFT -> TextAlign.Start
                            TableAlign.CENTER -> TextAlign.Center
                            TableAlign.RIGHT -> TextAlign.End
                        }
                        Box(
                            modifier = Modifier
                                .width(colWidth)
                                .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            .padding(8.dp)
                        ) {
                            RenderInlineText(
                                inlines = cellInlines,
                                baseStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = textAlign
                                ),
                                primaryColor = primaryColor,
                                codeBgColor = codeBgColor,
                                context = context,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderInlineText(
    inlines: List<Inline>,
    baseStyle: TextStyle,
    primaryColor: Color,
    codeBgColor: Color,
    context: Context,
    modifier: Modifier = Modifier
) {
    val annotated = remember(inlines, primaryColor, codeBgColor, context) {
        buildAnnotatedInlines(inlines, primaryColor, codeBgColor) { url ->
            openLinkSafely(context, url)
        }
    }

    Text(
        text = annotated,
        style = baseStyle,
        modifier = modifier
    )
}

private fun buildAnnotatedInlines(
    inlines: List<Inline>,
    primaryColor: Color,
    codeBgColor: Color,
    onLinkClick: (String) -> Unit
): AnnotatedString = buildAnnotatedString {
    appendInlines(inlines, primaryColor, codeBgColor, onLinkClick)
}

private fun AnnotatedString.Builder.appendInlines(
    inlines: List<Inline>,
    primaryColor: Color,
    codeBgColor: Color,
    onLinkClick: (String) -> Unit
) {
    for (inline in inlines) {
        when (inline) {
            is Inline.Text -> append(inline.text)
            is Inline.Bold -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                appendInlines(inline.children, primaryColor, codeBgColor, onLinkClick)
                pop()
            }
            is Inline.Italic -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                appendInlines(inline.children, primaryColor, codeBgColor, onLinkClick)
                pop()
            }
            is Inline.Strike -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                appendInlines(inline.children, primaryColor, codeBgColor, onLinkClick)
                pop()
            }
            is Inline.Code -> {
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBgColor
                    )
                )
                append(inline.text)
                pop()
            }
            is Inline.Link -> {
                // A real link, so TalkBack can find it; the safe-URL check stays in openLinkSafely
                pushLink(
                    LinkAnnotation.Clickable(
                        tag = inline.url,
                        linkInteractionListener = { onLinkClick(inline.url) }
                    )
                )
                pushStyle(
                    SpanStyle(
                        color = primaryColor,
                        textDecoration = TextDecoration.Underline
                    )
                )
                appendInlines(inline.children, primaryColor, codeBgColor, onLinkClick)
                pop()
                pop()
            }
        }
    }
}

private fun inlinesPlainLength(inlines: List<Inline>): Int {
    var len = 0
    for (inline in inlines) {
        when (inline) {
            is Inline.Text -> len += inline.text.length
            is Inline.Code -> len += inline.text.length
            is Inline.Bold -> len += inlinesPlainLength(inline.children)
            is Inline.Italic -> len += inlinesPlainLength(inline.children)
            is Inline.Strike -> len += inlinesPlainLength(inline.children)
            is Inline.Link -> len += inlinesPlainLength(inline.children)
        }
    }
    return len
}

/**
 * Opens a link in an external viewer if its URL is in the safe allow-list.
 */
private fun openLinkSafely(context: Context, url: String) {
    if (!isSafeLinkUrl(url)) return
    try {
        val uri = Uri.parse(url.trim())
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Ignored as requested
    } catch (_: SecurityException) {
        // Ignored
    }
}

@Composable
fun MarkdownImageFallback(
    src: String,
    alt: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSafe = isSafeLinkUrl(src)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSafe) {
                    Modifier.clickable { openLinkSafely(context, src) }
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSafe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Image: $alt",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSafe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (isSafe) TextDecoration.Underline else TextDecoration.None
            )
        }
    }
}


