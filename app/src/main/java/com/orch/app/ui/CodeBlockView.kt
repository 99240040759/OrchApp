package com.orch.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orch.app.ui.SyntaxHighlighter.TokenType
import com.orch.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Colour palette for code ────────────────────────────────────────────────
private val CodeBg         = Color(0xFF0D1117)       // deep GitHub dark
private val CodeSurface    = Color(0xFF161B22)
private val CodeKeyword    = Color(0xFFFF7B72)        // coral-red
private val CodeString     = Color(0xFFA5D6FF)        // sky blue
private val CodeNumber     = Color(0xFFD2A8FF)        // purple
private val CodeComment    = Color(0xFF8B949E)        // grey
private val CodeDefault    = Color(0xFFE6EDF3)        // near-white
private val LangBadgeBg    = Color(0xFF21262D)
private val CopyIconColor  = Color(0xFF8B949E)

private val MonoFont = FontFamily.Monospace

private val LANGUAGE_DISPLAY = mapOf(
    "py"         to "Python",
    "python"     to "Python",
    "kt"         to "Kotlin",
    "kotlin"     to "Kotlin",
    "js"         to "JavaScript",
    "javascript" to "JavaScript",
    "ts"         to "TypeScript",
    "typescript" to "TypeScript",
    "jsx"        to "JSX",
    "tsx"        to "TSX",
    "java"       to "Java",
    "cpp"        to "C++",
    "c"          to "C",
    "cs"         to "C#",
    "go"         to "Go",
    "rs"         to "Rust",
    "sh"         to "Shell",
    "bash"       to "Bash",
    "zsh"        to "Zsh",
    "json"       to "JSON",
    "xml"        to "XML",
    "html"       to "HTML",
    "css"        to "CSS",
    "sql"        to "SQL",
    "yaml"       to "YAML",
    "yml"        to "YAML",
    "md"         to "Markdown",
    "swift"      to "Swift",
    "dart"       to "Dart",
    ""           to "Code",
)

// ── Fenced code block ──────────────────────────────────────────────────────
@Composable
fun CodeBlockView(language: String, code: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    val copyIconTint by animateColorAsState(
        targetValue = if (copied) WarmOrange else CopyIconColor,
        animationSpec = tween(300),
        label = "copyTint"
    )

    val displayLang = LANGUAGE_DISPLAY[language.lowercase()] ?: language.uppercase().ifEmpty { "Code" }
    val spans = remember(code, language) { SyntaxHighlighter.tokenize(code, language) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CodeBg)
            .padding(bottom = 2.dp)
    ) {
        // ── Header bar ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CodeSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Language badge
            Surface(
                color = LangBadgeBg,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = displayLang,
                    color = CodeComment,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = MonoFont,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Copy button
            IconButton(
                onClick = {
                    copyToClipboard(ctx, code, displayLang)
                    copied = true
                    scope.launch {
                        delay(1500)
                        copied = false
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy $displayLang code",
                    tint = copyIconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (copied) {
                Text(
                    text = "Copied!",
                    color = WarmOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }

        // ── Code body ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = buildSyntaxAnnotatedString(code, spans),
                fontFamily = MonoFont,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = CodeDefault
            )
        }
    }
}

// ── Inline code chip ───────────────────────────────────────────────────────
@Composable
fun InlineCodeChip(code: String) {
    Surface(
        color = CodeBg,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = code,
            fontFamily = MonoFont,
            fontSize = 13.sp,
            color = CodeString,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────
private fun copyToClipboard(context: Context, code: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("$label code", code))
    // Only show toast on Android < 13 (Android 13+ shows its own clipboard notification)
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}

private fun buildSyntaxAnnotatedString(
    code: String,
    spans: List<SyntaxHighlighter.TokenSpan>
) = buildAnnotatedString {
    var cursor = 0

    fun appendRange(start: Int, end: Int, color: Color) {
        if (start >= end) return
        withStyle(SpanStyle(color = color)) {
            append(code.substring(start, end.coerceAtMost(code.length)))
        }
    }

    fun appendDefault(start: Int, end: Int) = appendRange(start, end, CodeDefault)

    for (span in spans) {
        if (span.start > cursor) appendDefault(cursor, span.start)
        val color = when (span.type) {
            TokenType.Keyword  -> CodeKeyword
            TokenType.String   -> CodeString
            TokenType.Number   -> CodeNumber
            TokenType.Comment  -> CodeComment
            TokenType.Operator -> CodeDefault.copy(alpha = 0.7f)
            TokenType.Default  -> CodeDefault
        }
        appendRange(span.start, span.end, color)
        cursor = span.end
    }

    if (cursor < code.length) appendDefault(cursor, code.length)
}
