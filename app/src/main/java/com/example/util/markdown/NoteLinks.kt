package com.example.util.markdown

/**
 * Links between notes. A link is written as [[Note title]] in the body.
 *
 * The preview shows it as a tappable link and finds the note by its title, ignoring capitals.
 * Typing "[[" in the editor offers matching notes to pick from.
 */

/** The preview turns [[Title]] into a link with this prefix, so tapping it can open the note. */
const val NOTE_LINK_URL_PREFIX = "unitask-note:"

/** The longest title the "[[" suggestions will follow, so a stray "[[" far back does not trigger them. */
const val MAX_NOTE_LINK_QUERY = 100

private val NOTE_LINK_REGEX = Regex("\\[\\[([^\\[\\]\\n]+)\\]\\]")

/** Where a "[[" is being typed: [start, end) is the text typed after it, up to the cursor. */
data class NoteLinkQuery(val start: Int, val end: Int, val query: String)

/** Removes what would break a link: brackets and line breaks. Extra spaces are squeezed to one. */
fun sanitizeNoteLinkTitle(title: String): String =
    title.replace('[', ' ').replace(']', ' ').replace(Regex("\\s+"), " ").trim()

/** The form two titles are compared in: cleaned and lower case. */
fun noteLinkKey(title: String): String = sanitizeNoteLinkTitle(title).lowercase()

/** Every [[title]] written in a note body, in order (repeats included). */
fun noteLinkTitles(body: String): List<String> {
    if (!body.contains("[[")) return emptyList()
    return NOTE_LINK_REGEX.findAll(body).map { it.groupValues[1] }.toList()
}

/**
 * Returns the "[[" the cursor is typing inside, or null. It is null when the line has no "[["
 * before the cursor, when the link was already closed with "]]", or when it is too far back.
 */
fun activeNoteLinkQuery(text: String, cursor: Int): NoteLinkQuery? {
    if (cursor < 2 || cursor > text.length) return null
    var i = cursor - 1
    while (i >= 1) {
        if (cursor - i > MAX_NOTE_LINK_QUERY + 2) return null
        val c = text[i]
        if (c == '\n' || c == ']') return null
        if (c == '[') {
            return if (text[i - 1] == '[') NoteLinkQuery(i + 1, cursor, text.substring(i + 1, cursor)) else null
        }
        i--
    }
    return null
}

/**
 * Finishes the link being typed: puts the chosen title after the "[[" and closes it with "]]".
 * When "]]" already follows the cursor it is kept rather than doubled. The cursor ends up after it.
 */
fun applyNoteLinkChoice(editor: EditorText, query: NoteLinkQuery, title: String): EditorText {
    val safe = sanitizeNoteLinkTitle(title)
    val text = editor.text
    val start = query.start.coerceIn(0, text.length)
    val end = query.end.coerceIn(start, text.length)
    val before = text.substring(0, start)
    val after = text.substring(end)
    val rest = if (after.startsWith("]]")) after.substring(2) else after
    val cursor = start + safe.length + 2
    return EditorText(before + safe + "]]" + rest, cursor, cursor)
}
