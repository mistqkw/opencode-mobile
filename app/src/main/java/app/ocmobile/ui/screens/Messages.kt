package app.ocmobile.ui.screens

import androidx.compose.animation.animateContentSize
import android.net.Uri
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
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
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
import app.ocmobile.ui.components.DiffView
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.ImageThumb
import app.ocmobile.ui.components.Markdown
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.SmallButton
import app.ocmobile.ui.components.longPress
import app.ocmobile.ui.components.longPressAnchored
import app.ocmobile.ui.components.anchorTo
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

fun hhmm(ms: Long): String = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ms))

/** A bubble with a tail, like in messengers: 16dp corners, a small curled tail at the bottom corner. */
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
    // what a tap opens: the copy in the environment, the file inside the message, or (remote) the file on the computer
    val files: List<Pair<String, app.ocmobile.ui.components.ViewSrc?>> =
        m.parts.filter { it.type == "file" && !isImagePart(it) }.map { p ->
            val name = p.raw.str("filename") ?: "file"
            val url = p.raw.str("url").orEmpty()
            name to when {
                url.startsWith("data:") -> app.ocmobile.ui.components.ViewSrc.Data(url, name)
                url.startsWith("file:") && Graph.devices.isRemote -> app.ocmobile.ui.components.ViewSrc.Remote(Uri.parse(url).path ?: "", Graph.chats.session(m.info.sessionID)?.directory)
                url.startsWith("file:") -> app.ocmobile.ui.components.OutFiles.host(Uri.parse(url).path ?: "")?.let { app.ocmobile.ui.components.ViewSrc.Local(it) }
                else -> null
            }
        } + m.parts.filter { it.type == "text" }.mapNotNull { app.ocmobile.data.Attachments.parseAttached(it.text) }.map { (name, path) ->
            name to path?.let { app.ocmobile.ui.components.OutFiles.host(it) }?.let { app.ocmobile.ui.components.ViewSrc.Local(it) }
        }
    val text = m.parts.filter { it.type == "text" && it.raw.str("synthetic") != "true" }.joinToString("\n") { it.text }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.End) {
        if (images.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                images.forEach { p -> p.raw.str("url")?.let { u -> ImageThumb(u, 132.dp, { onImage(u) }) } }
            }
        }
        files.forEach { (name, src) ->
            val host = (src as? app.ocmobile.ui.components.ViewSrc.Local)?.file
            Row(
                Modifier.padding(bottom = 8.dp).clip(Oc.round(14.dp)).background(c.userBubble)
                    .then(if (src != null) Modifier.clickable { app.ocmobile.ui.components.FileView.open(src) } else Modifier)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(Ic.File, tint = c.textDim, size = 20.dp); Spacer(Modifier.width(8.dp)); Txt(name, size = 15.sp, maxLines = 1)
                host?.let { Spacer(Modifier.width(8.dp)); Txt(app.ocmobile.data.Attachments.human(it.length()), size = 13.sp, color = c.textDim) }
            }
        }
        if (text.isNotBlank()) {
            if (Oc.terminal) {
                // like in the TUI: a full-width dark block with a blue bar on the left
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).background(c.userBubble).longPressAnchored(onLong)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(c.link))
                    Text(text, color = c.text, fontSize = 15.sp, lineHeight = 22.sp, fontFamily = Oc.body, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
                }
            } else when (Oc.look.user) {
                // Messenger: a bubble with a tail and the time inside
                "tail" -> Box(Modifier.widthIn85().clip(TailShape(right = true)).background(c.userBubble).longPressAnchored(onLong).padding(start = 14.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text, color = c.text, fontSize = 16.sp, lineHeight = 22.sp, fontFamily = Oc.body)
                        Txt(hhmm(m.info.time.created), size = 11.sp, color = c.text.copy(alpha = 0.55f))
                    }
                }
                // Notebook: no bubble, a label and text with a bar on the left
                "plain" -> Column(Modifier.fillMaxWidth().longPressAnchored(onLong)) {
                    Txt("YOU · " + hhmm(m.info.time.created), size = 11.sp, color = c.textDim, family = Oc.label)
                    Row(Modifier.padding(top = 4.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                        Box(Modifier.width(2.dp).fillMaxHeight().background(c.accent)); Spacer(Modifier.width(12.dp))
                        Text(text, color = c.text, fontSize = 17.sp, lineHeight = 25.sp, fontFamily = Oc.body, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                }
                // Cards: a card with a header
                "card" -> Column(Modifier.widthIn85().clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp)).longPressAnchored(onLong).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent)); Spacer(Modifier.width(8.dp))
                        Txt("You", size = 13.sp, color = c.textDim, weight = FontWeight.Medium); Spacer(Modifier.width(6.dp)); Txt(hhmm(m.info.time.created), size = 12.sp, color = c.textDim)
                    }
                    Text(text, color = c.text, fontSize = 16.sp, lineHeight = 23.sp, fontFamily = Oc.body, modifier = Modifier.padding(top = 6.dp))
                }
                // Hairline: a 1px frame, no fill
                "line" -> Box(Modifier.widthIn85().clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp)).longPressAnchored(onLong).padding(horizontal = 16.dp, vertical = 11.dp)) {
                    Text(text, color = c.text, fontSize = 16.sp, lineHeight = 23.sp, fontFamily = Oc.body)
                }
                else -> Box(Modifier.widthIn85().clip(Oc.round(22.dp)).background(c.userBubble).longPressAnchored(onLong).padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text(text, color = c.text, fontSize = 16.sp, lineHeight = 23.sp, fontFamily = Oc.body)
                }
            }
            // the same floating card as the reply's ⋯, right at the bubble
            if (menu) app.ocmobile.ui.components.FloatMenu(
                { menu = false },
                listOf(
                    app.ocmobile.ui.components.FloatItem("Copy", Ic.Copy) { clip.setText(AnnotatedString(text)); app.ocmobile.ui.components.copiedNote() },
                    app.ocmobile.ui.components.FloatItem("Select text", Ic.TextSelect, act = onSelect),
                    app.ocmobile.ui.components.FloatItem("Edit", Ic.Edit, act = onEdit),
                ),
                header = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(m.info.time.created)),
                alignEnd = true,
            )
        }
    }
}

@Composable
private fun Modifier.widthIn85(): Modifier {
    val w = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp * 0.88f
    return this.then(Modifier.widthIn(max = w))
}

@Composable
fun AssistantBlock(m: ChatMessage, last: Boolean, onImage: (String) -> Unit, onLong: () -> Unit, onRetry: (() -> Unit)?, first: Boolean = true, parts: List<Part> = m.parts) {
    val c = Oc.c
    val clip = LocalClipboardManager.current
    val info = m.info
    val look = Oc.look
    val frame = when (look.reply) {
        "bubble" -> Modifier.padding(start = 10.dp, end = 40.dp).clip(TailShape(right = false)).background(c.surface).padding(start = 20.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)
        "card" -> Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp)).padding(14.dp)
        else -> Modifier.fillMaxWidth().padding(horizontal = 20.dp)
    }
    val textAll = parts.filter { it.type == "text" }.joinToString("\n\n") { it.text }
    val boxed = look.reply != "plain"
    @Composable fun Actions() {
        // the buttons under a reply slide out smoothly instead of jerking the chat when the reply ends
        AnimatedVisibility(
            info.time.completed != null && info.errorMessage == null && (textAll.isNotBlank() || last),
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)) + androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(140)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(80)) + androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(120)),
        ) { Column {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (textAll.isNotBlank()) {
                    app.ocmobile.ui.components.CopyButton(textAll, size = 40.dp, iconSize = 20.dp)
                    IconBtn(Ic.Share, {
                        val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, textAll)
                        ctx.startActivity(android.content.Intent.createChooser(i, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, size = 40.dp, iconSize = 20.dp, tint = c.textDim)
                    IconBtn(Ic.Volume, { Speaker.toggle(ctx, textAll) }, size = 40.dp, iconSize = 20.dp, tint = c.textDim)
                }
                if (onRetry != null) IconBtn(Ic.Refresh, onRetry, size = 40.dp, iconSize = 20.dp, tint = c.textDim)
                // the model and the price are in the "⋮" menu: the row is the same size under every reply
                var at by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                Box(Modifier.anchorTo { at = it }) {
                    IconBtn(Ic.More, { app.ocmobile.ui.components.MenuAnchor.rect = at; onLong() }, size = 40.dp, iconSize = 20.dp, tint = c.textDim)
                }
            }
            if (last) Txt((if (app.ocmobile.data.Persona.on) "cl0udly" else "OpenCode") + " can make mistakes. Check important changes.", size = 13.sp, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
        } }
    }
    Column(Modifier.fillMaxWidth()) {
    Column(frame, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Hairline: mono label with a blue square
        // the label goes only above the first message of a reply, not above every step
        if (!first && look.reply == "plain") Unit
        else if (look.id == "hairline") Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(c.accent)); Spacer(Modifier.width(10.dp))
            Txt("OPENCODE · " + hhmm(info.time.created), size = 11.sp, color = c.textDim, family = Oc.label)
        } else if (look.reply == "card" || look.user == "plain") Row(verticalAlignment = Alignment.CenterVertically) {
            if (look.reply == "card") { Box(Modifier.size(6.dp).clip(CircleShape).background(c.link)); Spacer(Modifier.width(8.dp)) }
            Txt(if (look.user == "plain") "OPENCODE · " + hhmm(info.time.created) else "OpenCode", size = if (look.user == "plain") 11.sp else 13.sp, color = c.textDim, family = Oc.label, weight = if (look.reply == "card") FontWeight.Medium else null)
            if (look.reply == "card") { Spacer(Modifier.width(6.dp)); Txt(hhmm(info.time.created), size = 12.sp, color = c.textDim) }
        }
        // Like in Claude: consecutive steps (thoughts, commands, reads) as one line "Thought, ran 2 commands ›"
        val segments = remember(parts.size, parts.fold(0) { a, p -> a * 31 + System.identityHashCode(p.raw) }) { segment(parts) }
        // the session isn't running anymore (the server stopped mid-step): don't spin the icon forever
        val busyNow = Graph.chats.statuses.collectAsState().value.containsKey(info.sessionID)
        val running = last && info.time.completed == null && busyNow
        segments.forEachIndexed { si, seg ->
            if (seg.size > 1 || (seg.size == 1 && isStep(seg[0]))) { StepsTimeline(seg, running && si == segments.lastIndex, onImage); return@forEachIndexed }
            val p = seg[0]
            when (p.type) {
                "text" -> if (p.text.isNotBlank()) androidx.compose.runtime.key(p.id) {
                    // while it's typed, the block grows smoothly instead of jumping a line at a time
                    androidx.compose.foundation.text.selection.SelectionContainer(if (running) Modifier.animateContentSize(androidx.compose.animation.core.spring(stiffness = 900f)) else Modifier) { Markdown(smoothText(p.text, running)) }
                }
                "reasoning" -> if (p.text.isNotBlank()) ReasoningBlock(p)
                "tool" -> ToolCard(p, onImage)
                "file" -> if (isImagePart(p)) p.raw.str("url")?.let { u -> ImageThumb(u, app.ocmobile.ui.components.TILE, { onImage(u) }, Modifier.clip(Oc.round(18.dp))) }
                    else PartFileChip(p)
                "subtask" -> InfoLine(Ic.Bot, "Subtask: " + (p.raw.str("description") ?: p.raw.str("prompt") ?: ""))
                "patch" -> InfoLine(Ic.Diff, "Patch · ${p.raw.arr("files")?.size ?: 0} files")
                "compaction" -> InfoLine(Ic.Sparkle, "Context compacted")
                "retry" -> InfoLine(Ic.Refresh, "Retrying…")
            }
        }
        val outFiles = remember(parts.size, parts.lastOrNull()?.text?.length, info.time.completed) {
            app.ocmobile.ui.components.OutFiles.find(parts.filter { it.type == "text" }.joinToString("\n") { it.text } + "\n" + app.ocmobile.ui.components.OutFiles.written(parts.map { it.raw }))
        }
        if (outFiles.isNotEmpty()) app.ocmobile.ui.components.FileCards(outFiles)
        val remoteFiles = remember(parts.size, info.time.completed) {
            if (info.time.completed == null) emptyList() else app.ocmobile.ui.components.OutFiles.findRemote(parts.filter { it.type == "text" }.joinToString("\n") { it.text } + "\n" + app.ocmobile.ui.components.OutFiles.written(parts.map { it.raw }))
        }
        if (remoteFiles.isNotEmpty()) app.ocmobile.ui.components.RemoteFileCards(remoteFiles, Graph.chats.session(info.sessionID)?.directory)
        info.errorMessage?.let { ErrorCard(info.errorName, it, info.sessionID) }
        if (boxed) {
            if (look.reply == "bubble" && info.time.completed != null)
                Txt(hhmm(info.time.created), Modifier.align(Alignment.End).offset(y = (-4).dp), size = 12.sp, color = c.textDim)
        } else Actions()
    }
    // in a bubble/card the buttons and label go under the frame, like reactions in a messenger
    if (boxed) Column(Modifier.padding(start = if (look.reply == "bubble") 14.dp else 16.dp, end = 16.dp, top = 2.dp)) { Actions() }
    }
}

/** Reads the reply aloud with the system TextToSpeech: tapping again stops reading. */
object Speaker {
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ready = false
    private var speaking = false

    fun toggle(ctx: android.content.Context, text: String) {
        if (speaking) { tts?.stop(); speaking = false; return }
        val go = {
            val clean = text.replace(Regex("[`*#>_]"), "")
            tts?.let { app.ocmobile.data.Speech.applyVoice(it, clean) }
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

/** What went wrong in plain words, and what to do about it. */
private class Trouble(val title: String, val hint: String, val retry: Boolean = true, val model: Boolean = true, val signIn: Boolean = false, val compact: Boolean = false, val quiet: Boolean = false)

private fun troubleOf(name: String?, message: String): Trouble {
    val m = message.lowercase()
    return when {
        name == "MessageAbortedError" || m.contains("aborted") ->
            Trouble("You stopped this reply", "Send it again whenever you like.", model = false, quiet = true)
        name == "ProviderAuthError" || m.contains("unauthorized") || m.contains("api key") || m.contains("didn't accept the sign-in") || m.contains("401") ->
            Trouble("The model's service didn't accept the sign-in", "Sign in again, or pick a model that needs no account.", retry = false, signIn = true)
        m.contains("context") && (m.contains("length") || m.contains("too long") || m.contains("maximum") || m.contains("window")) ->
            Trouble("This chat got too long for the model", "Compact it to free up room, or switch to a model with a bigger memory.", retry = false, compact = true)
        m.contains("function.name") || m.contains("expected '") || m.contains("tool call") || m.contains("invalid json") ->
            Trouble("The model sent a broken step", "It happens with some free models. Try again, or pick another model.")
        m.contains("timeout") || m.contains("timed out") || m.contains("network") || m.contains("fetch failed") || m.contains("econn") || m.contains("unable to connect") || m.contains("socket") ->
            Trouble("Couldn't reach the model's server", "Check the connection and try again.", model = false)
        m.contains("overloaded") || m.contains("503") || m.contains("502") || m.contains("unavailable") ->
            Trouble("The model's server is busy right now", "Try again in a moment, or pick another model.")
        else -> Trouble("Something went wrong with this reply", "Try again, or pick another model.")
    }
}

@Composable
private fun ErrorCard(name: String?, message: String, sessionId: String) {
    val c = Oc.c
    if (app.ocmobile.data.Limits.isLimit(name, message)) { LimitCard(message, sessionId); return }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val t = remember(name, message) { troubleOf(name, message) }
    var details by remember { mutableStateOf(false) }
    fun again() {
        val chat = Graph.chats.chat(sessionId)
        val last = chat.messages.lastOrNull { it.info.isUser } ?: return
        val text = userText(last)
        if (text.isNotBlank()) scope.launch { runCatching { Graph.chats.edit(ctx, sessionId, last, text, emptyList()) }.onFailure { Graph.chats.toasts.tryEmit(it.message ?: "Couldn't send again") } }
    }
    // a calm card, not a red alarm: what happened, why, and the way on
    Column(Modifier.fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface).border(1.dp, if (t.quiet) c.border else c.danger.copy(alpha = .3f), Oc.round(18.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(if (t.quiet) Ic.Stop else Ic.Warning, tint = if (t.quiet) c.textDim else c.danger, size = 20.dp); Spacer(Modifier.width(10.dp))
            Txt(t.title, size = 15.sp, weight = FontWeight.Medium)
        }
        Txt(t.hint, Modifier.padding(top = 6.dp), size = 14.sp, color = c.textDim, lineHeight = 20.sp)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (t.retry) SmallButton("Try again", { again() }, filled = true)
            if (t.compact) SmallButton("Compact chat", {
                scope.launch { runCatching { Graph.api.summarize(sessionId, Graph.chats.session(sessionId)?.directory, Graph.prefs.providerId, Graph.prefs.modelId) } }
            }, filled = true)
            if (t.signIn) SmallButton("Sign in", { Graph.chats.openRequest.value = "route:connect:" + Graph.prefs.providerId.removeSuffix("-chat").let { if (it == "copilot") "github-copilot" else it } }, filled = true)
            if (t.model || t.signIn || t.compact) SmallButton("Change model", { app.ocmobile.ui.components.UiRequests.pickModel++ })
        }
        if (!t.quiet) Txt(if (details) message else "Details", Modifier.padding(top = 10.dp).clickable { details = !details }, size = 13.sp, color = c.textDim, lineHeight = 18.sp)
    }
}

@Composable
private fun ReasoningBlock(p: Part) {
    val c = Oc.c
    var open by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.clip(Oc.round(10.dp)).clickable { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (Oc.look.id == "cloud") app.ocmobile.ui.components.MiniCloudIcon(16.dp) else Ico(Ic.Cloud, tint = c.textDim, size = 16.dp)
            Spacer(Modifier.width(8.dp))
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

// ---------- steps ----------

/** A step is a thought or a tool call (except questions and todo lists: those are shown separately). */
private fun isStep(p: Part): Boolean = when (p.type) {
    "reasoning" -> p.text.isNotBlank()
    "tool" -> p.raw.str("tool") !in setOf("question", "todowrite", "todoread", "remember")
    else -> false
}

/** Reply parts → groups: consecutive steps are gathered together, the rest go one by one. */
private fun segment(parts: List<Part>): List<List<Part>> {
    val out = ArrayList<List<Part>>()
    var cur = ArrayList<Part>()
    for (p in parts) {
        // service parts (step start/end, snapshots, patch summary) are invisible and must not break a group
        if (p.type in setOf("step-start", "step-finish", "snapshot", "patch") || (p.type == "text" && p.text.isBlank())) continue
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

internal fun stepsSummary(parts: List<Part>): String {
    var thought = false; var team = 0; var cmd = 0; var search = 0; var web = 0; var other = 0; var failed = 0
    // count distinct files: two edits of one file are "edited a file"
    val reads = HashSet<String>(); val edits = HashSet<String>()
    parts.forEach { p ->
        if (p.type == "reasoning") { thought = true; return@forEach }
        val t = p.raw.str("tool").orEmpty()
        if (p.raw.obj("state")?.str("status") == "error") failed++
        when {
            t == "bash" -> cmd++
            t == "task" -> team++
            t == "read" -> reads += p.raw.obj("state")?.obj("input")?.firstStr("filePath", "path") ?: p.id
            t in setOf("edit", "write", "patch", "multiedit", "apply_patch") -> edits += p.raw.obj("state")?.obj("input")?.firstStr("filePath", "path") ?: p.id
            t in setOf("grep", "glob", "list", "codesearch") -> search++
            t.startsWith("web") -> web++
            else -> other++
        }
    }
    fun n(k: Int, one: String, many: String) = if (k == 1) one else "$k $many"
    val bits = buildList {
        if (thought) add("thought")
        if (team > 0) add("asked " + n(team, "a helper", "helpers"))
        if (cmd > 0) add("ran " + n(cmd, "a command", "commands"))
        if (reads.isNotEmpty()) add("read " + n(reads.size, "a file", "files"))
        if (edits.isNotEmpty()) add("edited " + n(edits.size, "a file", "files"))
        if (search > 0) add("searched " + n(search, "once", "times"))
        if (web > 0) add("browsed " + n(web, "a page", "pages"))
        if (other > 0) add("used " + n(other, "a tool", "tools"))
    }
    return bits.joinToString(", ").replaceFirstChar { it.uppercase() } + if (failed > 0) " · $failed failed" else ""
}

// ---------- tools ----------

internal fun toolIcon(name: String): ImageVector = when {
    name == "bash" -> Ic.Terminal
    name == "read" -> Ic.File
    name in setOf("edit", "write", "patch", "multiedit", "apply_patch") -> Ic.Edit
    name in setOf("grep", "glob", "list", "codesearch") -> Ic.Search
    name.startsWith("web") -> Ic.Globe
    name == "task" -> Ic.Bot
    name.startsWith("todo") -> Ic.ListCheck
    else -> Ic.Wrench
}

internal fun JsonObject.firstStr(vararg keys: String) = keys.firstNotNullOfOrNull { k -> str(k)?.takeIf { it.isNotBlank() } }

internal fun toolSummary(input: JsonObject?): String {
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
    if (name == "remember") { RememberCard(p); return }
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
        // live terminal: while a command runs its output is visible right away, without expanding
        val live = name == "bash" && (status == "running" || status == "pending") && !open
        AnimatedVisibility(live, enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(180)), exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(220))) {
            LiveTerminal(input?.firstStr("command").orEmpty(), state?.obj("metadata")?.str("output").orEmpty())
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

/**
 * Command output while it runs: a dark window with the last 10 lines, a prompt and a blinking cursor,
 * new lines appear at the bottom. When it finishes the window collapses, the full output is in the card.
 */
@Composable
private fun LiveTerminal(command: String, output: String, inset: Boolean = true) {
    val c = Oc.c
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "cursor")
    val blink by t.animateFloat(1f, 0f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(530, easing = androidx.compose.animation.core.LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "blink")
    val lines = remember(output) { output.trimEnd().lines().takeLast(10).map { it.take(400) } }
    Column(
        Modifier.fillMaxWidth().then(if (inset) Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp) else Modifier).clip(Oc.round(14.dp)).background(c.codeBg)
            .horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("$ " + command.lineSequence().first().take(200), color = c.link, fontFamily = Oc.mono, fontSize = 12.sp, softWrap = false)
        if (output.isNotBlank()) lines.forEach { Text(it.ifEmpty { " " }, color = c.text, fontFamily = Oc.mono, fontSize = 12.sp, softWrap = false) }
        Box(Modifier.padding(top = 2.dp).size(width = 7.dp, height = 13.dp).background(c.text.copy(alpha = if (blink > 0.5f) 0.9f else 0f)))
    }
}

// ---------- requests to the user ----------

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
 * The agent's question, like in Claude: a panel in place of the input, numbered options, "Something else" and "Skip".
 * A single option without multiple choice is sent at once; several questions go one after another.
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

/** A permission request, with the same panel in place of the input. */
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

/** An answered question in the feed, like in Claude: "Question", the question itself and the answer. */
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


/**
 * Text that arrives in chunks is typed out evenly: the shown part catches up with what arrived
 * within ~0.15 s, however much came at once. Finished text (history) is shown at once,
 * a new reply ([live]) is typed from the first letter.
 * Words come out whole: a half-typed word never jumps to the next line, and the reply is laid out
 * once per word instead of on every frame.
 */
@Composable
private fun smoothText(target: String, live: Boolean): String {
    val latest = androidx.compose.runtime.rememberUpdatedState(target)
    var shown by remember { androidx.compose.runtime.mutableIntStateOf(if (live) 0 else target.length) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var acc = 0f
        var lastT = 0L
        while (true) {
            if (shown >= latest.value.length) {
                if (shown > latest.value.length) shown = latest.value.length   // the text was replaced entirely
                androidx.compose.runtime.snapshotFlow { latest.value.length }.first { it > shown }
                lastT = 0L; acc = 0f
            }
            val t = androidx.compose.runtime.withFrameNanos { it }
            val dt = if (lastT == 0L) 1f / 60 else ((t - lastT) / 1e9f).coerceAtMost(0.05f)
            lastT = t
            val back = latest.value.length - shown
            acc += maxOf(260f, back / 0.15f) * dt
            val n = acc.toInt()
            if (n > 0) { acc -= n; shown = minOf(latest.value.length, shown + n) }
        }
    }
    val words by remember {
        androidx.compose.runtime.derivedStateOf {
            val t = latest.value
            if (shown >= t.length) t else {
                var e = shown
                while (e < t.length && !t[e].isWhitespace()) e++
                t.substring(0, e)
            }
        }
    }
    return words
}

/** A file the agent put into its reply (not a picture): its name; a tap opens it in the app's viewer. */
@Composable
private fun PartFileChip(p: Part) {
    val c = Oc.c
    val name = p.raw.str("filename") ?: "file"
    val url = p.raw.str("url").orEmpty()
    val src = when {
        url.startsWith("data:") -> app.ocmobile.ui.components.ViewSrc.Data(url, name)
        url.startsWith("file:") && Graph.devices.isRemote -> app.ocmobile.ui.components.ViewSrc.Remote(Uri.parse(url).path ?: "", null)
        url.startsWith("file:") -> app.ocmobile.ui.components.OutFiles.host(Uri.parse(url).path ?: "")?.let { app.ocmobile.ui.components.ViewSrc.Local(it) }
        else -> null
    }
    Row(
        Modifier.clip(Oc.round(14.dp)).border(1.dp, c.border, Oc.round(14.dp))
            .then(if (src != null) Modifier.clickable { app.ocmobile.ui.components.FileView.open(src) } else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { Ico(Ic.File, tint = c.textDim, size = 20.dp); Spacer(Modifier.width(8.dp)); Txt(name, size = 15.sp, maxLines = 1) }
}

/**
 * The model's allowance ran out: not an alarming red error but what happened and a way on: another
 * free model, with the last message sent to it again.
 */
@Composable
private fun LimitCard(message: String, sessionId: String) {
    val c = Oc.c
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val next = remember { app.ocmobile.data.Limits.nextFree() }
    var details by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(Ic.Clock, tint = c.accent, size = 20.dp); Spacer(Modifier.width(10.dp))
            Txt("This model's limit is used up for now", size = 15.sp, weight = FontWeight.Medium)
        }
        Txt(
            "Free models have their own daily allowance on OpenCode's side. Another model usually still works, and this one comes back later.",
            Modifier.padding(top = 6.dp), size = 14.sp, color = c.textDim, lineHeight = 20.sp,
        )
        if (next != null) {
            Spacer(Modifier.height(12.dp))
            app.ocmobile.ui.components.Pill("Go on with ${next.third}", {
                val p = Graph.prefs
                p.providerId = next.first; p.modelId = next.second; p.variant = ""
                val last = Graph.chats.chat(sessionId).messages.lastOrNull { it.info.isUser }
                val text = last?.parts?.filter { it.type == "text" && it.raw["synthetic"]?.toString() != "true" }?.joinToString("\n") { it.text }.orEmpty()
                if (text.isNotBlank()) scope.launch { runCatching { Graph.chats.send(ctx, sessionId, text, emptyList()) } }
            }, Modifier.fillMaxWidth())
        }
        Txt(if (details) message else "Details", Modifier.padding(top = 10.dp).clickable { details = !details }, size = 13.sp, color = c.textDim)
    }
}


/**
 * "Remember this?": the agent heard something lasting about the user and offers it with the
 * `remember` tool (cl0udly-brain.js) instead of saving it on its own. Remember puts the fact on its
 * shelf; the answer is kept per call, so the card stays answered. The computer has the same card.
 */
@Composable
private fun RememberCard(p: Part) {
    val input = p.raw.obj("state")?.obj("input") ?: return
    val fact = input.str("fact")?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val shelf = input.str("shelf")?.trim().orEmpty().removeSuffix(".md").ifBlank { "about-me" }
    val call = p.raw.str("callID") ?: p.raw.str("id").orEmpty()
    val c = Oc.c
    val answer = Graph.prefs.memoryAnswer(call)
    Column(
        Modifier.fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface).animateContentSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(Ic.Scroll, tint = if (answer == "y") c.accent else c.textDim, size = 16.dp)
            Spacer(Modifier.width(8.dp))
            Txt(when (answer) { "y" -> "Remembered"; "n" -> "Not saved"; else -> "Remember this?" }, size = 13.sp, color = c.textDim, weight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Txt(shelf, size = 12.sp, family = Oc.mono, color = c.textDim)
        }
        Txt(fact, size = 16.sp, color = if (answer == "n") c.textDim else c.text)
        if (answer == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            SmallButton("Remember", {
                val safe = shelf.lowercase().replace(Regex("[^a-z0-9а-яё]+"), "-").trim('-').ifBlank { "notes" }
                app.ocmobile.data.Memory.addFact(java.io.File(app.ocmobile.data.Memory.dir(), "$safe.md"), fact)
                Graph.prefs.answerMemory(call, true)
            }, filled = true)
            SmallButton("No", { Graph.prefs.answerMemory(call, false) })
        }
    }
}
