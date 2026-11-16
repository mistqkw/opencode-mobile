package app.ocmobile.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.OcColors
import kotlinx.coroutines.delay

/**
 * Code in replies and in the agent's steps: a card with the language (or file) on top, line numbers
 * down the side and colors for keywords, strings, comments and numbers, all from the theme.
 */
@Composable
fun CodeBlock(code: String, lang: String = "", modifier: Modifier = Modifier, file: String? = null) {
    val c = Oc.c
    val wrap = Graph.prefs.wrapLines
    val text = code.trimEnd('\n')
    val lines = remember(text) { text.lines() }
    // long code opens folded: the first lines and a row to show the rest
    var all by remember(text) { mutableStateOf(lines.size <= FOLD_AT) }
    val shown = if (all) text else lines.take(FOLD_KEEP).joinToString("\n")
    val shape = Oc.round(16.dp)
    val styled = remember(shown, lang, c) { highlight(shown, lang, c) }
    Column(modifier.fillMaxWidth().clip(shape).background(c.codeBg).border(1.dp, c.border.copy(alpha = 0.6f), shape)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(file?.substringAfterLast('/') ?: lang.ifBlank { "code" }, size = 13.sp, color = c.textDim, family = Oc.mono, maxLines = 1, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            if (lines.size > 1) Txt("${lines.size} lines", size = 12.sp, color = c.textDim.copy(alpha = 0.7f), modifier = Modifier.padding(end = 4.dp))
            CopyButton(text)
            CodeMore(text, lang, file)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border.copy(alpha = 0.5f)))
        Row(Modifier.padding(top = 10.dp, bottom = if (all) 12.dp else 4.dp)) {
            // numbers stay put while the code scrolls sideways
            if (lines.size > 1) Text(
                (1..shown.lines().size).joinToString("\n"), color = c.textDim.copy(alpha = 0.45f), fontFamily = Oc.mono, fontSize = 12.sp, lineHeight = 19.sp,
                textAlign = TextAlign.End, softWrap = false, modifier = Modifier.padding(start = 10.dp, end = 10.dp),
            )
            val body = @Composable {
                Text(styled, color = c.text, fontFamily = Oc.mono, fontSize = 13.sp, lineHeight = 19.sp, softWrap = wrap,
                    modifier = Modifier.padding(start = if (lines.size > 1) 0.dp else 14.dp, end = 14.dp))
            }
            if (wrap) Box(Modifier.weight(1f)) { body() } else Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) { body() }
        }
        if (!all) Row(
            Modifier.fillMaxWidth().tap(scale = 1f) { all = true }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Txt("Show all ${lines.size} lines", size = 13.sp, color = c.textDim)
            Spacer(Modifier.width(6.dp))
            Ico(Ic.ChevronDown, tint = c.textDim, size = 16.dp)
        }
    }
}

/** "⋯" on a code block: save it as a file, or open a web page in the preview. */
@Composable
private fun CodeMore(code: String, lang: String, file: String?) {
    val c = Oc.c
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var open by remember { mutableStateOf(false) }
    val ext = file?.substringAfterLast('.', "")?.ifBlank { null } ?: EXT[lang.lowercase()] ?: lang.lowercase().ifBlank { "txt" }
    val save = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(code.toByteArray()) } }
            .onSuccess { Graph.chats.toasts.tryEmit("Saved") }.onFailure { Graph.chats.toasts.tryEmit("Couldn't save the file") }
    }
    val web = ext in setOf("html", "htm", "svg")
    Box {
        Box(Modifier.size(40.dp).clip(Oc.pill).tap(scale = 0.9f) { open = true }, contentAlignment = Alignment.Center) {
            Ico(Ic.More, tint = c.textDim, size = 18.dp, contentDescription = "More")
        }
        if (open) androidx.compose.ui.window.Popup(alignment = Alignment.TopEnd, offset = androidx.compose.ui.unit.IntOffset(0, 110), onDismissRequest = { open = false },
            properties = androidx.compose.ui.window.PopupProperties(focusable = true)) {
            val shape = RoundedCornerShape(18.dp)
            Column(Modifier.width(220.dp).clip(shape).background(c.surface.copy(alpha = 0.94f)).border(1.dp, c.border, shape).padding(vertical = 6.dp)) {
                @Composable fun item(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, act: () -> Unit) = Row(
                    Modifier.fillMaxWidth().tap(scale = 1f) { open = false; act() }.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Ico(icon, tint = c.text, size = 20.dp); Spacer(Modifier.width(16.dp)); Txt(label, size = 16.sp) }
                item(Ic.Download, "Save as file") { save.launch(file?.substringAfterLast('/') ?: "snippet.$ext") }
                if (web) item(Ic.Eye, "Open in preview") {
                    val b64 = android.util.Base64.encodeToString(code.toByteArray(), android.util.Base64.NO_WRAP)
                    Graph.chats.openRequest.value = "route:preview:data:" + (if (ext == "svg") "image/svg+xml" else "text/html") + ";base64," + b64
                }
                item(Ic.Share, "Share") {
                    val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, code)
                    ctx.startActivity(android.content.Intent.createChooser(i, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}

private val EXT = mapOf(
    "kotlin" to "kt", "python" to "py", "javascript" to "js", "typescript" to "ts", "bash" to "sh", "shell" to "sh", "ruby" to "rb",
    "rust" to "rs", "markdown" to "md", "yaml" to "yml", "csharp" to "cs", "c++" to "cpp", "golang" to "go",
)

private const val FOLD_AT = 40
private const val FOLD_KEEP = 24

/**
 * Copy that answers in place: the icon turns into a check with a short tick under the finger and turns
 * back after a moment. No toast on top of it (Android 13+ shows its own clipboard note already).
 */
@Composable
fun CopyButton(text: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 40.dp, iconSize: androidx.compose.ui.unit.Dp = 18.dp, tint: Color = Oc.c.textDim) {
    val clip = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done) { delay(1400); done = false } }
    Box(
        modifier.size(size).clip(Oc.pill).tap(scale = 0.9f) {
            clip.setText(AnnotatedString(text)); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); done = true
            copiedNote()
        },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(done, label = "copy", transitionSpec = {
            (fadeIn(tween(90)) + scaleIn(tween(140), initialScale = 0.6f)) togetherWith (fadeOut(tween(70)) + scaleOut(tween(90), targetScale = 0.6f))
        }) { d -> Ico(if (d) Ic.Check else Ic.Copy, tint = if (d) Oc.c.ok else tint, size = iconSize, contentDescription = if (d) "Copied" else "Copy") }
    }
}

/** Asks a screen to open something it owns (the chat opens its model picker when [pickModel] grows). */
object UiRequests {
    var pickModel by mutableStateOf(0)
}

/** Android 13+ confirms a copy itself; before that a toast does, so the two never show at once. */
fun copiedNote() { if (android.os.Build.VERSION.SDK_INT < 33) Graph.chats.toasts.tryEmit("Copied") }

/** Colors for code, picked from the theme so it fits every look, light and dark. */
private class CodePalette(c: OcColors) {
    val keyword = c.accent
    val string = lerp(c.ok, c.text, 0.25f)
    val number = lerp(c.warn, c.text, 0.2f)
    val comment = c.textDim.copy(alpha = 0.75f)
    val call = lerp(c.link, c.text, 0.35f)
    val type = lerp(c.accent, c.text, 0.45f)
}

private val KEYWORDS = setOf(
    // shared by most languages people ask about
    "if", "else", "for", "while", "do", "return", "break", "continue", "switch", "case", "default", "try", "catch", "finally", "throw",
    "class", "interface", "enum", "struct", "new", "this", "self", "super", "import", "from", "export", "package", "as", "in", "is",
    "true", "false", "null", "nil", "None", "True", "False", "undefined", "void",
    // JS/TS
    "const", "let", "var", "function", "async", "await", "yield", "typeof", "instanceof", "extends", "implements", "static", "public", "private", "protected", "readonly", "type",
    // Kotlin/Java/Swift
    "fun", "val", "when", "object", "data", "sealed", "override", "open", "internal", "companion", "lateinit", "final", "abstract", "func", "guard", "init", "where",
    // Python/Ruby/Rust/Go
    "def", "lambda", "pass", "with", "elif", "and", "or", "not", "end", "fn", "mut", "impl", "pub", "use", "mod", "match", "loop", "go", "defer", "chan", "range", "map",
    // shell
    "then", "fi", "esac", "done", "echo", "export", "local", "sudo",
)

private val TOKENS = Regex(
    """(//[^\n]*|#(?!include)[^\n]*|/\*[\s\S]*?\*/|--[^\n]*)""" +
        """|("(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|`(?:\\.|[^`\\])*`)""" +
        """|(\b\d[\d_]*(?:\.\d+)?(?:[eE][+-]?\d+)?\b|\b0x[0-9a-fA-F]+\b)""" +
        """|([A-Za-z_][A-Za-z0-9_]*)""",
)

/** Light highlighting by token, good enough for reading on a phone; plain text and logs stay plain. */
fun highlight(code: String, lang: String, c: OcColors): AnnotatedString {
    val l = lang.lowercase()
    if (l in setOf("text", "txt", "output", "log", "error", "plain")) return AnnotatedString(code)
    val p = CodePalette(c)
    // "#" starts a comment only where it does (shell, Python, Ruby, YAML...), not in C or CSS colors
    val hashComments = l in setOf("sh", "bash", "shell", "zsh", "py", "python", "rb", "ruby", "yaml", "yml", "toml", "r", "perl", "dockerfile", "makefile", "conf", "ini")
    val dashComments = l in setOf("sql", "lua", "haskell", "hs")
    return buildAnnotatedString {
        var at = 0
        for (m in TOKENS.findAll(code)) {
            val v = m.value
            val style = when {
                m.groups[1] != null -> when {
                    v.startsWith("#") && !hashComments -> null
                    v.startsWith("--") && !dashComments -> null
                    else -> SpanStyle(color = p.comment, fontStyle = FontStyle.Italic)
                }
                m.groups[2] != null -> SpanStyle(color = p.string)
                m.groups[3] != null -> SpanStyle(color = p.number)
                v in KEYWORDS -> SpanStyle(color = p.keyword)
                // a name followed by "(" is a call; a capitalized name is a type
                code.getOrNull(m.range.last + 1) == '(' -> SpanStyle(color = p.call)
                v.first().isUpperCase() && v.length > 1 && v.any { it.isLowerCase() } -> SpanStyle(color = p.type)
                else -> null
            }
            if (style == null) continue
            append(code.substring(at, m.range.first))
            withStyle(style) { append(v) }
            at = m.range.last + 1
        }
        append(code.substring(at))
    }
}

/**
 * A file edit as in a code review: the file and +added −removed on top, the old and new line numbers
 * down the side, added lines green and removed ones red, the code itself highlighted.
 */
@Composable
fun DiffView(diff: String, file: String? = null) {
    val c = Oc.c
    val rows = remember(diff) { parseDiff(diff) }
    val name = file ?: rows.firstNotNullOfOrNull { it.file }
    val lang = name?.substringAfterLast('.', "").orEmpty()
    val added = rows.count { it.kind == '+' }
    val removed = rows.count { it.kind == '-' }
    var all by remember(diff) { mutableStateOf(rows.size <= 80) }
    val shape = Oc.round(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.codeBg).border(1.dp, c.border.copy(alpha = 0.6f), shape)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(name?.substringAfterLast('/') ?: "changes", size = 13.sp, color = c.textDim, family = Oc.mono, maxLines = 1, modifier = Modifier.weight(1f, fill = false).padding(vertical = 10.dp))
            Spacer(Modifier.width(10.dp))
            if (added > 0) Txt("+$added", size = 13.sp, color = c.ok, family = Oc.mono)
            if (added > 0 && removed > 0) Spacer(Modifier.width(6.dp))
            if (removed > 0) Txt("−$removed", size = 13.sp, color = c.danger, family = Oc.mono)
            Spacer(Modifier.weight(1f))
            CopyButton(rows.filter { it.kind != '-' && it.kind != '@' }.joinToString("\n") { it.text })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border.copy(alpha = 0.5f)))
        val shown = if (all) rows else rows.take(60)
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column(Modifier.padding(vertical = 6.dp)) {
                shown.forEach { r ->
                    if (r.kind == '@') {
                        Text("⋯", color = c.textDim.copy(alpha = 0.6f), fontFamily = Oc.mono, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 2.dp))
                        return@forEach
                    }
                    val (bg, mark) = when (r.kind) {
                        '+' -> c.ok.copy(alpha = .13f) to c.ok
                        '-' -> c.danger.copy(alpha = .13f) to c.danger
                        else -> Color.Transparent to c.textDim
                    }
                    Row(Modifier.background(bg)) {
                        Text(r.num?.toString().orEmpty(), color = c.textDim.copy(alpha = 0.45f), fontFamily = Oc.mono, fontSize = 12.sp, lineHeight = 19.sp,
                            textAlign = TextAlign.End, softWrap = false, modifier = Modifier.width(36.dp).padding(end = 6.dp))
                        Text(if (r.kind == ' ') " " else r.kind.toString().replace('-', '−'), color = mark, fontFamily = Oc.mono, fontSize = 12.sp, lineHeight = 19.sp, softWrap = false, modifier = Modifier.width(14.dp))
                        Text(remember(r.text, lang, c) { highlight(r.text, lang.ifBlank { "code" }, c) }, color = c.text, fontFamily = Oc.mono, fontSize = 12.sp, lineHeight = 19.sp, softWrap = false, modifier = Modifier.padding(end = 14.dp))
                    }
                }
            }
        }
        if (!all) Row(
            Modifier.fillMaxWidth().tap(scale = 1f) { all = true }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Txt("Show all ${rows.size} lines", size = 13.sp, color = c.textDim)
            Spacer(Modifier.width(6.dp))
            Ico(Ic.ChevronDown, tint = c.textDim, size = 16.dp)
        }
    }
}

/** One line of a unified diff: '+', '-', ' ' or '@' (a gap between hunks), with the line number in the new file (old for removed). */
private class DiffRow(val kind: Char, val text: String, val num: Int?, val file: String? = null)

private fun parseDiff(diff: String): List<DiffRow> {
    val out = ArrayList<DiffRow>()
    var old = 0; var new = 0
    var file: String? = null
    val hunk = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")
    diff.lineSequence().take(2000).forEach { l ->
        when {
            l.startsWith("+++ ") -> file = l.removePrefix("+++ ").removePrefix("b/").trim().takeIf { it != "/dev/null" } ?: file
            l.startsWith("--- ") || l.startsWith("diff ") || l.startsWith("index ") || l.startsWith("Index:") || l.startsWith("====") -> {}
            l.startsWith("@@") -> {
                hunk.find(l)?.let { old = it.groupValues[1].toInt(); new = it.groupValues[2].toInt() }
                if (out.isNotEmpty()) out.add(DiffRow('@', "", null))
            }
            l.startsWith("+") -> out.add(DiffRow('+', l.drop(1), new++, file))
            l.startsWith("-") -> out.add(DiffRow('-', l.drop(1), old++, file))
            l.startsWith("\\") -> {}
            else -> { out.add(DiffRow(' ', l.removePrefix(" "), new++, file)); old++ }
        }
    }
    return out
}
