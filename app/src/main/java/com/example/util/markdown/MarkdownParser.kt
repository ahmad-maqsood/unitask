package com.example.util.markdown

sealed interface Inline {
    data class Text(val text: String) : Inline
    data class Bold(val children: List<Inline>) : Inline
    data class Italic(val children: List<Inline>) : Inline
    data class Strike(val children: List<Inline>) : Inline
    data class Code(val text: String) : Inline
    data class Link(val children: List<Inline>, val url: String) : Inline
}

enum class TableAlign {
    LEFT, CENTER, RIGHT
}

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val inlines: List<Inline>) : MarkdownBlock
    data class Paragraph(val inlines: List<Inline>) : MarkdownBlock
    data class BulletItem(val depth: Int, val inlines: List<Inline>) : MarkdownBlock
    data class NumberedItem(val depth: Int, val number: Int, val inlines: List<Inline>) : MarkdownBlock
    data class ChecklistItem(val depth: Int, val checked: Boolean, val inlines: List<Inline>, val sourceLine: Int) : MarkdownBlock
    data class Quote(val inlines: List<Inline>) : MarkdownBlock
    data class CodeBlock(val language: String, val text: String) : MarkdownBlock
    object Rule : MarkdownBlock
    data class Image(val alt: String, val src: String) : MarkdownBlock
    data class Table(
        val header: List<List<Inline>>,
        val alignments: List<TableAlign>,
        val rows: List<List<List<Inline>>>
    ) : MarkdownBlock
}

/** Parsed blocks plus the source line (0 based) each block starts on. Both lists have the same size. */
class ParsedMarkdown(val blocks: List<MarkdownBlock>, val startLines: List<Int>)

fun parseMarkdown(source: String): List<MarkdownBlock> = parseMarkdownIndexed(source).blocks

fun parseMarkdownIndexed(source: String): ParsedMarkdown {
    if (source.isEmpty()) return ParsedMarkdown(emptyList(), emptyList())

    val lines = source.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    val startLines = mutableListOf<Int>()
    var iterationStart = 0
    var lineIdx = 0

    while (lineIdx < lines.size) {
        // Blocks added by the previous pass start on the line that pass began on
        while (startLines.size < blocks.size) startLines.add(iterationStart)
        iterationStart = lineIdx
        val line = lines[lineIdx]

        // 1. Blank line: skip
        if (line.isBlank()) {
            lineIdx++
            continue
        }

        val trimmedStart = line.trimStart()

        // 2. Fenced code block: ```[lang]
        if (trimmedStart.startsWith("```")) {
            val lang = trimmedStart.substring(3).trim()
            val codeLines = mutableListOf<String>()
            lineIdx++
            while (lineIdx < lines.size) {
                val codeLine = lines[lineIdx]
                if (codeLine.trimStart().startsWith("```")) {
                    lineIdx++
                    break
                }
                codeLines.add(codeLine)
                lineIdx++
            }
            blocks.add(MarkdownBlock.CodeBlock(language = lang, text = codeLines.joinToString("\n")))
            continue
        }

        // 3. Horizontal rule: ---, ***, ___ (3 or more)
        val trimmed = line.trim()
        val noSpaces = trimmed.filter { it != ' ' }
        if (noSpaces.length >= 3 && (noSpaces.all { it == '-' } || noSpaces.all { it == '*' } || noSpaces.all { it == '_' })) {
            blocks.add(MarkdownBlock.Rule)
            lineIdx++
            continue
        }

        // 4. Standalone Image: line contains only ![alt](src)
        if (trimmed.startsWith("![") && trimmed.endsWith(")")) {
            val match = matchLinkOrImage(trimmed, 1)
            if (match != null && match.endIdx == trimmed.length) {
                blocks.add(MarkdownBlock.Image(alt = match.text, src = match.url))
                lineIdx++
                continue
            }
        }

        // 5. Heading: # to ######
        val leadingSpaces = line.length - trimmedStart.length
        if (leadingSpaces <= 3 && trimmedStart.startsWith("#")) {
            var hCount = 0
            while (hCount < trimmedStart.length && trimmedStart[hCount] == '#') {
                hCount++
            }
            if (hCount in 1..6 && (hCount == trimmedStart.length || trimmedStart[hCount] == ' ')) {
                val headingContent = if (hCount < trimmedStart.length) trimmedStart.substring(hCount + 1).trim() else ""
                blocks.add(MarkdownBlock.Heading(level = hCount, inlines = parseInlines(headingContent)))
                lineIdx++
                continue
            }
        }

        // 6. Block Quote: lines starting with "> " or lone ">"
        if (trimmedStart.startsWith("> ") || trimmedStart == ">") {
            val quoteLines = mutableListOf<String>()
            while (lineIdx < lines.size) {
                val cur = lines[lineIdx].trimStart()
                if (cur.startsWith("> ")) {
                    quoteLines.add(cur.substring(2))
                    lineIdx++
                } else if (cur == ">") {
                    quoteLines.add("")
                    lineIdx++
                } else {
                    break
                }
            }
            blocks.add(MarkdownBlock.Quote(inlines = parseInlines(quoteLines.joinToString("\n"))))
            continue
        }

        // 7. List Items: Checklist, Bullet, Numbered
        val listItem = parseListItemLine(line, lineIdx)
        if (listItem != null) {
            blocks.add(listItem)
            lineIdx++
            continue
        }

        // 8. Table (GitHub style)
        if (containsUnescapedPipe(line) && lineIdx + 1 < lines.size && isTableSeparator(lines[lineIdx + 1])) {
            val headerCells = splitTableCells(line)
            val colCount = headerCells.size
            val header = headerCells.map { parseInlines(it) }

            val sepCells = splitTableCells(lines[lineIdx + 1])
            val alignments = mutableListOf<TableAlign>()
            for (i in 0 until colCount) {
                val s = sepCells.getOrElse(i) { "" }.trim()
                val align = when {
                    s.startsWith(":") && s.endsWith(":") -> TableAlign.CENTER
                    s.endsWith(":") -> TableAlign.RIGHT
                    s.startsWith(":") -> TableAlign.LEFT
                    else -> TableAlign.LEFT
                }
                alignments.add(align)
            }

            lineIdx += 2
            val rows = mutableListOf<List<List<Inline>>>()
            while (lineIdx < lines.size) {
                val rowLine = lines[lineIdx]
                if (rowLine.isBlank() || !containsUnescapedPipe(rowLine) || isAnotherBlockStart(rowLine, lines, lineIdx)) {
                    break
                }
                val rawCells = splitTableCells(rowLine)
                val rowInlines = mutableListOf<List<Inline>>()
                for (c in 0 until colCount) {
                    val cellText = rawCells.getOrElse(c) { "" }
                    rowInlines.add(parseInlines(cellText))
                }
                rows.add(rowInlines)
                lineIdx++
            }
            blocks.add(MarkdownBlock.Table(header = header, alignments = alignments, rows = rows))
            continue
        }

        // 9. Paragraph: consecutive non-blank lines that are not another block type
        val paraLines = mutableListOf<String>()
        while (lineIdx < lines.size) {
            val curLine = lines[lineIdx]
            if (curLine.isBlank()) break
            if (paraLines.isNotEmpty() && isAnotherBlockStart(curLine, lines, lineIdx)) {
                break
            }
            paraLines.add(curLine)
            lineIdx++
        }
        if (paraLines.isNotEmpty()) {
            val paraText = paraLines.joinToString("\n")
            blocks.add(MarkdownBlock.Paragraph(inlines = parseInlines(paraText)))
        }
    }
    while (startLines.size < blocks.size) startLines.add(iterationStart)

    return ParsedMarkdown(blocks, startLines)
}

private fun parseListItemLine(line: String, lineIdx: Int): MarkdownBlock? {
    var charIdx = 0
    var leadingSpaces = 0
    while (charIdx < line.length) {
        when (line[charIdx]) {
            ' ' -> leadingSpaces += 1
            '\t' -> leadingSpaces += 4
            else -> break
        }
        charIdx++
    }
    val depth = (leadingSpaces / 2).coerceAtMost(3)
    val rest = line.substring(charIdx)

    // Checklist item
    if (rest.length >= 5 && (rest[0] == '-' || rest[0] == '*' || rest[0] == '+') &&
        rest[1] == ' ' && rest[2] == '[' && (rest[3] == ' ' || rest[3] == 'x' || rest[3] == 'X') && rest[4] == ']'
    ) {
        val checked = (rest[3] == 'x' || rest[3] == 'X')
        val content = if (rest.length > 5 && rest[5] == ' ') rest.substring(6) else rest.substring(5)
        return MarkdownBlock.ChecklistItem(
            depth = depth,
            checked = checked,
            inlines = parseInlines(content.trim()),
            sourceLine = lineIdx
        )
    }

    // Bullet item
    if (rest.startsWith("- ") || rest.startsWith("* ") || rest.startsWith("+ ") ||
        rest == "-" || rest == "*" || rest == "+"
    ) {
        val content = if (rest.length > 2) rest.substring(2) else ""
        return MarkdownBlock.BulletItem(
            depth = depth,
            inlines = parseInlines(content.trim())
        )
    }

    // Numbered item: 1. or 1)
    var dEnd = 0
    while (dEnd < rest.length && rest[dEnd].isDigit()) dEnd++
    if (dEnd > 0 && dEnd < rest.length && (rest[dEnd] == '.' || rest[dEnd] == ')') &&
        (dEnd + 1 == rest.length || rest[dEnd + 1] == ' ')
    ) {
        val num = rest.substring(0, dEnd).toIntOrNull() ?: 1
        val content = if (dEnd + 2 <= rest.length) rest.substring(dEnd + 2) else ""
        return MarkdownBlock.NumberedItem(
            depth = depth,
            number = num,
            inlines = parseInlines(content.trim())
        )
    }

    return null
}

private fun isAnotherBlockStart(line: String, lines: List<String>, idx: Int): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return true
    val trimmedStart = line.trimStart()

    // Fenced code block
    if (trimmedStart.startsWith("```")) return true

    // Block quote
    if (trimmedStart.startsWith("> ") || trimmedStart == ">") return true

    // Standalone Image
    if (trimmed.startsWith("![") && trimmed.endsWith(")")) {
        val match = matchLinkOrImage(trimmed, 1)
        if (match != null && match.endIdx == trimmed.length) return true
    }

    // Heading
    val leadingSpaces = line.length - trimmedStart.length
    if (leadingSpaces <= 3 && trimmedStart.startsWith("#")) {
        var hCount = 0
        while (hCount < trimmedStart.length && trimmedStart[hCount] == '#') hCount++
        if (hCount in 1..6 && (hCount == trimmedStart.length || trimmedStart[hCount] == ' ')) return true
    }

    // Rule
    val noSpaces = trimmed.filter { it != ' ' }
    if (noSpaces.length >= 3 && (noSpaces.all { it == '-' } || noSpaces.all { it == '*' } || noSpaces.all { it == '_' })) {
        return true
    }

    // List item
    if (parseListItemLine(line, idx) != null) return true

    // Table
    if (containsUnescapedPipe(line) && idx + 1 < lines.size && isTableSeparator(lines[idx + 1])) return true

    return false
}

private fun containsUnescapedPipe(line: String): Boolean {
    var i = 0
    while (i < line.length) {
        if (line[i] == '\\' && i + 1 < line.length && line[i + 1] == '|') {
            i += 2
        } else if (line[i] == '|') {
            return true
        } else {
            i++
        }
    }
    return false
}

private fun isTableSeparator(line: String): Boolean {
    val t = line.trim()
    if (!t.contains("-") || !containsUnescapedPipe(t)) return false
    val cells = splitTableCells(t)
    if (cells.isEmpty()) return false
    return cells.all { cell ->
        val c = cell.trim()
        if (c.isEmpty()) return@all false
        val withoutColons = c.removePrefix(":").removeSuffix(":")
        withoutColons.isNotEmpty() && withoutColons.all { it == '-' }
    }
}

private fun splitTableCells(line: String): List<String> {
    var s = line.trim()
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|") && !s.endsWith("\\|")) s = s.substring(0, s.length - 1)

    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length && s[i + 1] == '|') {
            current.append('|')
            i += 2
        } else if (c == '|') {
            cells.add(current.toString().trim())
            current.clear()
            i++
        } else {
            current.append(c)
            i++
        }
    }
    cells.add(current.toString().trim())
    return cells
}

fun parseInlines(text: String, depth: Int = 0): List<Inline> {
    if (text.isEmpty()) return emptyList()
    if (depth >= 4) return listOf(Inline.Text(text))

    val inlines = mutableListOf<Inline>()
    val cannotCloseFrom = mutableMapOf<String, Int>()
    var i = 0
    val len = text.length
    val plainBuffer = StringBuilder()

    fun flushPlain() {
        if (plainBuffer.isNotEmpty()) {
            inlines.add(Inline.Text(plainBuffer.toString()))
            plainBuffer.clear()
        }
    }

    while (i < len) {
        val c = text[i]

        // 1. Backslash escapes: \* \_ \` \[ \] \( \) \# \\ \|
        if (c == '\\' && i + 1 < len) {
            val next = text[i + 1]
            if (next in "\\*_`[]()#|~-+") {
                plainBuffer.append(next)
                i += 2
                continue
            }
        }

        // 2. Code span: `code`
        if (c == '`') {
            if ((cannotCloseFrom["`"] ?: Int.MAX_VALUE) <= i) {
                plainBuffer.append(c)
                i++
                continue
            }
            val close = text.indexOf('`', i + 1)
            if (close != -1) {
                flushPlain()
                val codeContent = text.substring(i + 1, close)
                inlines.add(Inline.Code(codeContent))
                i = close + 1
                continue
            } else {
                cannotCloseFrom["`"] = i
                plainBuffer.append(c)
                i++
                continue
            }
        }

        // 3. Inline Image: ![alt](url) -> turned into Link
        if (c == '!' && i + 1 < len && text[i + 1] == '[') {
            if ((cannotCloseFrom["link"] ?: Int.MAX_VALUE) <= i) {
                plainBuffer.append(c)
                i++
                continue
            }
            val match = matchLinkOrImage(text, i + 1)
            if (match != null) {
                flushPlain()
                val (alt, url, endIdx) = match
                val children = if (alt.isEmpty()) {
                    listOf(Inline.Text("image"))
                } else {
                    parseInlines(alt, depth + 1)
                }
                inlines.add(Inline.Link(children = children, url = url))
                i = endIdx
                continue
            } else {
                if (text.indexOf("](", i + 1) == -1) {
                    cannotCloseFrom["link"] = i
                }
            }
        }

        // 3b. Note link: [[Note title]]
        if (c == '[' && i + 1 < len && text[i + 1] == '[') {
            val close = text.indexOf("]]", i + 2)
            if (close != -1) {
                val inner = text.substring(i + 2, close)
                val title = sanitizeNoteLinkTitle(inner)
                val isPlainTitle = !inner.contains('[') && !inner.contains('\n')
                // "[[a]](url)" is an ordinary link whose text is "[a]", so it is left to the link rule
                val isFollowedByUrl = close + 2 < len && text[close + 2] == '('
                if (isPlainTitle && title.isNotEmpty() && !isFollowedByUrl) {
                    flushPlain()
                    inlines.add(
                        Inline.Link(
                            children = listOf(Inline.Text(title)),
                            url = NOTE_LINK_URL_PREFIX + title
                        )
                    )
                    i = close + 2
                    continue
                }
            }
        }

        // 4. Link: [text](url)
        if (c == '[') {
            if ((cannotCloseFrom["link"] ?: Int.MAX_VALUE) <= i) {
                plainBuffer.append(c)
                i++
                continue
            }
            val match = matchLinkOrImage(text, i)
            if (match != null) {
                flushPlain()
                val (linkText, url, endIdx) = match
                val children = parseInlines(linkText, depth + 1)
                inlines.add(Inline.Link(children = children, url = url))
                i = endIdx
                continue
            } else {
                if (text.indexOf("](", i) == -1) {
                    cannotCloseFrom["link"] = i
                }
            }
        }

        // 5. Bare URLs: http:// or https://
        if (c == 'h' && (text.startsWith("http://", i) || text.startsWith("https://", i))) {
            val urlEnd = findBareUrlEnd(text, i)
            val url = text.substring(i, urlEnd)
            if (url.length > 7) {
                flushPlain()
                inlines.add(Inline.Link(children = listOf(Inline.Text(url)), url = url))
                i = urlEnd
                continue
            }
        }

        // 6. Bold: ** or __
        if (text.startsWith("**", i)) {
            val close = findClosingDelimiter(text, i, "**", cannotCloseFrom)
            if (close != -1) {
                flushPlain()
                val content = text.substring(i + 2, close)
                inlines.add(Inline.Bold(parseInlines(content, depth + 1)))
                i = close + 2
                continue
            }
        }
        if (text.startsWith("__", i) && (i == 0 || !text[i - 1].isLetterOrDigit())) {
            val close = findClosingDelimiter(text, i, "__", cannotCloseFrom)
            if (close != -1) {
                flushPlain()
                val content = text.substring(i + 2, close)
                inlines.add(Inline.Bold(parseInlines(content, depth + 1)))
                i = close + 2
                continue
            }
        }

        // 7. Strikethrough: ~~
        if (text.startsWith("~~", i)) {
            val close = findClosingDelimiter(text, i, "~~", cannotCloseFrom)
            if (close != -1) {
                flushPlain()
                val content = text.substring(i + 2, close)
                inlines.add(Inline.Strike(parseInlines(content, depth + 1)))
                i = close + 2
                continue
            }
        }

        // 8. Italic: * or _
        if (c == '*' && !text.startsWith("**", i)) {
            val close = findClosingItalic(text, i, '*', cannotCloseFrom)
            if (close != -1) {
                flushPlain()
                val content = text.substring(i + 1, close)
                inlines.add(Inline.Italic(parseInlines(content, depth + 1)))
                i = close + 1
                continue
            }
        }
        if (c == '_' && !text.startsWith("__", i) && (i == 0 || !text[i - 1].isLetterOrDigit())) {
            val close = findClosingItalic(text, i, '_', cannotCloseFrom)
            if (close != -1) {
                flushPlain()
                val content = text.substring(i + 1, close)
                inlines.add(Inline.Italic(parseInlines(content, depth + 1)))
                i = close + 1
                continue
            }
        }

        plainBuffer.append(c)
        i++
    }
    flushPlain()

    return mergeInlines(inlines)
}

private fun findClosingDelimiter(
    text: String,
    openPos: Int,
    marker: String,
    cannotCloseFrom: MutableMap<String, Int>
): Int {
    if ((cannotCloseFrom[marker] ?: Int.MAX_VALUE) <= openPos) {
        return -1
    }
    val mLen = marker.length
    if (openPos + mLen >= text.length || text[openPos + mLen].isWhitespace()) {
        return -1
    }

    var searchPos = openPos + mLen + 1
    val isUnderscore = marker.startsWith("_")
    val isOpenTripleStar = (marker == "**" && ((openPos == 0 && text.startsWith("***")) || (openPos > 0 && text[openPos - 1] == '*')))

    while (searchPos < text.length) {
        val idx = text.indexOf(marker, searchPos)
        if (idx == -1) {
            cannotCloseFrom[marker] = openPos
            return -1
        }
        if (!text[idx - 1].isWhitespace()) {
            if (isUnderscore) {
                val after = idx + mLen
                if (after == text.length || !text[after].isLetterOrDigit()) {
                    return idx
                }
            } else {
                if (isOpenTripleStar && idx + 2 < text.length && text[idx + 2] == '*') {
                    return idx + 1
                }
                return idx
            }
        }
        searchPos = idx + 1
    }
    cannotCloseFrom[marker] = openPos
    return -1
}

private fun findClosingItalic(
    text: String,
    openPos: Int,
    markerChar: Char,
    cannotCloseFrom: MutableMap<String, Int>
): Int {
    val key = markerChar.toString()
    if ((cannotCloseFrom[key] ?: Int.MAX_VALUE) <= openPos) {
        return -1
    }
    if (openPos + 1 >= text.length || text[openPos + 1].isWhitespace() || text[openPos + 1] == markerChar) {
        return -1
    }

    var searchPos = openPos + 2
    val isUnderscore = (markerChar == '_')

    while (searchPos < text.length) {
        val idx = text.indexOf(markerChar, searchPos)
        if (idx == -1) {
            cannotCloseFrom[key] = openPos
            return -1
        }
        if (!isUnderscore && idx + 1 < text.length && text[idx + 1] == '*') {
            searchPos = idx + 2
            continue
        }
        if (!text[idx - 1].isWhitespace()) {
            if (isUnderscore) {
                if (idx + 1 < text.length && text[idx + 1] == '_') {
                    searchPos = idx + 2
                    continue
                }
                if (idx + 1 == text.length || !text[idx + 1].isLetterOrDigit()) {
                    return idx
                }
            } else {
                return idx
            }
        }
        searchPos = idx + 1
    }
    cannotCloseFrom[key] = openPos
    return -1
}

private data class LinkMatch(val text: String, val url: String, val endIdx: Int)

private fun matchLinkOrImage(text: String, bracketStart: Int): LinkMatch? {
    var depth = 0
    var closeBracket = -1
    var p = bracketStart + 1
    while (p < text.length) {
        val c = text[p]
        if (c == '\\' && p + 1 < text.length) {
            p += 2
            continue
        }
        if (c == '[') depth++
        else if (c == ']') {
            if (depth > 0) {
                depth--
            } else {
                if (p + 1 < text.length && text[p + 1] == '(') {
                    closeBracket = p
                    break
                } else {
                    return null
                }
            }
        }
        p++
    }
    if (closeBracket == -1) return null

    val openParen = closeBracket + 1
    var parenDepth = 0
    var closeParen = -1
    var q = openParen + 1
    while (q < text.length) {
        val c = text[q]
        if (c == '\\' && q + 1 < text.length) {
            q += 2
            continue
        }
        if (c == '(') {
            parenDepth++
        } else if (c == ')') {
            if (parenDepth > 0) {
                parenDepth--
            } else {
                closeParen = q
                break
            }
        }
        q++
    }
    if (closeParen == -1) return null

    val linkText = text.substring(bracketStart + 1, closeBracket)
    val linkUrl = text.substring(openParen + 1, closeParen)
    return LinkMatch(linkText, linkUrl, closeParen + 1)
}

private fun findBareUrlEnd(text: String, start: Int): Int {
    var end = start
    while (end < text.length) {
        val c = text[end]
        if (c.isWhitespace() || c == ')' || c == ']' || c == '>' || c == '"' || c == '<') {
            break
        }
        end++
    }
    while (end > start && (text[end - 1] == '.' || text[end - 1] == ',' || text[end - 1] == ';' || text[end - 1] == '!')) {
        end--
    }
    return end
}

private fun mergeInlines(inlines: List<Inline>): List<Inline> {
    if (inlines.isEmpty()) return emptyList()
    val result = mutableListOf<Inline>()
    var currentText = StringBuilder()
    for (item in inlines) {
        if (item is Inline.Text) {
            currentText.append(item.text)
        } else {
            if (currentText.isNotEmpty()) {
                result.add(Inline.Text(currentText.toString()))
                currentText = StringBuilder()
            }
            result.add(item)
        }
    }
    if (currentText.isNotEmpty()) {
        result.add(Inline.Text(currentText.toString()))
    }
    return result
}

fun referencedImageNames(source: String): Set<String> {
    if (source.isEmpty()) return emptySet()
    val result = mutableSetOf<String>()
    val prefix = "unitask-image:"
    var i = 0
    while (i < source.length) {
        val imgStart = source.indexOf("![", i)
        if (imgStart == -1) break
        val closeBracket = source.indexOf("](", imgStart + 2)
        if (closeBracket == -1) {
            break
        }
        val closeParen = source.indexOf(")", closeBracket + 2)
        if (closeParen == -1) {
            i = closeBracket + 2
            continue
        }
        val src = source.substring(closeBracket + 2, closeParen).trim()
        if (src.startsWith(prefix)) {
            val name = src.substring(prefix.length).trim()
            if (name.isNotEmpty()) {
                result.add(name)
            }
        }
        i = closeParen + 1
    }
    return result
}
