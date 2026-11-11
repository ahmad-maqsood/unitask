package com.example.util.markdown

/** The kinds of piece a line of code is split into so each kind can get its own colour. */
enum class CodeTokenType { KEYWORD, STRING, COMMENT, NUMBER, TYPE, FUNCTION }

/** A coloured piece of code: characters [start, end) of the code text. Anything not covered stays plain. */
data class CodeToken(val start: Int, val end: Int, val type: CodeTokenType)

/**
 * A small, dependency-free syntax highlighter for fenced code blocks (```py, ```kotlin, ...).
 * It is not a full parser: it finds comments, strings, numbers, keywords, type names and function
 * calls, which is enough to make code readable. Unknown or missing languages are left plain.
 */
object CodeHighlighter {

    private class Spec(
        val keywords: Set<String>,
        val lineComments: List<String> = emptyList(),
        val blockComment: Pair<String, String>? = null,
        val quotes: String = "\"'",
        val tripleQuotes: Boolean = false,
        val ignoreCase: Boolean = false,
        val markup: Boolean = false,
        val highlightFunctions: Boolean = true,
        val highlightTypes: Boolean = true
    )

    private fun words(text: String): Set<String> = text.trim().split(Regex("\\s+")).toSet()

    private val cLike = Spec(
        keywords = words(
            "if else for while do switch case break continue return default goto sizeof typedef struct union enum " +
                "static const volatile extern register inline void int long short char float double signed unsigned " +
                "bool true false null nullptr new delete class public private protected virtual override namespace " +
                "using template typename this try catch throw include define"
        ),
        lineComments = listOf("//"),
        blockComment = "/*" to "*/"
    )

    private val python = Spec(
        keywords = words(
            "False None True and as assert async await break class continue def del elif else except finally for " +
                "from global if import in is lambda nonlocal not or pass raise return try while with yield self cls " +
                "match case print"
        ),
        lineComments = listOf("#"),
        tripleQuotes = true
    )

    private val javascript = Spec(
        keywords = words(
            "var let const function return if else for while do switch case break continue default new delete " +
                "typeof instanceof in of this class extends super import export from as async await yield try catch " +
                "finally throw null undefined true false void static get set interface type enum implements " +
                "public private protected readonly abstract namespace declare"
        ),
        lineComments = listOf("//"),
        blockComment = "/*" to "*/",
        quotes = "\"'`"
    )

    private val java = Spec(
        keywords = words(
            "abstract assert boolean break byte case catch char class const continue default do double else enum " +
                "extends final finally float for goto if implements import instanceof int interface long native new " +
                "package private protected public return short static strictfp super switch synchronized this throw " +
                "throws transient try void volatile while true false null var record sealed permits"
        ),
        lineComments = listOf("//"),
        blockComment = "/*" to "*/"
    )

    private val kotlin = Spec(
        keywords = words(
            "abstract annotation as break by catch class companion const constructor continue crossinline data " +
                "delegate do dynamic else enum external false final finally for fun get if import in infix init " +
                "inline inner interface internal is it lateinit lazy noinline null object open operator out override " +
                "package private protected public reified return sealed set super suspend tailrec this throw true " +
                "try typealias typeof val value var vararg when where while"
        ),
        lineComments = listOf("//"),
        blockComment = "/*" to "*/",
        tripleQuotes = true
    )

    private val csharp = Spec(
        keywords = words(
            "abstract as base bool break byte case catch char checked class const continue decimal default delegate " +
                "do double else enum event explicit extern false finally fixed float for foreach goto if implicit in " +
                "int interface internal is lock long namespace new null object operator out override params private " +
                "protected public readonly ref return sbyte sealed short sizeof static string struct switch this throw " +
                "true try typeof uint ulong unchecked unsafe ushort using var virtual void volatile while async await"
        ),
        lineComments = listOf("//"),
        blockComment = "/*" to "*/"
    )

    private val go = Spec(
        keywords = words(
            "break case chan const continue default defer else fallthrough for func go goto if import interface map " +
                "package range return select struct switch type var true false nil iota string int int8 int16 int32 " +
                "int64 uint uint8 uint16 uint32 uint64 float32 float64 bool byte rune error any"
        ),
        lineComments = listOf("//"),
        blockComment = "/*" to "*/",
        quotes = "\"'`"
    )

    private val rust = Spec(
        keywords = words(
            "as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod " +
                "move mut pub ref return self Self static struct super trait true type unsafe use where while " +
                "i8 i16 i32 i64 i128 isize u8 u16 u32 u64 u128 usize f32 f64 bool char str String Vec Option Result " +
                "Some None Ok Err"
        ),
        lineComments = listOf("//"),
        blockComment = "/*" to "*/"
    )

    private val swift = Spec(
        keywords = words(
            "associatedtype class deinit enum extension fileprivate func import init inout internal let open operator " +
                "private protocol public static struct subscript typealias var break case continue default defer do " +
                "else fallthrough for guard if in repeat return switch where while as Any catch false is nil rethrows " +
                "super self Self throw throws true try async await"
        ),
        lineComments = listOf("//"),
        blockComment = "/*" to "*/"
    )

    private val dart = Spec(
        keywords = words(
            "abstract as assert async await break case catch class const continue covariant default deferred do " +
                "dynamic else enum export extends extension external factory false final finally for Function get " +
                "hide if implements import in interface is late library mixin new null on operator part required " +
                "rethrow return set show static super switch sync this throw true try typedef var void while with yield"
        ),
        lineComments = listOf("//"),
        blockComment = "/*" to "*/"
    )

    private val shell = Spec(
        keywords = words(
            "if then else elif fi for while until do done case esac in function select time return exit break " +
                "continue local export readonly declare set unset echo cd source alias true false"
        ),
        lineComments = listOf("#"),
        quotes = "\"'`",
        highlightTypes = false
    )

    private val sql = Spec(
        keywords = words(
            "select from where and or not null is in like between join inner outer left right full cross on as " +
                "group by order having limit offset insert into values update set delete create table alter drop " +
                "index view primary key foreign references default unique distinct union all case when then else end " +
                "count sum avg min max asc desc exists begin commit rollback int integer text varchar boolean date " +
                "timestamp true false"
        ),
        lineComments = listOf("--"),
        blockComment = "/*" to "*/",
        ignoreCase = true,
        highlightTypes = false
    )

    private val json = Spec(
        keywords = words("true false null"),
        quotes = "\"",
        highlightFunctions = false,
        highlightTypes = false
    )

    private val html = Spec(
        keywords = emptySet(),
        blockComment = "<!--" to "-->",
        markup = true,
        highlightFunctions = false,
        highlightTypes = false
    )

    private val css = Spec(
        keywords = words("important"),
        blockComment = "/*" to "*/",
        highlightFunctions = false,
        highlightTypes = false
    )

    private val yaml = Spec(
        keywords = words("true false null yes no on off"),
        lineComments = listOf("#"),
        highlightFunctions = false,
        highlightTypes = false
    )

    /** Anything labelled with a language we do not know still gets comments, strings and numbers. */
    private val generic = Spec(
        keywords = emptySet(),
        lineComments = listOf("//"),
        blockComment = "/*" to "*/",
        highlightFunctions = false,
        highlightTypes = false
    )

    private val specs: Map<String, Spec> = buildMap {
        listOf("c", "h", "cpp", "c++", "cc", "cxx", "hpp", "objc", "objective-c").forEach { put(it, cLike) }
        listOf("py", "python", "python3", "py3").forEach { put(it, python) }
        listOf("js", "javascript", "jsx", "ts", "typescript", "tsx", "mjs", "node").forEach { put(it, javascript) }
        put("java", java)
        listOf("kt", "kotlin", "kts").forEach { put(it, kotlin) }
        listOf("cs", "csharp", "c#").forEach { put(it, csharp) }
        listOf("go", "golang").forEach { put(it, go) }
        listOf("rs", "rust").forEach { put(it, rust) }
        put("swift", swift)
        put("dart", dart)
        listOf("sh", "bash", "zsh", "shell", "console", "fish").forEach { put(it, shell) }
        listOf("sql", "mysql", "postgres", "postgresql", "sqlite").forEach { put(it, sql) }
        listOf("json", "jsonc").forEach { put(it, json) }
        listOf("html", "xml", "svg", "xhtml").forEach { put(it, html) }
        listOf("css", "scss", "less").forEach { put(it, css) }
        listOf("yml", "yaml", "toml", "ini").forEach { put(it, yaml) }
    }

    /**
     * Splits [code] into coloured pieces for the fence label [language] (the text after the three backticks;
     * extra words such as "python title=x" are ignored). A blank language gives no tokens (plain text).
     */
    fun tokenize(code: String, language: String): List<CodeToken> {
        val name = language.trim().substringBefore(' ').substringBefore('{').lowercase()
        if (name.isEmpty() || name == "text" || name == "txt" || name == "plain" || name == "plaintext") {
            return emptyList()
        }
        return scan(code, specs[name] ?: generic)
    }

    private fun isIdentStart(c: Char) = c.isLetter() || c == '_' || c == '$'
    private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

    private fun scan(code: String, spec: Spec): List<CodeToken> {
        val tokens = ArrayList<CodeToken>()
        val n = code.length
        var i = 0
        while (i < n) {
            val c = code[i]

            // Block comment
            val block = spec.blockComment
            if (block != null && code.startsWith(block.first, i)) {
                val close = code.indexOf(block.second, i + block.first.length)
                val end = if (close < 0) n else close + block.second.length
                tokens.add(CodeToken(i, end, CodeTokenType.COMMENT))
                i = end
                continue
            }

            // Line comment
            if (spec.lineComments.any { code.startsWith(it, i) }) {
                var end = code.indexOf('\n', i)
                if (end < 0) end = n
                tokens.add(CodeToken(i, end, CodeTokenType.COMMENT))
                i = end
                continue
            }

            // Triple-quoted string (Python docstrings, Kotlin raw strings)
            if (spec.tripleQuotes && (code.startsWith("\"\"\"", i) || code.startsWith("'''", i))) {
                val quote = code.substring(i, i + 3)
                val close = code.indexOf(quote, i + 3)
                val end = if (close < 0) n else close + 3
                tokens.add(CodeToken(i, end, CodeTokenType.STRING))
                i = end
                continue
            }

            // String
            if (spec.quotes.indexOf(c) >= 0) {
                val multiline = c == '`'
                var j = i + 1
                while (j < n) {
                    val d = code[j]
                    if (d == '\\' && !multiline && j + 1 < n) {
                        j += 2
                        continue
                    }
                    if (d == c) {
                        j++
                        break
                    }
                    if (d == '\n' && !multiline) break
                    j++
                }
                tokens.add(CodeToken(i, j, CodeTokenType.STRING))
                i = j
                continue
            }

            // Markup tag name: <div, </div
            if (spec.markup && c == '<' && i + 1 < n) {
                var j = i + 1
                if (code[j] == '/') j++
                val nameStart = j
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '-' || code[j] == ':' || code[j] == '_')) j++
                if (j > nameStart) {
                    tokens.add(CodeToken(nameStart, j, CodeTokenType.KEYWORD))
                    i = j
                    continue
                }
            }

            // Number
            if (c.isDigit() && (i == 0 || !isIdentPart(code[i - 1]))) {
                var j = i + 1
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_' || (code[j] == '.' && j + 1 < n && code[j + 1].isDigit()))) j++
                tokens.add(CodeToken(i, j, CodeTokenType.NUMBER))
                i = j
                continue
            }

            // Word: keyword, type name or function call
            if (isIdentStart(c)) {
                var j = i + 1
                while (j < n && isIdentPart(code[j])) j++
                val word = code.substring(i, j)
                val key = if (spec.ignoreCase) word.lowercase() else word
                when {
                    key in spec.keywords -> tokens.add(CodeToken(i, j, CodeTokenType.KEYWORD))
                    spec.highlightTypes && c.isUpperCase() && word.length > 1 ->
                        tokens.add(CodeToken(i, j, CodeTokenType.TYPE))
                    spec.highlightFunctions && nextNonBlankIs(code, j, '(') ->
                        tokens.add(CodeToken(i, j, CodeTokenType.FUNCTION))
                }
                i = j
                continue
            }

            i++
        }
        return tokens
    }

    private fun nextNonBlankIs(code: String, from: Int, expected: Char): Boolean {
        var k = from
        while (k < code.length && (code[k] == ' ' || code[k] == '\t')) k++
        return k < code.length && code[k] == expected
    }
}
