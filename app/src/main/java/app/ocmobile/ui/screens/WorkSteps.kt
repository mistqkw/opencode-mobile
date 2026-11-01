package app.ocmobile.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.api.Part
import app.ocmobile.api.arr
import app.ocmobile.api.long
import app.ocmobile.api.obj
import app.ocmobile.api.str
import app.ocmobile.ui.components.CodeBlock
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.serialization.json.JsonObject

/*
 * The agent's work like in Claude Code: one summary line ("Ran 2 commands, edited count.sh +3 −1 ›"),
 * tap for a card with a list of steps, each step expands on its own: the command and its output,
 * the edit diff, the thought text. A running command shows right away, as a live terminal under the summary.
 */

@Composable
internal fun StepsTimeline(parts: List<Part>, running: Boolean, onImage: (String) -> Unit) {
    val c = Oc.c
    var open by remember { mutableStateOf(false) }
    val rot by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, tween(108), label = "steps")
    Column {
        Row(Modifier.clip(Oc.round(10.dp)).clickable { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(richSummary(parts), label = "sum", modifier = Modifier.weight(1f, fill = false), transitionSpec = {
                (fadeIn(tween(96)) + androidx.compose.animation.slideInVertically(tween(120)) { it / 2 }) togetherWith
                    (fadeOut(tween(60)) + androidx.compose.animation.slideOutVertically(tween(84)) { -it / 2 })
            }) { t -> Text(t, color = c.textDim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.width(4.dp))
            Ico(Ic.ChevronRight, tint = c.textDim, size = 16.dp, modifier = Modifier.rotate(rot))
            if (running) { Spacer(Modifier.width(8.dp)); Spinner(12.dp, color = c.textDim, stroke = 1.5.dp) }
        }
        // collapsed while a command runs: its terminal is visible right away
        val liveCmd = parts.lastOrNull { it.raw.str("tool") == "bash" && it.statusOf().let { s -> s == "running" || s == "pending" } }
        AnimatedVisibility(liveCmd != null && !open, enter = expandVertically(tween(132)) + fadeIn(tween(108)), exit = shrinkVertically(tween(132)) + fadeOut(tween(72))) {
            liveCmd?.let { Box(Modifier.padding(top = 8.dp)) { CommandView(it) } }
        }
        AnimatedVisibility(open, enter = fadeIn(tween(96)) + expandVertically(tween(84, easing = FastOutSlowInEasing)), exit = fadeOut(tween(60)) + shrinkVertically(tween(120))) {
            Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(Oc.round(14.dp)).border(1.dp, c.border, Oc.round(14.dp))) {
                parts.forEachIndexed { i, p ->
                    key(p.id.ifEmpty { "i$i" }) {
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                        StepIn(running) { StepRow(p, onImage) }
                    }
                }
            }
        }
    }
}

internal fun Part.statusOf(): String =
    if (type == "reasoning") (if (raw.obj("time")?.long("end") != null) "completed" else "running")
    else raw.obj("state")?.str("status") ?: "pending"

private val EDITS = setOf("edit", "multiedit", "patch", "apply_patch")

/** Group summary: verbs in grey, file name bright, +/− in green and red. */
@Composable
private fun richSummary(parts: List<Part>): androidx.compose.ui.text.AnnotatedString {
    val c = Oc.c
    val base = stepsSummary(parts)
    val touched = parts.filter { it.raw.str("tool") in EDITS || it.raw.str("tool") == "write" }
    val files = touched.mapNotNull { fileOf(it) }.distinct()
    val reads = parts.filter { it.raw.str("tool") == "read" }.mapNotNull { fileOf(it) }.distinct()
    return androidx.compose.ui.text.buildAnnotatedString {
        var text = base
        fun named(phrase: String, file: String, a: Int?, d: Int?) {
            val at = text.indexOf(phrase)
            if (at < 0) return
            append(text.substring(0, at))
            append(phrase.substringBefore(" ") + " ")
            withStyle(androidx.compose.ui.text.SpanStyle(color = c.text)) { append(file) }
            if (a != null && a > 0) withStyle(androidx.compose.ui.text.SpanStyle(color = c.ok)) { append(" +$a") }
            if (d != null && d > 0) withStyle(androidx.compose.ui.text.SpanStyle(color = c.danger)) { append(" −$d") }
            text = text.substring(at + phrase.length)
        }
        if (files.size == 1) {
            var a = 0; var d = 0
            touched.forEach { p -> if (p.raw.str("tool") == "write") a += p.raw.obj("state")?.obj("input")?.str("content")?.lines()?.size ?: 0 else diffCounts(p).let { (x, y) -> a += x; d += y } }
            named(if (base.contains("edited a file")) "edited a file" else "Edited a file", files[0], a, d)
        }
        if (reads.size == 1) named(if (text.contains("read a file")) "read a file" else "Read a file", reads[0], null, null)
        append(text)
    }
}

private fun fileOf(p: Part): String? = p.raw.obj("state")?.obj("input")?.firstStr("filePath", "path")?.substringAfterLast('/')

/** A new step slides in from below and fades in; steps from history are in place at once. */
@Composable
private fun StepIn(animate: Boolean, content: @Composable () -> Unit) {
    val p = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate) p.animateTo(1f, tween(90, easing = FastOutSlowInEasing)) }
    Box(Modifier.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 14f }) { content() }
}

private fun diffCounts(p: Part): Pair<Int, Int> {
    val md = p.raw.obj("state")?.obj("metadata")
    md?.obj("filediff")?.let { f -> return (f.long("additions") ?: 0).toInt() to (f.long("deletions") ?: 0).toInt() }
    val diff = md?.str("diff") ?: return 0 to 0
    var a = 0; var d = 0
    diff.lineSequence().forEach { l -> if (l.startsWith("+") && !l.startsWith("+++")) a++ else if (l.startsWith("-") && !l.startsWith("---")) d++ }
    return a to d
}

/** Step row in the card: what was done (file bright, +/−), a chevron; expands into details. */
@Composable
private fun StepRow(p: Part, onImage: (String) -> Unit) {
    val c = Oc.c
    val name = p.raw.str("tool").orEmpty()
    val st = p.raw.obj("state")
    val input = st?.obj("input")
    val status = p.statusOf()
    val live = status == "running" || status == "pending"
    var open by remember { mutableStateOf(false) }
    val rot by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, tween(108), label = "row")
    val label = androidx.compose.ui.text.buildAnnotatedString {
        fun file(f: String) = withStyle(androidx.compose.ui.text.SpanStyle(color = c.text)) { append(f) }
        when {
            p.type == "reasoning" -> append(if (live) "Thinking" else "Thought")
            name == "bash" -> append(input?.firstStr("description") ?: input?.firstStr("command")?.lineSequence()?.first().orEmpty())
            name in EDITS -> { append(if (live) "Editing " else "Edited "); file(fileOf(p) ?: toolSummary(input))
                val (a, d) = diffCounts(p)
                if (a > 0) withStyle(androidx.compose.ui.text.SpanStyle(color = c.ok)) { append(" +$a") }
                if (d > 0) withStyle(androidx.compose.ui.text.SpanStyle(color = c.danger)) { append(" −$d") } }
            name == "write" -> { append(if (live) "Writing " else if (st?.obj("metadata")?.str("exists") == "true") "Rewrote " else "Created "); file(fileOf(p).orEmpty())
                input?.str("content")?.lines()?.size?.let { withStyle(androidx.compose.ui.text.SpanStyle(color = c.ok)) { append(" +$it") } } }
            name == "read" -> { append("Read "); file(fileOf(p).orEmpty()) }
            name in setOf("grep", "glob", "list", "codesearch") -> { append("Searched "); file(toolSummary(input)) }
            name.startsWith("web") -> { append("Opened "); file(input?.firstStr("url")?.substringAfter("://")?.substringBefore('/') ?: toolSummary(input)) }
            name == "task" -> append("Helper: " + input?.firstStr("description").orEmpty())
            else -> append(name.replaceFirstChar { it.uppercase() } + toolSummary(input).let { if (it.isBlank()) "" else " $it" })
        }
    }
    Column(Modifier.fillMaxWidth().animateContentSize(tween(132))) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = c.textDim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(4.dp))
            Ico(Ic.ChevronRight, tint = c.textDim, size = 16.dp, modifier = Modifier.rotate(rot))
            Spacer(Modifier.width(8.dp))
            AnimatedContent(when { live -> 0; status == "error" -> 2; else -> 1 }, label = "st", transitionSpec = {
                (fadeIn(tween(108)) + scaleIn(tween(144), initialScale = 0.5f)) togetherWith fadeOut(tween(54))
            }) { s ->
                when (s) {
                    0 -> Spinner(12.dp, color = c.accent, stroke = 1.5.dp)
                    2 -> Ico(Ic.Warning, tint = c.danger, size = 14.dp)
                    else -> Spacer(Modifier.size(0.dp))
                }
            }
        }
        // a running command expands by itself: you can see what's happening
        AnimatedVisibility(open || (name == "bash" && live), enter = fadeIn(tween(84)) + expandVertically(tween(132)), exit = fadeOut(tween(54)) + shrinkVertically(tween(108))) {
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    p.type == "reasoning" -> Txt(p.text.trim(), size = 14.sp, color = c.textDim, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 2.dp))
                    name == "bash" -> CommandView(p)
                    status == "error" -> OutputBox(st?.str("error").orEmpty(), bad = true)
                    name in EDITS -> st?.obj("metadata")?.str("diff")?.takeIf { it.isNotBlank() }?.let { DiffView(it) } ?: OutputBox(st?.str("output").orEmpty())
                    name == "write" -> CodeBlock(input?.str("content").orEmpty().take(6000), input?.str("filePath")?.substringAfterLast('.').orEmpty())
                    else -> {
                        OutputBox(st?.str("output").orEmpty())
                        st?.arr("attachments")?.forEach { a ->
                            val o = a as? JsonObject ?: return@forEach
                            if (o.str("mime")?.startsWith("image/") == true) o.str("url")?.let { u -> app.ocmobile.ui.components.ImageThumb(u, 200.dp, { onImage(u) }) }
                        }
                    }
                }
            }
        }
    }
}

/** A command like in a terminal: a `$ command` block, output below (scrollable), "exit 0 · 0.4s" at the bottom. */
@Composable
private fun CommandView(p: Part) {
    val c = Oc.c
    val st = p.raw.obj("state")
    val status = p.statusOf()
    val live = status == "running" || status == "pending"
    val command = st?.obj("input")?.firstStr("command").orEmpty()
    val out = (st?.obj("metadata")?.str("output") ?: st?.str("output")).orEmpty().trimEnd().takeUnless { it == "(no output)" }.orEmpty()
    val exit = st?.obj("metadata")?.str("exit")?.toIntOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clip(Oc.round(10.dp)).background(c.codeBg).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("$", color = c.textDim, fontFamily = Oc.mono, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.width(10.dp))
            Text(command.take(2000), color = c.link, fontFamily = Oc.mono, fontSize = 13.sp, lineHeight = 19.sp)
        }
        if (out.isNotEmpty() || live) OutputBox(out, live = live, bad = exit != null && exit != 0)
        if (!live) {
            val took = st?.obj("time")?.let { t -> val a = t.long("start"); val b = t.long("end"); if (a != null && b != null) (b - a) / 1000.0 else null }
            Row(Modifier.padding(start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (exit != null && exit != 0) c.danger else c.ok))
                Spacer(Modifier.width(8.dp))
                Txt(listOfNotNull(exit?.let { "exit $it" } ?: "done", took?.let { if (it < 10) "%.1fs".format(it) else "${it.toInt()}s" }).joinToString(" · "),
                    size = 12.sp, color = c.textDim, family = Oc.mono)
            }
        }
    }
}

/**
 * Framed output: scrolls on its own (up to ~14 lines tall). While the command runs it stays on
 * the last lines and the cursor blinks.
 */
@Composable
private fun OutputBox(text: String, live: Boolean = false, bad: Boolean = false) {
    val c = Oc.c
    val v = rememberScrollState()
    val shown = remember(text) { text.take(20_000).let { if (text.length > 20_000) "$it\n… (${text.length - 20_000} more chars)" else it } }
    LaunchedEffect(shown, live) { if (live) v.animateScrollTo(v.maxValue) }
    Box(
        Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(Oc.round(10.dp)).border(1.dp, c.border, Oc.round(10.dp)).background(c.bg)
            .verticalScroll(v).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column {
            if (shown.isNotEmpty()) Text(shown, color = if (bad) c.danger else c.text, fontFamily = Oc.mono, fontSize = 12.sp, lineHeight = 17.sp, softWrap = false)
            if (live) Cursor()
        }
    }
}

@Composable
private fun Cursor() {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "cursor")
    val blink by t.animateFloat(1f, 0f, infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse), label = "blink")
    Box(Modifier.padding(top = 2.dp).size(width = 7.dp, height = 13.dp).background(c.text.copy(alpha = if (blink > 0.5f) 0.85f else 0f)))
}
