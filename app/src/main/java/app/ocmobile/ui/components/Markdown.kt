package app.ocmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.OcColors

private sealed interface Block {
    data class Para(val text: String) : Block
    data class Head(val level: Int, val text: String) : Block
    data class Code(val lang: String, val code: String) : Block
    data class Item(val marker: String, val indent: Int, val text: String) : Block
    data class Quote(val text: String) : Block
    data class Table(val rows: List<List<String>>) : Block
    data object Rule : Block
}

private val fenceRe = Regex("^\\s*(```|~~~)\\s*([\\w+#.-]*)")
private val headRe = Regex("^(#{1,6})\\s+(.*)$")
private val itemRe = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")
private val ruleRe = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
private val tableSepRe = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

private fun parseBlocks(src: String): List<Block> {
    val lines = src.replace("\r", "").split('\n')
    val out = ArrayList<Block>()
    var i = 0
    val para = StringBuilder()
    fun flush() { if (para.isNotBlank()) out += Block.Para(para.toString().trimEnd()); para.setLength(0) }
    while (i < lines.size) {
        val line = lines[i]
        val fence = fenceRe.find(line)
        if (fence != null) {
            flush()
            val marker = fence.groupValues[1]
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith(marker)) { code.appendLine(lines[i]); i++ }
            out += Block.Code(fence.groupValues[2], code.toString().trimEnd('\n'))
            i++
            continue
        }
        val head = headRe.find(line)
        val item = itemRe.find(line)
        when {
            line.isBlank() -> flush()
            head != null -> { flush(); out += Block.Head(head.groupValues[1].length, head.groupValues[2].trim()) }
            ruleRe.matches(line) -> { flush(); out += Block.Rule }
            line.trimStart().startsWith(">") -> {
                flush()
                val q = StringBuilder()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) { q.appendLine(lines[i].trimStart().removePrefix(">").trimStart()); i++ }
                out += Block.Quote(q.toString().trimEnd()); continue
            }
            line.contains('|') && i + 1 < lines.size && tableSepRe.matches(lines[i + 1]) -> {
                flush()
                val rows = ArrayList<List<String>>()
                fun cells(l: String) = l.trim().trim('|').split('|').map { it.trim() }
                rows += cells(line); i += 2
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) { rows += cells(lines[i]); i++ }
                out += Block.Table(rows); continue
            }
            item != null -> { flush(); out += Block.Item(item.groupValues[2], item.groupValues[1].length / 2, item.groupValues[3]) }
            else -> { if (para.isNotEmpty()) para.append('\n'); para.append(line) }
        }
        i++
    }
    flush()
    return out
}

private val linkRe = Regex("^\\[([^\\]]+)]\\(([^)\\s]+)\\)")

private fun AnnotatedString.Builder.inline(s: String, c: OcColors, mono: FontFamily) {
    var i = 0
    val plain = StringBuilder()
    fun flush() { if (plain.isNotEmpty()) { append(plain.toString()); plain.setLength(0) } }
    while (i < s.length) {
        val ch = s[i]
        when {
            ch == '`' -> {
                val end = s.indexOf('`', i + 1)
                if (end > i) {
                    flush()
                    withStyle(SpanStyle(fontFamily = mono, color = c.link, background = c.surfaceHigh, fontSize = 0.92f.em())) { append(s.substring(i + 1, end)) }
                    i = end + 1; continue
                }
            }
            s.startsWith("**", i) || s.startsWith("__", i) -> {
                val end = s.indexOf(s.substring(i, i + 2), i + 2)
                if (end > i + 2) {
                    flush()
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { inline(s.substring(i + 2, end), c, mono) }
                    i = end + 2; continue
                }
            }
            s.startsWith("~~", i) -> {
                val end = s.indexOf("~~", i + 2)
                if (end > i + 2) {
                    flush()
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { inline(s.substring(i + 2, end), c, mono) }
                    i = end + 2; continue
                }
            }
            (ch == '*' || ch == '_') && i + 1 < s.length && !s[i + 1].isWhitespace() && (i == 0 || !s[i - 1].isLetterOrDigit()) -> {
                val end = s.indexOf(ch, i + 1)
                if (end > i + 1 && !s[end - 1].isWhitespace()) {
                    flush()
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { inline(s.substring(i + 1, end), c, mono) }
                    i = end + 1; continue
                }
            }
            ch == '[' -> {
                val m = linkRe.find(s.substring(i))
                if (m != null) {
                    flush()
                    withLink(LinkAnnotation.Url(m.groupValues[2], TextLinkStyles(SpanStyle(color = c.link, textDecoration = TextDecoration.Underline)))) { append(m.groupValues[1]) }
                    i += m.value.length; continue
                }
            }
            s.startsWith("http://", i) || s.startsWith("https://", i) -> {
                var end = i
                while (end < s.length && !s[end].isWhitespace() && s[end] != ')' && s[end] != '>') end++
                val url = s.substring(i, end).trimEnd('.', ',', ';', ':')
                flush()
                withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = c.link, textDecoration = TextDecoration.Underline)))) { append(url) }
                i += url.length; continue
            }
        }
        plain.append(ch); i++
    }
    flush()
}

private fun Float.em() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Em)

@Composable
fun Markdown(text: String, modifier: Modifier = Modifier, serif: Boolean = Graph.prefs.fontStyle == "default") {
    val c = Oc.c
    val mono = Oc.mono
    val blocks = remember(text) { parseBlocks(text) }
    val family = if (serif) FontFamily.Serif else FontFamily.Default
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Block.Para -> Text(remember(b.text, c) { buildAnnotatedString { inline(b.text, c, mono) } }, color = c.text, fontSize = 18.sp, lineHeight = 28.sp, fontFamily = family)
                is Block.Head -> Text(
                    remember(b.text, c) { buildAnnotatedString { inline(b.text, c, mono) } }, color = c.text,
                    fontSize = when (b.level) { 1 -> 24.sp; 2 -> 21.sp; else -> 18.sp }, fontWeight = FontWeight.SemiBold, fontFamily = family,
                    modifier = Modifier.padding(top = 6.dp),
                )
                is Block.Code -> CodeBlock(b.code, b.lang)
                is Block.Item -> Row(Modifier.padding(start = (b.indent * 18).dp)) {
                    Text(if (b.marker[0].isDigit()) b.marker else "•", color = c.textDim, fontSize = 18.sp, fontFamily = family, modifier = Modifier.width(26.dp))
                    Text(remember(b.text, c) { buildAnnotatedString { inline(b.text, c, mono) } }, color = c.text, fontSize = 18.sp, lineHeight = 27.sp, fontFamily = family, modifier = Modifier.weight(1f))
                }
                is Block.Quote -> Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.width(3.dp).height(24.dp).background(c.border))
                    Spacer(Modifier.width(12.dp))
                    Text(remember(b.text, c) { buildAnnotatedString { inline(b.text, c, mono) } }, color = c.textDim, fontSize = 18.sp, lineHeight = 27.sp, fontFamily = family)
                }
                is Block.Table -> TableBlock(b.rows)
                Block.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            }
        }
    }
}

@Composable
fun CodeBlock(code: String, lang: String = "", modifier: Modifier = Modifier) {
    val c = Oc.c
    val clip = LocalClipboardManager.current
    val wrap = Graph.prefs.wrapLines
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.codeBg)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(lang.ifBlank { "code" }, size = 13.sp, color = c.textDim, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            IconBtn(Ic.Copy, { clip.setText(AnnotatedString(code)) }, size = 40.dp, iconSize = 18.dp, tint = c.textDim)
        }
        val body = @Composable {
            Text(code, color = c.text, fontFamily = Oc.mono, fontSize = 13.sp, lineHeight = 19.sp, softWrap = wrap, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
        }
        if (wrap) body() else Box(Modifier.horizontalScroll(rememberScrollState())) { body() }
    }
}

@Composable
private fun TableBlock(rows: List<List<String>>) {
    val c = Oc.c
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).horizontalScroll(rememberScrollState())) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEachIndexed { r, cells ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    cells.forEach { cell -> Txt(cell, size = 15.sp, weight = if (r == 0) FontWeight.SemiBold else null, modifier = Modifier.width(120.dp)) }
                }
            }
        }
    }
}
