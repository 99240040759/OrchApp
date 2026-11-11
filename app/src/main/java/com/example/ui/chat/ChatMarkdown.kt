package com.example.ui.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes

@Composable
fun ChatMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = OrchTextPrimary
) {
    val state = rememberMarkdownState(text, retainState = true)
    val body = MaterialTheme.typography.bodyLarge.copy(color = color)
    val heading = { size: Int -> TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = (size + 6).sp, color = color) }
    val mono = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.5.sp, lineHeight = 18.sp)

    val components = remember {
        markdownComponents(
            codeFence = {
                val highlights = remember { Highlights.Builder().theme(SyntaxThemes.default(darkMode = true)) }
                MarkdownHighlightedCodeFence(it.content, it.node, it.typography.code, highlights, showHeader = true)
            },
            codeBlock = {
                val highlights = remember { Highlights.Builder().theme(SyntaxThemes.default(darkMode = true)) }
                MarkdownHighlightedCodeBlock(it.content, it.node, it.typography.code, highlights)
            },
            checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) }
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
            h1 = heading(22),
            h2 = heading(20),
            h3 = heading(18),
            h4 = heading(16),
            h5 = heading(15),
            h6 = heading(14),
            text = body,
            code = mono.copy(color = color),
            inlineCode = body.copy(fontFamily = JetBrainsMonoFontFamily, fontSize = 13.5.sp),
            quote = body.copy(color = color.copy(alpha = 0.75f)),
            paragraph = body,
            ordered = body,
            bullet = body,
            list = body,
            textLink = TextLinkStyles(
                style = SpanStyle(color = OrchAccentCoral, textDecoration = TextDecoration.Underline)
            ),
            table = body.copy(fontSize = 13.sp)
        ),
        components = components,
        modifier = modifier.fillMaxWidth()
    )
}
