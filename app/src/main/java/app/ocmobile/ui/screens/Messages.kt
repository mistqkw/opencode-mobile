package app.ocmobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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

fun isImagePart(p: Part) = p.type == "file" && p.raw.str("mime")?.startsWith("image/") == true

@Composable
fun UserBubble(m: ChatMessage, onImage: (String) -> Unit, onLong: () -> Unit) {
    val c = Oc.c
    val images = m.parts.filter(::isImagePart)
    val files = m.parts.filter { it.type == "file" && !isImagePart(it) }
    val text = m.parts.filter { it.type == "text" && it.raw.str("synthetic") != "true" }.joinToString("\n") { it.text }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.End) {
        if (images.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                images.forEach { p -> p.raw.str("url")?.let { u -> ImageThumb(u, 132.dp, { onImage(u) }) } }
            }
        }
        files.forEach { f ->
            Row(Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(c.userBubble).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(Ic.File, tint = c.textDim, size = 20.dp); Spacer(Modifier.width(8.dp)); Txt(f.raw.str("filename") ?: "file", size = 15.sp)
            }
        }
        if (text.isNotBlank()) {
            Box(Modifier.widthIn85().clip(RoundedCornerShape(22.dp)).background(c.userBubble).longPress(onLong).padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(text, color = c.text, fontSize = 16.sp, lineHeight = 23.sp, fontFamily = Oc.body)
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
fun AssistantBlock(m: ChatMessage, last: Boolean, onImage: (String) -> Unit, onLong: () -> Unit, onRetry: (() -> Unit)?) {
    val c = Oc.c
    val clip = LocalClipboardManager.current
    val info = m.info
    Column(Modifier.fillMaxWidth().longPress(onLong).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        m.parts.forEach { p ->
            when (p.type) {
                "text" -> if (p.text.isNotBlank()) Markdown(p.text)
                "reasoning" -> if (p.text.isNotBlank()) ReasoningBlock(p)
                "tool" -> ToolCard(p, onImage)
                "file" -> if (isImagePart(p)) p.raw.str("url")?.let { u -> ImageThumb(u, 220.dp, { onImage(u) }) }
                "subtask" -> InfoLine(Ic.Bot, "Subtask: " + (p.raw.str("description") ?: p.raw.str("prompt") ?: ""))
                "patch" -> InfoLine(Ic.Diff, "Patch · ${p.raw.arr("files")?.size ?: 0} files")
                "compaction" -> InfoLine(Ic.Sparkle, "Context compacted")
                "retry" -> InfoLine(Ic.Refresh, "Retrying…")
            }
        }
        info.errorMessage?.let { ErrorCard(info.errorName, it) }
        if (info.time.completed != null && info.errorMessage == null) {
            val textAll = m.parts.filter { it.type == "text" }.joinToString("\n\n") { it.text }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (textAll.isNotBlank()) {
                    IconBtn(Ic.Copy, { clip.setText(AnnotatedString(textAll)); Graph.chats.toasts.tryEmit("Copied") }, size = 40.dp, iconSize = 20.dp, tint = c.textDim)
                    IconBtn(Ic.Share, {
                        val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, textAll)
                        ctx.startActivity(android.content.Intent.createChooser(i, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, size = 40.dp, iconSize = 20.dp, tint = c.textDim)
                    IconBtn(Ic.Play, { Speaker.toggle(ctx, textAll) }, size = 40.dp, iconSize = 20.dp, tint = c.textDim)
                }
                if (onRetry != null) IconBtn(Ic.Refresh, onRetry, size = 40.dp, iconSize = 20.dp, tint = c.textDim)
                val bits = buildList {
                    info.modelName?.let { add(it) }
                    info.cost?.takeIf { it > 0 }?.let { add("$" + "%.4f".format(it).trimEnd('0').trimEnd('.')) }
                }
                if (bits.isNotEmpty()) Txt(bits.joinToString(" · "), size = 13.sp, color = c.textDim)
            }
            if (last) Txt("OpenCode can make mistakes. Check important changes.", size = 13.sp, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
        }
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.danger.copy(alpha = .12f)).border(1.dp, c.danger.copy(alpha = .35f), RoundedCornerShape(18.dp)).padding(14.dp)) {
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
        Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Ic.Sparkle, tint = c.textDim, size = 16.dp); Spacer(Modifier.width(8.dp))
            Txt("Thinking", size = 14.sp, color = c.textDim)
            Spacer(Modifier.width(4.dp))
            Ico(if (open) Ic.ChevronDown else Ic.ChevronRight, tint = c.textDim, size = 16.dp)
        }
        AnimatedVisibility(open) {
            Row(Modifier.padding(top = 6.dp)) {
                Box(Modifier.width(2.dp).heightIn(min = 20.dp).background(c.border)); Spacer(Modifier.width(12.dp))
                Txt(p.text.trim(), size = 15.sp, color = c.textDim, lineHeight = 22.sp)
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
    val status = state?.str("status") ?: "pending"
    val input = state?.obj("input")
    val title = state?.str("title")?.takeIf { it.isNotBlank() }
    val output = state?.str("output") ?: state?.str("error") ?: ""
    val diff = state?.obj("metadata")?.str("diff")
    var open by remember { mutableStateOf(name.startsWith("todo")) }
    val head = title ?: (name + toolSummary(input).let { if (it.isBlank()) "" else ": $it" })

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface)) {
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
        AnimatedVisibility(open) {
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
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.codeBg).horizontalScroll(rememberScrollState())) {
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(24.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(24.dp)).padding(16.dp)) {
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

@Composable
fun QuestionCard(q: QuestionReq, directory: String?) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    val picked = remember(q.id) { q.questions.map { mutableStateListOf<String>() } }
    val custom = remember(q.id) { q.questions.map { mutableStateOf("") } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(24.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        q.questions.forEachIndexed { qi, item ->
            Txt(item.question, size = 17.sp, weight = FontWeight.Medium)
            item.options.forEach { o ->
                val sel = o.label in picked[qi]
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (sel) c.progressTrack else c.surfaceHigh)
                        .clickable {
                            if (item.multiple) { if (sel) picked[qi].remove(o.label) else picked[qi].add(o.label) }
                            else { picked[qi].clear(); picked[qi].add(o.label) }
                        }.padding(12.dp),
                ) {
                    Txt(o.label, size = 16.sp, color = if (sel) c.link else c.text)
                    if (o.description.isNotBlank()) Txt(o.description, size = 13.sp, color = c.textDim)
                }
            }
            if (item.custom) OcTextField(custom[qi].value, { custom[qi].value = it }, placeholder = "Type your own answer")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Skip", {
                Graph.chats.questions.value = Graph.chats.questions.value.filterNot { it.id == q.id }
                scope.launch { runCatching { Graph.api.rejectQuestion(q.id, directory) } }
            })
            SmallButton("Send", {
                val answers = q.questions.indices.map { i -> picked[i].toList() + listOfNotNull(custom[i].value.takeIf { it.isNotBlank() }) }
                Graph.chats.questions.value = Graph.chats.questions.value.filterNot { it.id == q.id }
                scope.launch { runCatching { Graph.api.replyQuestion(q.id, directory, answers) } }
            }, filled = true)
        }
    }
}
