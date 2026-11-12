package com.example.ui.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OrchAccentCoral
import com.example.ui.theme.OrchBorder
import com.example.ui.theme.OrchSurface
import com.example.ui.theme.OrchSurfaceElevated
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.SpaceGroteskFontFamily
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState

private val SharedLinkStyles = TextLinkStyles(
    style = SpanStyle(color = OrchAccentCoral, textDecoration = TextDecoration.Underline)
)

@Immutable
private data class CachedMarkdownStyles(
    val h1: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val h4: TextStyle,
    val h5: TextStyle,
    val h6: TextStyle,
    val body: TextStyle,
    val mono: TextStyle,
    val inlineCode: TextStyle,
    val quote: TextStyle,
    val table: TextStyle
)

@Composable
fun ChatMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = OrchTextPrimary,
    compact: Boolean = false,
    streaming: Boolean = false
) {
    val immediate = remember { !streaming }
    val state = rememberMarkdownState(text, retainState = true, immediate = immediate)
    val baseBodyLarge = MaterialTheme.typography.bodyLarge
    val baseBodyMedium = MaterialTheme.typography.bodyMedium
    val baseLabelMedium = MaterialTheme.typography.labelMedium

    val styles = remember(color, compact, baseBodyLarge, baseBodyMedium, baseLabelMedium) {
        val body = if (compact) {
            baseBodyMedium.copy(fontSize = 12.5.sp, lineHeight = 18.sp, color = color)
        } else {
            baseBodyLarge.copy(color = color)
        }
        val heading = { size: Int ->
            val scaled = if (compact) (size - 4).coerceAtLeast(12) else size
            TextStyle(
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = scaled.sp,
                lineHeight = (scaled + 5).sp,
                color = color
            )
        }
        val mono = baseLabelMedium.copy(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = if (compact) 11.5.sp else 12.5.sp,
            lineHeight = if (compact) 16.sp else 18.sp,
            color = color
        )
        CachedMarkdownStyles(
            h1 = heading(22),
            h2 = heading(20),
            h3 = heading(18),
            h4 = heading(16),
            h5 = heading(15),
            h6 = heading(14),
            body = body,
            mono = mono,
            inlineCode = body.copy(fontFamily = JetBrainsMonoFontFamily, fontSize = if (compact) 12.sp else 13.5.sp),
            quote = body.copy(color = color.copy(alpha = 0.75f)),
            table = body.copy(fontSize = if (compact) 12.sp else 13.sp)
        )
    }

    Markdown(
        markdownState = state,
        colors = markdownColor(
            text = color,
            codeBackground = OrchSurface,
            inlineCodeBackground = OrchSurfaceElevated,
            dividerColor = OrchBorder,
            tableBackground = OrchSurface
        ),
        typography = markdownTypography(
            h1 = styles.h1,
            h2 = styles.h2,
            h3 = styles.h3,
            h4 = styles.h4,
            h5 = styles.h5,
            h6 = styles.h6,
            text = styles.body,
            code = styles.mono,
            inlineCode = styles.inlineCode,
            quote = styles.quote,
            paragraph = styles.body,
            ordered = styles.body,
            bullet = styles.body,
            list = styles.body,
            textLink = SharedLinkStyles,
            table = styles.table
        ),
        modifier = modifier.fillMaxWidth()
    )
}
