package com.orch.app.ui

/**
 * Zero-dependency Markdown parser for AI chat responses.
 *
 * Handles:
 *  - Fenced code blocks (``` lang … ```) → CodeBlock segment
 *  - Inline code (`code`) → InlineCode segment
 *  - **Bold**, *Italic*
 *  - Plain text
 *  - Numbered / bullet lists (passed through as plain with preserved formatting)
 */

// ── Segment types ──────────────────────────────────────────────────────────
sealed class MessageSegment {
    data class Plain(val text: String) : MessageSegment()
    data class CodeBlock(val language: String, val code: String) : MessageSegment()
    data class InlineCode(val code: String) : MessageSegment()
}

// ── Parser ─────────────────────────────────────────────────────────────────
object MarkdownParser {

    private val FENCE = Regex("^```(\\w*)?\\s*$")

    /**
     * Parse [text] into a list of [MessageSegment]s.
     * Streaming-safe: unclosed code blocks are rendered as plain text.
     */
    fun parse(text: String): List<MessageSegment> {
        if (text.isBlank()) return listOf(MessageSegment.Plain(text))

        val segments = mutableListOf<MessageSegment>()
        val lines = text.lines()

        var i = 0
        val plainBuffer = StringBuilder()

        fun flushPlain() {
            if (plainBuffer.isNotEmpty()) {
                segments += MessageSegment.Plain(plainBuffer.toString())
                plainBuffer.clear()
            }
        }

        while (i < lines.size) {
            val line = lines[i]
            val fenceMatch = FENCE.matchEntire(line.trim())

            if (fenceMatch != null) {
                val lang = fenceMatch.groupValues[1].lowercase().trim()
                val codeLines = StringBuilder()
                i++
                var closed = false

                while (i < lines.size) {
                    val codeLine = lines[i]
                    if (codeLine.trim() == "```") {
                        closed = true
                        i++ // skip closing fence
                        break
                    }
                    codeLines.appendLine(codeLine)
                    i++
                }

                if (closed) {
                    flushPlain()
                    segments += MessageSegment.CodeBlock(lang, codeLines.toString().trimEnd('\n'))
                } else {
                    // Streaming — unclosed block: treat as plain
                    plainBuffer.append("```$lang\n").append(codeLines)
                }
            } else {
                // Process inline code within the line
                val inlineSegments = parseInline(line)
                if (inlineSegments.size == 1 && inlineSegments[0] is MessageSegment.Plain) {
                    plainBuffer.appendLine(line)
                } else {
                    flushPlain()
                    // Append each inline segment; group consecutive Plains
                    for (seg in inlineSegments) {
                        when (seg) {
                            is MessageSegment.Plain -> plainBuffer.append(seg.text)
                            else -> {
                                flushPlain()
                                segments += seg
                            }
                        }
                    }
                    plainBuffer.append("\n")
                }
                i++
            }
        }

        flushPlain()
        
        // Final pass: filter out segments that are just whitespace to avoid "gap inflation"
        // but keep actual content. Also merge consecutive plain segments if any remain.
        val finalSegments = mutableListOf<MessageSegment>()
        for (seg in segments) {
            if (seg is MessageSegment.Plain && seg.text.isBlank()) continue
            
            val last = finalSegments.lastOrNull()
            if (seg is MessageSegment.Plain && last is MessageSegment.Plain) {
                finalSegments[finalSegments.size - 1] = MessageSegment.Plain(last.text + seg.text)
            } else {
                finalSegments += seg
            }
        }
        return finalSegments
    }

    /** Split a single line by inline-code backticks */
    private fun parseInline(line: String): List<MessageSegment> {
        if (!line.contains('`')) return listOf(MessageSegment.Plain(line))
        val result = mutableListOf<MessageSegment>()
        val parts = line.split("`")
        parts.forEachIndexed { idx, part ->
            if (idx % 2 == 0) {
                if (part.isNotEmpty()) result += MessageSegment.Plain(part)
            } else {
                result += MessageSegment.InlineCode(part)
            }
        }
        return result
    }
}

// ── Syntax colouring helpers (used by CodeBlockView) ──────────────────────
object SyntaxHighlighter {

    data class TokenSpan(val start: Int, val end: Int, val type: TokenType)

    enum class TokenType { Keyword, String, Number, Comment, Operator, Default }

    private val PYTHON_KEYWORDS = setOf(
        "False","None","True","and","as","assert","async","await",
        "break","class","continue","def","del","elif","else","except",
        "finally","for","from","global","if","import","in","is",
        "lambda","nonlocal","not","or","pass","raise","return","try",
        "while","with","yield","print","len","range","type","self"
    )
    private val KOTLIN_KEYWORDS = setOf(
        "abstract","actual","annotation","as","break","by","catch","class",
        "companion","const","constructor","continue","crossinline","data",
        "delegate","do","dynamic","else","enum","expect","external","false",
        "field","file","final","finally","for","fun","get","if","import",
        "in","infix","init","inline","inner","interface","internal","is",
        "it","lateinit","noinline","null","object","open","operator","out",
        "override","package","param","private","property","protected","public",
        "receiver","reified","return","sealed","set","setparam","super",
        "suspend","tailrec","this","throw","true","try","typealias","typeof",
        "val","value","var","vararg","when","where","while"
    )
    private val JS_TS_KEYWORDS = setOf(
        "abstract","arguments","async","await","boolean","break","byte","case",
        "catch","char","class","const","continue","debugger","default","delete",
        "do","double","else","enum","eval","export","extends","false","final",
        "finally","float","for","from","function","goto","if","implements",
        "import","in","instanceof","int","interface","let","long","native",
        "new","null","package","private","protected","public","return","short",
        "static","super","switch","synchronized","this","throw","throws",
        "transient","true","try","type","typeof","undefined","var","void",
        "volatile","while","with","yield","console","Promise","Array","Object",
        "string","number","boolean","any","void","never","unknown"
    )

    fun tokenize(code: String, language: String): List<TokenSpan> {
        val spans = mutableListOf<TokenSpan>()
        val keywords = when (language.lowercase()) {
            "python", "py" -> PYTHON_KEYWORDS
            "kotlin", "kt" -> KOTLIN_KEYWORDS
            "javascript", "js", "typescript", "ts", "jsx", "tsx" -> JS_TS_KEYWORDS
            else -> emptySet()
        }

        val len = code.length
        var i = 0

        while (i < len) {
            // --- Single-line comment ---
            val isLineComment = (i + 1 < len) &&
                (code[i] == '/' && code[i + 1] == '/') ||
                (language in listOf("python","py") && code[i] == '#')
            if (isLineComment) {
                val end = code.indexOf('\n', i).let { if (it == -1) len else it }
                spans += TokenSpan(i, end, TokenType.Comment)
                i = end
                continue
            }

            // --- Block comment /* ... */ ---
            if (i + 1 < len && code[i] == '/' && code[i + 1] == '*') {
                val end = code.indexOf("*/", i + 2).let { if (it == -1) len else it + 2 }
                spans += TokenSpan(i, end, TokenType.Comment)
                i = end
                continue
            }

            // --- Python # comment ---
            if (language in listOf("python","py") && code[i] == '#') {
                val end = code.indexOf('\n', i).let { if (it == -1) len else it }
                spans += TokenSpan(i, end, TokenType.Comment)
                i = end
                continue
            }

            // --- String literals (single, double, triple) ---
            if (code[i] == '"' || code[i] == '\'') {
                val quote = code[i]
                val isTriple = i + 2 < len && code[i + 1] == quote && code[i + 2] == quote
                val endSeq = if (isTriple) "$quote$quote$quote" else quote.toString()
                val start = i
                i += endSeq.length
                while (i < len) {
                    if (code[i] == '\\') { i += 2; continue }
                    if (code.startsWith(endSeq, i)) { i += endSeq.length; break }
                    i++
                }
                spans += TokenSpan(start, i, TokenType.String)
                continue
            }

            // --- Numbers ---
            if (code[i].isDigit() || (code[i] == '.' && i + 1 < len && code[i + 1].isDigit())) {
                val start = i
                while (i < len && (code[i].isLetterOrDigit() || code[i] == '.' || code[i] == '_')) i++
                spans += TokenSpan(start, i, TokenType.Number)
                continue
            }

            // --- Identifiers / Keywords ---
            if (code[i].isLetter() || code[i] == '_') {
                val start = i
                while (i < len && (code[i].isLetterOrDigit() || code[i] == '_')) i++
                val word = code.substring(start, i)
                if (keywords.contains(word)) {
                    spans += TokenSpan(start, i, TokenType.Keyword)
                }
                continue
            }

            i++
        }
        return spans.sortedBy { it.start }
    }
}
