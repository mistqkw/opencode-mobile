package app.ocmobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import androidx.compose.runtime.collectAsState
import app.ocmobile.api.Part
import app.ocmobile.api.PermissionReq
import app.ocmobile.api.QuestionReq
import app.ocmobile.api.arr
import app.ocmobile.api.obj
import app.ocmobile.api.str
import app.ocmobile.data.ChatMessage
import app.ocmobile.ui.components.CodeBlock
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.ImageThumb
import app.ocmobile.ui.components.Markdown
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.SmallButton
import app.ocmobile.ui.components.longPress
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

fun hhmm(ms: Long): String = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ms))

/** Пузырь с хвостиком, как в мессенджерах: скругления 16dp, у нижнего угла — маленький загнутый хвост. */
class TailShape(private val right: Boolean) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val r = with(density) { 16.dp.toPx() }; val t = with(density) { 7.dp.toPx() }
        val w = size.width; val h = size.height
        val p = androidx.compose.ui.graphics.Path()
        if (right) {
            val bw = w - t
            p.moveTo(r, 0f); p.lineTo(bw - r, 0f); p.quadraticTo(bw, 0f, bw, r)
            p.lineTo(bw, h - t * 1.6f); p.quadraticTo(bw, h - 1f, w, h - 1f); p.quadraticTo(bw - t * 0.4f, h, bw - t * 1.6f, h)
            p.lineTo(r, h); p.quadraticTo(0f, h, 0f, h - r); p.lineTo(0f, r); p.quadraticTo(0f, 0f, r, 0f)
        } else {
            val bx = t
            p.moveTo(bx + r, 0f); p.lineTo(w - r, 0f); p.quadraticTo(w, 0f, w, r); p.lineTo(w, h - r); p.quadraticTo(w, h, w - r, h)
            p.lineTo(bx + t * 1.6f, h); p.quadraticTo(bx + t * 0.4f - t, h, 0f, h - 1f); p.quadraticTo(bx, h - 1f, bx, h - t * 1.6f)
            p.lineTo(bx, r); p.quadraticTo(bx, 0f, bx + r, 0f)
        }
        p.close()
        return androidx.compose.ui.graphics.Outline.Generic(p)
    }
}

fun isImagePart(p: Part) = p.type == "file" && p.raw.str("mime")?.startsWith("image/") == true

@Composable
fun UserBubble(m: ChatMessage, onImage: (String) -> Unit, onSelect: () -> Unit, onEdit: () -> Unit) {
    val c = Oc.c
    var menu by remember { mutableStateOf(false) }
    val onLong = { menu = true }
    val clip = LocalClipboardManager.current
    val images = m.parts.filter(::isImagePart)
    val files = m.parts.filter { it.type == "file" && !isImagePart(it) }.map { (it.raw.str("filename") ?: "file") to null as String? } +
        m.parts.filter { it.type == "text" }.mapNotNull { app.ocmobile.data.Attachments.parseAttached(it.text) }
    val text = m.parts.filter { it.type == "text" && it.raw.str("synthetic") != "true" }.joinToString("\n") { it.text }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.End) {
        if (images.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                images.forEach { p -> p.raw.str("url")?.let { u -> ImageThumb(u, 132.dp, { onImage(u) }) } }
            }
        }
        files.forEach { (name, path) ->
            val host = path?.let { app.ocmobile.ui.components.OutFiles.host(it) }
            Row(
                Modifier.padding(bottom = 8.dp).clip(Oc.round(14.dp)).background(c.userBubble)
                    .then(if (host != null) Modifier.clickable { app.ocmobile.ui.components.OutFiles.open(ctx, host) } else Modifier)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(Ic.File, tint = c.textDim, size = 20.dp); Spacer(Modifier.width(8.dp)); Txt(name, size = 15.sp, maxLines = 1)
                host?.let { Spacer(Modifier.width(8.dp)); Txt(app.ocmobile.data.Attachments.human(it.length()), size = 13.sp, color = c.textDim) }
            }
        }
        if (text.isNotBlank()) {
            if (Oc.terminal) {
                // как в TUI: тёмный блок на всю ширину с синей полосой слева
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).background(c.userBubble).longPress(onLong)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(c.link))
                    Text(text, color = c.text, fontSize = 15.sp, lineHeight = 22.sp, fontFamily = Oc.body, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
                }
            } else when (Oc.look.user) {
                // Messenger: пузырь с хвостиком и временем внутри
                "tail" -> Box(Modifier.widthIn85().clip(TailShape(right = true)).background(c.userBubble).longPress(onLong).padding(start = 14.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text, color = c.text, fontSize = 16.sp, lineHeight = 22.sp, fontFamily = Oc.body)
                        Txt(hhmm(m.info.time.created), size = 11.sp, color = c.text.copy(alpha = 0.55f))
                    }
                }
                // Notebook: без пузыря — подпись и текст с полосой слева
                "plain" -> Column(Modifier.fillMaxWidth().longPress(onLong)) {
                    Txt("YOU · " + hhmm(m.info.time.created), size = 11.sp, color = c.textDim, family = Oc.label)
                    Row(Modifier.padding(top = 4.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                        Box(Modifier.width(2.dp).fillMaxHeight().background(c.accent)); Spacer(Modifier.width(12.dp))
                        Text(text, color = c.text, fontSize = 17.sp, lineHeight = 25.sp, fontFamily = Oc.body, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                }
                // Cards: карточка с шапкой
                "card" -> Column(Modifier.widthIn85().clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp)).longPress(onLong).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent)); Spacer(Modifier.width(8.dp))
                        Txt("You", size = 13.sp, color = c.textDim, weight = FontWeight.Medium); Spacer(Modifier.width(6.dp)); Txt(hhmm(m.info.time.created), size = 12.sp, color = c.textDim)
                    }
                    Text(text, color = c.text, fontSize = 16.sp, lineHeight = 23.sp, fontFamily = Oc.body, modifier = Modifier.padding(top = 6.dp))
                }
                // Hairline: рамка в 1px, без заливки
                "line" -> Box(Modifier.widthIn85().clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp)).longPress(onLong).padding(horizontal = 16.dp, vertical = 11.dp)) {
                    Text(text, color = c.text, fontSize = 16.sp, lineHeight = 23.sp, fontFamily = Oc.body)
                }
                else -> Box(Modifier.widthIn85().clip(Oc.round(22.dp)).background(c.userBubble).longPress(onLong).padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text(text, color = c.text, fontSize = 16.sp, lineHeight = 23.sp, fontFamily = Oc.body)
                }
            }
            Box {
                androidx.compose.material3.DropdownMenu(menu, { menu = false }, containerColor = c.surface, shape = Oc.round(16.dp), offset = androidx.compose.ui.unit.DpOffset(0.dp, 4.dp)) {
                    Txt(java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(m.info.time.created)), size = 13.sp, color = c.textDim, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                    @Composable fun row(icon: ImageVector, label: String, act: () -> Unit) = androidx.compose.material3.DropdownMenuItem(
                        text = { Txt(label, size = 16.sp) }, leadingIcon = { Ico(icon, tint = c.textDim, size = 20.dp) }, onClick = { menu = false; act() },
                    )
                    row(Ic.Copy, "Copy message") { clip.setText(AnnotatedString(text)); Graph.chats.toasts.tryEmit("Copied") }
                    row(Ic.TextSelect, "Select text", onSelect)
                    row(Ic.Edit, "Edit", onEdit)
                }
            }
        }
    }
}

@Composable
private fun Modifier.widthIn85(): Modifier {
    val w = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp * 0.88f
    return this.then(Modifier.widthIn(max = w))
}

@Composable
fun AssistantBlock(m: ChatMessage, last: Boolean, onImage: (String) -> Unit, onLong: () -> Unit, onRetry: (() -> Unit)?, first: Boolean = true) {
    val c = Oc.c
    val clip = LocalClipboardManager.current
    val info = m.info
    val look = Oc.look
    val frame = when (look.reply) {
        "bubble" -> Modifier.padding(start = 10.dp, end = 40.dp).clip(TailShape(right = false)).background(c.surface).padding(start = 20.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)
        "card" -> Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp)).padding(14.dp)
        else -> Modifier.fillMaxWidth().padding(horizontal = 20.dp)
    }
    val textAll = m.parts.filter { it.type == "text" }.joinToString("\n\n") { it.text }
    val boxed = look.reply != "plain"
    @Composable fun Actions() {
        if (info.time.completed != null && info.errorMessage == null && (textAll.isNotBlank() || last)) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (textAll.isNotBlank()) {
                    IconBtn(Ic.Copy, { clip.setText(AnnotatedString(textAll)); Graph.chats.toasts.tryEmit("Copied") }, size = 40.dp, iconSize = 20.dp, tint = c.textDim)
                    IconBtn(Ic.Share, {
                        val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, textAll)
                        ctx.startActivity(android.content.Intent.createChooser(i, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, size = 40.dp, iconSize = 20.dp, tint = c.textDim)
                    IconBtn(Ic.Volume, { Speaker.toggle(ctx, textAll) }, size = 40.dp, iconSize = 20.dp, tint = c.textDim)
                }
                if (onRetry != null) IconBtn(Ic.Refresh, onRetry, size = 40.dp, iconSize = 20.dp, tint = c.textDim)
                IconBtn(Ic.More, onLong, size = 40.dp, iconSize = 20.dp, tint = c.textDim)
                val bits = buildList {
                    info.modelName?.let { add(it) }
                    info.cost?.takeIf { it > 0 }?.let { add("$" + "%.4f".format(it).trimEnd('0').trimEnd('.')) }
                }
                if (bits.isNotEmpty()) Txt(bits.joinToString(" · "), size = 13.sp, color = c.textDim)
            }
            if (last) Txt("OpenCode can make mistakes. Check important changes.", size = 13.sp, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
        }
    }
    Column(Modifier.fillMaxWidth()) {
    Column(frame, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Hairline: моно-подпись с синим квадратом
        // подпись — только над первым сообщением ответа, а не над каждым шагом
        if (!first && look.reply == "plain") Unit
        else if (look.id == "hairline") Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(c.accent)); Spacer(Modifier.width(10.dp))
            Txt("OPENCODE · " + hhmm(info.time.created), size = 11.sp, color = c.textDim, family = Oc.label)
        } else if (look.reply == "card" || look.user == "plain") Row(verticalAlignment = Alignment.CenterVertically) {
            if (look.reply == "card") { Box(Modifier.size(6.dp).clip(CircleShape).background(c.link)); Spacer(Modifier.width(8.dp)) }
            Txt(if (look.user == "plain") "OPENCODE · " + hhmm(info.time.created) else "OpenCode", size = if (look.user == "plain") 11.sp else 13.sp, color = c.textDim, family = Oc.label, weight = if (look.reply == "card") FontWeight.Medium else null)
            if (look.reply == "card") { Spacer(Modifier.width(6.dp)); Txt(hhmm(info.time.created), size = 12.sp, color = c.textDim) }
        }
        // Как в Claude: подряд идущие шаги (мысли, команды, чтение) — одной строкой «Thought, ran 2 commands ›»
        val segments = remember(m.parts.size, m.parts.lastOrNull()?.raw) { segment(m.parts) }
        // сессия уже не работает (сервер остановили посреди шага) — не крутить значок вечно
        val busyNow = Graph.chats.statuses.collectAsState().value.containsKey(info.sessionID)
        val running = last && info.time.completed == null && busyNow
        segments.forEachIndexed { si, seg ->
            if (seg.size > 1 || (seg.size == 1 && isStep(seg[0]))) { StepsGroup(seg, running && si == segments.lastIndex, onImage); return@forEachIndexed }
            val p = seg[0]
            when (p.type) {
                "text" -> if (p.text.isNotBlank()) androidx.compose.foundation.text.selection.SelectionContainer { Markdown(p.text) }
                "reasoning" -> if (p.text.isNotBlank()) ReasoningBlock(p)
                "tool" -> ToolCard(p, onImage)
                "file" -> if (isImagePart(p)) p.raw.str("url")?.let { u -> ImageThumb(u, 220.dp, { onImage(u) }) }
                "subtask" -> InfoLine(Ic.Bot, "Subtask: " + (p.raw.str("description") ?: p.raw.str("prompt") ?: ""))
                "patch" -> InfoLine(Ic.Diff, "Patch · ${p.raw.arr("files")?.size ?: 0} files")
                "compaction" -> InfoLine(Ic.Sparkle, "Context compacted")
                "retry" -> InfoLine(Ic.Refresh, "Retrying…")
            }
        }
        val outFiles = remember(m.parts.size, m.parts.lastOrNull()?.text?.length, info.time.completed) {
            app.ocmobile.ui.components.OutFiles.find(m.parts.filter { it.type == "text" }.joinToString("\n") { it.text })
        }
        if (outFiles.isNotEmpty()) app.ocmobile.ui.components.FileCards(outFiles)
        info.errorMessage?.let { ErrorCard(info.errorName, it) }
        if (boxed) {
            if (look.reply == "bubble" && info.time.completed != null)
                Txt(hhmm(info.time.created), Modifier.align(Alignment.End).offset(y = (-4).dp), size = 12.sp, color = c.textDim)
        } else Actions()
    }
    // в пузыре/карточке кнопки и подпись — под рамкой, как реакции в мессенджере
    if (boxed) Column(Modifier.padding(start = if (look.reply == "bubble") 14.dp else 16.dp, end = 16.dp, top = 2.dp)) { Actions() }
    }
}

/** Озвучка ответа системным TextToSpeech: повторное нажатие останавливает чтение. */
object Speaker {
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ready = false
    private var speaking = false

    fun toggle(ctx: android.content.Context, text: String) {
        if (speaking) { tts?.stop(); speaking = false; return }
        val go = {
            val clean = text.replace(Regex("[`*#>_]"), "")
            tts?.language = app.ocmobile.data.Speech.ttsLocale(clean)
            tts?.speak(clean, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "oc")
            speaking = true
        }
        if (ready) go() else tts = android.speech.tts.TextToSpeech(ctx.applicationContext) { st ->
            if (st == android.speech.tts.TextToSpeech.SUCCESS) {
                ready = true
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { speaking = false }
                    @Deprecated("api") override fun onError(id: String?) { speaking = false }
                })
                go()
            }
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Ico(icon, tint = Oc.c.textDim, size = 18.dp); Spacer(Modifier.width(8.dp)); Txt(text, size = 14.sp, color = Oc.c.textDim)
    }
}

@Composable
private fun ErrorCard(name: String?, message: String) {
    val c = Oc.c
    Row(Modifier.fillMaxWidth().clip(Oc.round(18.dp)).background(c.danger.copy(alpha = .12f)).border(1.dp, c.danger.copy(alpha = .35f), Oc.round(18.dp)).padding(14.dp)) {
        Ico(Ic.Warning, tint = c.danger, size = 22.dp); Spacer(Modifier.width(12.dp))
        Column {
            if (name == "ProviderAuthError") Txt("Provider not connected", size = 15.sp, color = c.danger, weight = FontWeight.Medium)
            Txt(message, size = 15.sp, color = c.text)
        }
    }
}

@Composable
private fun ReasoningBlock(p: Part) {
    val c = Oc.c
    var open by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.clip(Oc.round(10.dp)).clickable { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Ic.Sparkle, tint = c.textDim, size = 16.dp); Spacer(Modifier.width(8.dp))
            Txt("Thinking", size = 14.sp, color = c.textDim)
            Spacer(Modifier.width(4.dp))
            Ico(if (open) Ic.ChevronDown else Ic.ChevronRight, tint = c.textDim, size = 16.dp)
        }
        AnimatedVisibility(open, enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)) + androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(180)), exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(80)) + androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(150))) {
            Row(Modifier.padding(top = 6.dp)) {
                Box(Modifier.width(2.dp).heightIn(min = 20.dp).background(c.border)); Spacer(Modifier.width(12.dp))
                Txt(p.text.trim(), size = 15.sp, color = c.textDim, lineHeight = 22.sp)
            }
        }
    }
}

// ---------- шаги ----------

/** Шаг — мысль или вызов инструмента (кроме вопроса и списка дел: они показываются отдельно). */
private fun isStep(p: Part): Boolean = when (p.type) {
    "reasoning" -> p.text.isNotBlank()
    "tool" -> p.raw.str("tool") !in setOf("question", "todowrite", "todoread")
    else -> false
}

/** Части ответа → группы: шаги подряд собираются вместе, остальное идёт по одному. */
private fun segment(parts: List<Part>): List<List<Part>> {
    val out = ArrayList<List<Part>>()
    var cur = ArrayList<Part>()
    for (p in parts) {
        if (isStep(p)) cur.add(p)
        else {
            if (cur.isNotEmpty()) { out.add(cur); cur = ArrayList() }
            if (p.type == "reasoning" && p.text.isBlank()) continue
            out.add(listOf(p))
        }
    }
    if (cur.isNotEmpty()) out.add(cur)
    return out
}

private fun stepsSummary(parts: List<Part>): String {
    var thought = false; var cmd = 0; var read = 0; var edit = 0; var search = 0; var web = 0; var other = 0; var failed = 0
    parts.forEach { p ->
        if (p.type == "reasoning") { thought = true; return@forEach }
        val t = p.raw.str("tool").orEmpty()
        if (p.raw.obj("state")?.str("status") == "error") failed++
        when {
            t == "bash" -> cmd++
            t == "read" -> read++
            t in setOf("edit", "write", "patch", "multiedit", "apply_patch") -> edit++
            t in setOf("grep", "glob", "list", "codesearch") -> search++
            t.startsWith("web") -> web++
            else -> other++
        }
    }
    fun n(k: Int, one: String, many: String) = if (k == 1) one else "$k $many"
    val bits = buildList {
        if (thought) add("thought")
        if (cmd > 0) add("ran " + n(cmd, "a command", "commands"))
        if (read > 0) add("read " + n(read, "a file", "files"))
        if (edit > 0) add("edited " + n(edit, "a file", "files"))
        if (search > 0) add("searched " + n(search, "once", "times"))
        if (web > 0) add("browsed " + n(web, "a page", "pages"))
        if (other > 0) add("used " + n(other, "a tool", "tools"))
    }
    return bits.joinToString(", ").replaceFirstChar { it.uppercase() } + if (failed > 0) " · $failed failed" else ""
}

@Composable
private fun StepsGroup(parts: List<Part>, running: Boolean, onImage: (String) -> Unit) {
    val c = Oc.c
    var open by remember { mutableStateOf(false) }
    val rot by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, androidx.compose.animation.core.tween(150), label = "steps")
    Column {
        Row(Modifier.clip(Oc.round(10.dp)).clickable { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.animation.AnimatedContent(stepsSummary(parts), label = "sum", transitionSpec = {
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(140)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(160)) { it / 2 }) togetherWith
                    (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)) + androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(120)) { -it / 2 })
            }) { t -> Txt(t, size = 15.sp, color = c.textDim, maxLines = 1) }
            Spacer(Modifier.width(4.dp))
            Ico(Ic.ChevronRight, tint = c.textDim, size = 16.dp, modifier = Modifier.rotate(rot))
            if (running) { Spacer(Modifier.width(8.dp)); app.ocmobile.ui.components.Spinner(12.dp, color = c.textDim, stroke = 1.5.dp) }
        }
        AnimatedVisibility(open, enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)) + androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(180)), exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(80)) + androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(150))) {
            Row(Modifier.padding(top = 8.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(c.border)); Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    parts.forEach { p -> if (p.type == "reasoning") ReasoningBlock(p) else ToolCard(p, onImage) }
                }
            }
        }
    }
}

// ---------- инструменты ----------

private fun toolIcon(name: String): ImageVector = when {
    name == "bash" -> Ic.Terminal
    name == "read" -> Ic.File
    name in setOf("edit", "write", "patch", "multiedit", "apply_patch") -> Ic.Edit
    name in setOf("grep", "glob", "list", "codesearch") -> Ic.Search
    name.startsWith("web") -> Ic.Globe
    name == "task" -> Ic.Bot
    name.startsWith("todo") -> Ic.ListCheck
    else -> Ic.Wrench
}

private fun JsonObject.firstStr(vararg keys: String) = keys.firstNotNullOfOrNull { k -> str(k)?.takeIf { it.isNotBlank() } }

private fun toolSummary(input: JsonObject?): String {
    if (input == null) return ""
    val path = input.firstStr("filePath", "path")?.substringAfterLast('/')
    return (path ?: input.firstStr("command", "pattern", "url", "query", "description", "prompt")).orEmpty().lineSequence().firstOrNull().orEmpty().take(120)
}

@Composable
fun ToolCard(p: Part, onImage: (String) -> Unit) {
    val c = Oc.c
    val name = p.raw.str("tool") ?: "tool"
    val state = p.raw.obj("state")
    if (name == "question") { QuestionResult(state?.obj("input"), state); return }
    if (name == "todowrite" && state?.obj("input")?.arr("todos").isNullOrEmpty()) return
    val status = state?.str("status") ?: "pending"
    val input = state?.obj("input")
    val title = state?.str("title")?.takeIf { it.isNotBlank() }
    val output = state?.str("output") ?: state?.str("error") ?: ""
    val diff = state?.obj("metadata")?.str("diff")
    var open by remember { mutableStateOf(name.startsWith("todo")) }
    val head = title ?: (name + toolSummary(input).let { if (it.isBlank()) "" else ": $it" })

    Column(Modifier.fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(toolIcon(name), tint = c.textDim, size = 20.dp); Spacer(Modifier.width(12.dp))
            Txt(head, size = 15.sp, maxLines = 1, modifier = Modifier.weight(1f)); Spacer(Modifier.width(10.dp))
            val rot by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, label = "chev")
            Ico(Ic.ChevronRight, tint = c.textDim, size = 16.dp, modifier = Modifier.rotate(rot)); Spacer(Modifier.width(8.dp))
            when (status) {
                "running", "pending" -> Spinner(18.dp, stroke = 2.dp)
                "error" -> Ico(Ic.Warning, tint = c.danger, size = 18.dp)
                else -> Ico(Ic.Check, tint = c.ok, size = 18.dp)
            }
        }
        AnimatedVisibility(open, enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)) + androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(180)), exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(80)) + androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(150))) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (name == "todowrite") {
                    input?.arr("todos")?.forEach { t ->
                        val o = t as? JsonObject ?: return@forEach
                        val done = o.str("status") == "completed"
                        Row(verticalAlignment = Alignment.Top) {
                            Ico(if (done) Ic.Check else Ic.Clock, tint = if (done) c.ok else c.textDim, size = 18.dp); Spacer(Modifier.width(10.dp))
                            Txt(o.str("content") ?: "", size = 15.sp, color = if (done) c.textDim else c.text)
                        }
                    }
                } else {
                    input?.firstStr("command")?.let { CodeBlock(it, "shell") }
                    if (diff != null && diff.isNotBlank()) DiffView(diff)
                    else if (output.isNotBlank()) CodeBlock(output.take(6000) + if (output.length > 6000) "\n… (${output.length - 6000} more chars)" else "", if (status == "error") "error" else "output")
                    state?.arr("attachments")?.forEach { a ->
                        val o = a as? JsonObject ?: return@forEach
                        if (o.str("mime")?.startsWith("image/") == true) o.str("url")?.let { u -> ImageThumb(u, 200.dp, { onImage(u) }) }
                    }
                }
            }
        }
    }
}

@Composable
fun DiffView(diff: String) {
    val c = Oc.c
    Box(Modifier.fillMaxWidth().clip(Oc.round(14.dp)).background(c.codeBg).horizontalScroll(rememberScrollState())) {
        Column(Modifier.padding(vertical = 10.dp)) {
            diff.lineSequence().take(400).forEach { l ->
                val bg = when { l.startsWith("+") && !l.startsWith("+++") -> c.ok.copy(alpha = .16f); l.startsWith("-") && !l.startsWith("---") -> c.danger.copy(alpha = .16f); else -> androidx.compose.ui.graphics.Color.Transparent }
                val fg = if (l.startsWith("@@")) c.link else c.text
                Text(l.ifEmpty { " " }, color = fg, fontFamily = Oc.mono, fontSize = 12.sp, softWrap = false, modifier = Modifier.background(bg).padding(horizontal = 12.dp))
            }
        }
    }
}

// ---------- запросы к пользователю ----------

@Composable
fun PermissionCard(r: PermissionReq, directory: String?) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    fun reply(kind: String) {
        Graph.chats.permissions.value = Graph.chats.permissions.value.filterNot { it.id == r.id }
        scope.launch { runCatching { Graph.api.replyPermission(r.id, directory, kind) } }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(Oc.round(24.dp)).background(c.surface).border(1.dp, c.border, Oc.round(24.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(Ic.Shield, tint = c.warn, size = 22.dp); Spacer(Modifier.width(10.dp))
            Txt("Permission needed", size = 17.sp, weight = FontWeight.Medium)
        }
        Txt(r.permission, size = 15.sp, color = c.textDim, modifier = Modifier.padding(top = 8.dp))
        r.patterns.take(3).forEach { Txt(it, size = 14.sp, family = Oc.mono, maxLines = 2, modifier = Modifier.padding(top = 4.dp)) }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Deny", { reply("reject") }, danger = true)
            SmallButton("Allow once", { reply("once") }, filled = true)
            SmallButton("Always", { reply("always") })
        }
    }
}

/**
 * Вопрос агента — как в Claude: панель на месте поля ввода, пронумерованные варианты, «Something else» и «Skip».
 * Один вариант без множественного выбора отправляется сразу; несколько вопросов идут по очереди.
 */
@Composable
fun QuestionPanel(q: QuestionReq, directory: String?) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    val picked = remember(q.id) { q.questions.map { mutableStateListOf<String>() } }
    val custom = remember(q.id) { q.questions.map { mutableStateOf("") } }
    var page by remember(q.id) { mutableStateOf(0) }
    val item = q.questions.getOrNull(page) ?: return
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    fun done() { Graph.chats.questions.value = Graph.chats.questions.value.filterNot { it.id == q.id } }
    fun submit() {
        val answers = q.questions.indices.map { i -> picked[i].toList() + listOfNotNull(custom[i].value.trim().takeIf { it.isNotEmpty() }) }
        done(); scope.launch { runCatching { Graph.api.replyQuestion(q.id, directory, answers) } }
    }
    fun next() { if (page < q.questions.lastIndex) page++ else submit() }
    fun skip() { done(); scope.launch { runCatching { Graph.api.rejectQuestion(q.id, directory) } } }
    val typed = custom[page].value.isNotBlank()

    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().heightIn(max = screenH * 0.7f)
            .clip(Oc.round(22.dp)).background(c.surface).border(1.dp, c.border, Oc.round(22.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (q.questions.size > 1 || item.header.isNotBlank()) Txt(
                    listOfNotNull(item.header.takeIf { it.isNotBlank() }, if (q.questions.size > 1) "${page + 1} of ${q.questions.size}" else null).joinToString(" · "),
                    size = 13.sp, color = c.textDim,
                )
                Txt(item.question, size = 16.sp, lineHeight = 22.sp)
            }
            IconBtn(Ic.Close, { skip() }, size = 44.dp, iconSize = 20.dp, tint = c.textDim)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            item.options.forEachIndexed { i, o ->
                val sel = o.label in picked[page]
                Row(
                    Modifier.fillMaxWidth().clip(Oc.round(10.dp)).clickable {
                        if (item.multiple) { if (sel) picked[page].remove(o.label) else picked[page].add(o.label) }
                        else { picked[page].clear(); picked[page].add(o.label); if (!typed) next() }
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(30.dp).clip(Oc.round(8.dp)).background(if (sel) c.text else c.surfaceHigh), contentAlignment = Alignment.Center) {
                        if (sel && item.multiple) Ico(Ic.Check, tint = c.bg, size = 16.dp) else Txt("${i + 1}", size = 14.sp, color = if (sel) c.bg else c.textDim)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(o.label, size = 16.sp)
                        if (o.description.isNotBlank()) Txt(o.description, size = 13.sp, color = c.textDim, lineHeight = 18.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.custom) {
                Box(Modifier.size(30.dp).clip(Oc.round(8.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) { Ico(Ic.Edit, tint = c.textDim, size = 16.dp) }
                Spacer(Modifier.width(14.dp))
                androidx.compose.foundation.text.BasicTextField(
                    custom[page].value, { custom[page].value = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 16.sp, fontFamily = Oc.body),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.text),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> Box { if (custom[page].value.isEmpty()) Txt("Something else", size = 16.sp, color = c.textDim); inner() } },
                )
            } else Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            val ready = typed || (item.multiple && picked[page].isNotEmpty())
            SmallButton(if (ready) (if (page < q.questions.lastIndex) "Next" else "Send") else "Skip", { if (ready) next() else skip() }, filled = ready)
        }
    }
}

/** Разрешение — той же панелью на месте поля ввода. */
@Composable
fun PermissionPanel(r: PermissionReq, directory: String?) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    fun reply(kind: String) {
        Graph.chats.permissions.value = Graph.chats.permissions.value.filterNot { it.id == r.id }
        scope.launch { runCatching { Graph.api.replyPermission(r.id, directory, kind) } }
    }
    Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(Oc.round(22.dp)).background(c.surface).border(1.dp, c.border, Oc.round(22.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(Ic.Shield, tint = c.warn, size = 20.dp); Spacer(Modifier.width(10.dp))
            Txt("Allow ${r.permission}?", size = 16.sp, weight = FontWeight.Medium)
        }
        r.patterns.take(3).forEach { Txt(it, size = 14.sp, family = Oc.mono, maxLines = 2, color = c.textDim, modifier = Modifier.padding(top = 6.dp)) }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Allow once", { reply("once") }, filled = true)
            SmallButton("Always", { reply("always") })
            Spacer(Modifier.weight(1f))
            SmallButton("Deny", { reply("reject") }, danger = true)
        }
    }
}

/** Отвеченный вопрос в ленте — как в Claude: «Question», сам вопрос и ответ. */
@Composable
private fun QuestionResult(input: JsonObject?, state: JsonObject?) {
    val c = Oc.c
    val qs = input?.arr("questions")?.mapNotNull { (it as? JsonObject)?.str("question") }.orEmpty()
    val answers = state?.obj("metadata")?.arr("answers")?.map { a ->
        ((a as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: listOfNotNull((a as? JsonPrimitive)?.contentOrNull)).joinToString(", ")
    }.orEmpty()
    Column(Modifier.fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt("Question", size = 14.sp, color = c.textDim)
        qs.forEachIndexed { i, q ->
            Txt(q, size = 16.sp, color = c.textDim, lineHeight = 22.sp)
            val a = answers.getOrNull(i)?.takeIf { it.isNotBlank() } ?: if (state?.str("status") == "error") "Skipped" else if (state?.str("status") == "completed") (state.str("output")?.lines()?.lastOrNull { it.isNotBlank() } ?: "") else "Waiting for your answer…"
            Txt(a, size = 16.sp)
        }
    }
}

