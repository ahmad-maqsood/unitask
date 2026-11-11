package com.example.util

import com.example.util.markdown.CodeHighlighter
import com.example.util.markdown.CodeToken
import com.example.util.markdown.CodeTokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHighlighterTest {

    private fun texts(code: String, lang: String, type: CodeTokenType): List<String> =
        CodeHighlighter.tokenize(code, lang)
            .filter { it.type == type }
            .map { code.substring(it.start, it.end) }

    @Test
    fun pythonKeywordsStringsCommentsAndNumbers() {
        val code = "def greet(name):\n    # say hi\n    return \"hi \" + name * 2"
        assertEquals(listOf("def", "return"), texts(code, "py", CodeTokenType.KEYWORD))
        assertEquals(listOf("\"hi \""), texts(code, "python", CodeTokenType.STRING))
        assertEquals(listOf("# say hi"), texts(code, "py", CodeTokenType.COMMENT))
        assertEquals(listOf("2"), texts(code, "py", CodeTokenType.NUMBER))
        assertEquals(listOf("greet"), texts(code, "py", CodeTokenType.FUNCTION))
    }

    @Test
    fun pythonTripleQuotedStringSpansLines() {
        val code = "x = \"\"\"a\nb\"\"\"\ny = 1"
        assertEquals(listOf("\"\"\"a\nb\"\"\""), texts(code, "py", CodeTokenType.STRING))
    }

    @Test
    fun hashInsideStringIsNotAComment() {
        val code = "s = \"# not a comment\""
        assertTrue(texts(code, "py", CodeTokenType.COMMENT).isEmpty())
    }

    @Test
    fun kotlinBlockCommentAndType() {
        val code = "/* note */ val list: List<Int> = listOf(1)"
        assertEquals(listOf("/* note */"), texts(code, "kotlin", CodeTokenType.COMMENT))
        assertEquals(listOf("val"), texts(code, "kt", CodeTokenType.KEYWORD))
        assertEquals(listOf("List", "Int"), texts(code, "kt", CodeTokenType.TYPE))
    }

    @Test
    fun unterminatedStringStopsAtEndOfLine() {
        val code = "a = 'oops\nb = 2"
        assertEquals(listOf("'oops"), texts(code, "py", CodeTokenType.STRING))
        assertEquals(listOf("2"), texts(code, "py", CodeTokenType.NUMBER))
    }

    @Test
    fun sqlKeywordsIgnoreCase() {
        val code = "SELECT name FROM users"
        assertEquals(listOf("SELECT", "FROM"), texts(code, "sql", CodeTokenType.KEYWORD))
    }

    @Test
    fun htmlTagNames() {
        val code = "<div class=\"a\"></div>"
        assertEquals(listOf("div", "div"), texts(code, "html", CodeTokenType.KEYWORD))
        assertEquals(listOf("\"a\""), texts(code, "html", CodeTokenType.STRING))
    }

    @Test
    fun noLanguageOrPlainTextIsLeftAlone() {
        assertTrue(CodeHighlighter.tokenize("def x(): pass", "").isEmpty())
        assertTrue(CodeHighlighter.tokenize("def x(): pass", "text").isEmpty())
    }

    @Test
    fun unknownLanguageStillColoursStringsAndComments() {
        val tokens: List<CodeToken> = CodeHighlighter.tokenize("x = \"a\" // c", "mylang")
        assertEquals(listOf(CodeTokenType.STRING, CodeTokenType.COMMENT), tokens.map { it.type })
    }

    @Test
    fun languageLabelWithExtraWordsIsUnderstood() {
        assertEquals(listOf("def", "pass"), texts("def f(): pass", "python title=demo", CodeTokenType.KEYWORD))
    }
}
