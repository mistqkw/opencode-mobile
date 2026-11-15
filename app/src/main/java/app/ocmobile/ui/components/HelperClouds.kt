package app.ocmobile.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import app.ocmobile.Graph
import app.ocmobile.api.PermissionReq
import app.ocmobile.api.obj
import app.ocmobile.api.str
import app.ocmobile.data.Team
import app.ocmobile.ui.screens.AssistantBlock
import app.ocmobile.ui.screens.PermissionPanel
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.rememberMotionEnabled
import kotlinx.coroutines.launch

/** One helper the main agent called (OpenCode's task tool): its brief, model, state and the OK it waits for. */
data class HelperRun(
    val callId: String,
    val agent: String,
    val description: String,
    val prompt: String,
    /** asking, working, done or failed */
    val status: String,
    val childId: String?,
    val permission: PermissionReq?,
)

/** The helpers of a chat from its task calls, with any OK they wait for (theirs or one to start them). */
@Composable
fun rememberHelperRuns(sessionId: String): List<HelperRun> {
    val chat = remember(sessionId) { Graph.chats.chat(sessionId) }
    val perms by Graph.chats.permissions.collectAsState()
    // derived, so the screens that show helpers redraw only when a helper really changes, not on
    // every token the main agent or one of the helpers prints
    val runs by remember(sessionId) {
        derivedStateOf {
            chat.messages.flatMap { m -> m.parts.filter { it.type == "tool" && it.raw.str("tool") == "task" } }.map { p ->
                val state = p.raw.obj("state")
                val input = state?.obj("input")
                Triple(p.raw.str("callID") ?: p.id, state, input)
            }
        }
    }
    return remember(runs, perms) {
        runs.map { (call, state, input) ->
            val child = state?.obj("metadata")?.str("sessionId")
            val own = perms.firstOrNull { it.sessionID == sessionId && it.permission == "task" && it.callID == call }
            val inside = child?.let { c -> perms.firstOrNull { it.sessionID == c } }
            HelperRun(
                callId = call,
                agent = input?.str("subagent_type").orEmpty(),
                description = input?.str("description") ?: "A task",
                prompt = input?.str("prompt").orEmpty(),
                status = when {
                    own != null || inside != null -> "asking"
                    state?.str("status") == "completed" -> "done"
                    state?.str("status") == "error" -> "failed"
                    else -> "working"
                },
                childId = child,
                permission = own ?: inside,
            )
        }
    }
}

/** The open helper card and the cloud it grows out of. */
object HelperView {
    var sessionId by mutableStateOf<String?>(null)
    var callId by mutableStateOf<String?>(null)
    var from by mutableStateOf(Rect.Zero)
}

/**
 * Little clouds above the input, one per helper: a working one bobs and looks around, one waiting
 * for an OK pulses in the accent, a finished one wears a check. A tap opens its card.
 */
@Composable
fun HelperClouds(sessionId: String) {
    val runs = rememberHelperRuns(sessionId)
    // your team waits here too: each sub-agent that's on and not busy right now sits as a still cloud
    @Suppress("UNUSED_VARIABLE") val added = Team.justAdded   // a new sub-agent redraws the row
    val busy = runs.filter { it.status == "working" || it.status == "asking" }.map { it.agent }.toSet()
    val team = if (Team.mode == "manual") Team.members.filter { it.on && Team.slug(it.name) !in busy } else emptyList()
    if (runs.isEmpty() && team.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom,
    ) {
        runs.forEach { r -> key(r.callId) { HelperCloud(sessionId, r, variantOf(runs, r)) } }
        team.forEach { m -> key("team-" + m.name) { TeamCloud(m) } }
    }
}

/**
 * A sub-agent at rest. A new one flies in from above and lands with a little bounce (once, right
 * after "Create"); a tap says who it is and what it does.
 */
@Composable
private fun TeamCloud(m: Team.Member) {
    val c = Oc.c
    val slug = Team.slug(m.name)
    val fresh = Team.justAdded == m.name
    val drop = remember { Animatable(if (fresh) 1f else 0f) }
    LaunchedEffect(fresh) {
        if (fresh) {
            drop.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 260f))
            Team.justAdded = null
        }
    }
    Column(
        Modifier.graphicsLayer { translationY = -drop.value * 260.dp.toPx(); alpha = 1f - drop.value * 0.6f; val s = 1f + drop.value * 0.5f; scaleX = s; scaleY = s }
            .clip(Oc.round(12.dp)).clickable { Graph.chats.toasts.tryEmit(m.name + ": " + m.role) }.padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HelperGlyph("idle", 38.dp, slug)
        Spacer(Modifier.height(4.dp))
        Txt(short(m.name), size = 12.sp, color = c.textDim, maxLines = 1, raw = true)
    }
}

@Composable
private fun HelperCloud(sessionId: String, r: HelperRun, variant: Int) {
    val c = Oc.c
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(160, easing = FastOutSlowInEasing)) }
    val open = HelperView.callId == r.callId
    Column(
        Modifier.graphicsLayer { alpha = appear.value * (if (open) 0f else 1f); val s = 0.6f + 0.4f * appear.value; scaleX = s; scaleY = s }
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .clip(Oc.round(12.dp))
            .clickable(onClickLabel = "Open ${Team.display(r.agent)}") { HelperView.sessionId = sessionId; HelperView.callId = r.callId; HelperView.from = bounds }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HelperGlyph(r.status, 38.dp, r.agent, variant)
        Spacer(Modifier.height(4.dp))
        Txt(short(Team.display(r.agent)) + if (variant > 0) " ${variant + 1}" else "", size = 12.sp, color = if (r.status == "asking") c.accent else c.textDim, maxLines = 1)
    }
}

/**
 * A helper is a small cl0udly: the mascot itself (same drawing, same shape), only smaller.
 * The state shows in the eyes, as with the big one: glancing while it works, wide open while it
 * waits for your OK, "^" when done, "x" when it failed. The body stays still.
 * Each helper has its own color (by its model, [key]) and its own rhythm, so several side by side differ;
 * [variant] tells apart two helpers of the same model.
 */
@Composable
fun HelperGlyph(status: String, size: Dp, key: String = "", variant: Int = 0) {
    val c = Oc.c
    val motion = rememberMotionEnabled()
    val seed = (key.hashCode() and 0x7fffffff) + variant * 3
    val tint = HELPER_TINTS[seed % HELPER_TINTS.size]
    val t = rememberInfiniteTransition(label = "helper")
    val look by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1100 + seed % 5 * 160, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "look")
    val blink by t.animateFloat(0f, 1f, infiniteRepeatable(androidx.compose.animation.core.keyframes { val d = 3300 + seed % 7 * 340; durationMillis = d; 0f at 0; 0f at d - 250; 1f at d - 180; 1f at d - 100; 0f at d - 30 }), label = "blink")
    val mood = when (status) { "done" -> CloudMood.Happy; "failed" -> CloudMood.Dizzy; "asking" -> CloudMood.Surprised; else -> CloudMood.Idle }
    Box(Modifier.size(width = size + 6.dp, height = size * CH / CW + 6.dp)) {
        Canvas(Modifier.align(Alignment.BottomStart).size(width = size, height = size * CH / CW)) {
            drawIconCloud(
                0f, 0f, this.size.width, mood,
                eyeDx = if (status == "working" && motion) look * 0.03f else 0f,
                blink = if (status == "working" && motion && blink > 0.5f) 1f else 0f,
                eye = if (c.isDark) c.bg else INK, outline = if (c.isDark) null else INK,
                body = tint.first, shade = tint.second,
            )
        }
        if (status == "done" || status == "failed") Box(
            Modifier.align(Alignment.TopEnd).size(13.dp).clip(CircleShape).background(if (status == "done") c.ok else c.danger),
            contentAlignment = Alignment.Center,
        ) { Ico(if (status == "done") Ic.Check else Ic.Close, size = 9.dp, tint = c.bg) }
    }
}

/**
 * The open helper: the card grows out of its cloud to the whole screen (position, size and corners
 * together, 240 ms), like an app opening from its icon. Back, the close button or a tap outside
 * shrink it back. It shows the model and its price, the brief, its steps live, and its OK requests.
 */
@Composable
fun HelperHost() {
    val sid = HelperView.sessionId ?: return
    val call = HelperView.callId ?: return
    val c = Oc.c
    val motion = rememberMotionEnabled()
    val t = remember(call) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(call) { if (motion) t.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) else t.snapTo(1f) }
    val close: () -> Unit = remember(call) {
        {
            scope.launch {
                if (motion) t.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                HelperView.callId = null; HelperView.sessionId = null
            }
            Unit
        }
    }
    BackHandler { close() }
    // The growing is done in the layout and draw phases only: the card inside is laid out once at
    // full size and never recomposed by the animation (with a long helper log that used to hang the app).
    BoxWithConstraints(
        Modifier.fillMaxSize().drawBehind { drawRect(Color.Black, alpha = 0.35f * t.value) }
            .clickable(remember { MutableInteractionSource() }, null) { close() },
    ) {
        val W = constraints.maxWidth; val H = constraints.maxHeight
        val fullW = maxWidth; val fullH = maxHeight
        val to = Rect(0f, 0f, W.toFloat(), H.toFloat())
        val from = HelperView.from.takeIf { it.width > 0 } ?: Rect(to.center, Size(1f, 1f))
        fun rect(k: Float) = Rect(lerp(from.left, to.left, k), lerp(from.top, to.top, k), lerp(from.right, to.right, k), lerp(from.bottom, to.bottom, k))
        Box(
            Modifier
                .layout { m, _ ->
                    val r = rect(t.value)
                    val p = m.measure(Constraints.fixed(r.width.toInt().coerceAtLeast(1), r.height.toInt().coerceAtLeast(1)))
                    layout(W, H) { p.place(r.left.toInt(), r.top.toInt()) }
                }
                .graphicsLayer { shape = RoundedCornerShape(lerp(14f, 0f, t.value).dp.toPx()); clip = true }
                .background(c.bg)
                .clickable(remember { MutableInteractionSource() }, null) { },
        ) {
            Box(
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(fullW, fullH)
                    .graphicsLayer { alpha = ((t.value - 0.5f) / 0.5f).coerceIn(0f, 1f) },
            ) { HelperBody(sid, call, close) }
        }
    }
}

/** Takes only ids, so the grow animation above never recomposes it. */
@Composable
private fun HelperBody(sid: String, call: String, close: () -> Unit) {
    val runs = rememberHelperRuns(sid)
    val run = runs.firstOrNull { it.callId == call }
    if (run != null) HelperCard(run, variantOf(runs, run), close)
    else Box(Modifier.fillMaxSize()) { Txt("This helper is gone.", Modifier.align(Alignment.Center), color = Oc.c.textDim) }
}

@Composable
private fun HelperCard(r: HelperRun, variant: Int, close: () -> Unit) {
    val c = Oc.c
    val ref = Team.refOf(r.agent)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            HelperGlyph(r.status, 44.dp, r.agent, variant)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Txt(Team.display(r.agent), size = 19.sp, weight = FontWeight.SemiBold, maxLines = 1)
                Txt(listOf(Team.cost(ref), statusText(r.status)).joinToString("  ·  "), size = 13.sp, color = c.textDim, maxLines = 1)
            }
            IconBtn(Ic.Close, close, contentDescription = "Close")
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        val child = r.childId
        val chat = remember(child) { child?.let { Graph.chats.chat(it) } }
        // a helper opened after it finished (or after a restart) has nothing in memory yet
        LaunchedEffect(chat) { if (chat != null && chat.messages.isEmpty()) runCatching { chat.load() } }
        val replies by remember(chat) { derivedStateOf { chat?.messages?.filter { !it.info.isUser }.orEmpty() } }
        // only the steps on screen are built: a helper's log can be hundreds of steps long
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
            item(key = "brief") {
                Column {
                    Txt("Task", size = 13.sp, color = c.textDim)
                    Spacer(Modifier.height(4.dp))
                    Txt(r.description, size = 17.sp, lineHeight = 23.sp)
                    if (r.prompt.isNotBlank()) {
                        var full by remember { mutableStateOf(false) }
                        Spacer(Modifier.height(10.dp))
                        Column(Modifier.fillMaxWidth().clip(Oc.round(14.dp)).background(c.surface).clickable { full = !full }.padding(14.dp)) {
                            Txt(if (full || r.prompt.length <= 240) r.prompt else r.prompt.take(240) + "…", size = 14.sp, color = c.textDim, lineHeight = 20.sp)
                            if (r.prompt.length > 240) Txt(if (full) "Show less" else "Show the whole brief", size = 13.sp, color = c.link, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Txt("What it does", size = 13.sp, color = c.textDim)
                    Spacer(Modifier.height(6.dp))
                    if (child == null) Txt(if (r.status == "asking") "Waiting for your OK to start." else "Starting…", size = 15.sp, color = c.textDim)
                    else if (replies.isEmpty()) Txt("Thinking…", size = 15.sp, color = c.textDim)
                }
            }
            items(replies.size, key = { replies[it].id }) { i ->
                AssistantBlock(replies[i], i == replies.lastIndex, onImage = {}, onLong = {}, onRetry = null)
            }
        }
        r.permission?.let { p ->
            Box(Modifier.padding(bottom = 8.dp)) {
                if (p.permission == "task") TaskPermissionPanel(p, Graph.chats.session(p.sessionID)?.directory)
                else PermissionPanel(p, Graph.chats.session(p.sessionID)?.directory)
            }
        }
    }
}

private fun statusText(s: String) = when (s) { "asking" -> "waits for your OK"; "done" -> "done"; "failed" -> "failed"; else -> "working" }

/** The main agent wants a helper: which model, what it costs, what for; allow once, always for this model, or not. */
@Composable
fun TaskPermissionPanel(r: PermissionReq, directory: String?) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    val agent = r.patterns.firstOrNull().orEmpty()
    val ref = Team.refOf(agent)
    fun reply(kind: String) {
        Graph.chats.permissions.value = Graph.chats.permissions.value.filterNot { it.id == r.id }
        scope.launch { runCatching { Graph.api.replyPermission(r.id, directory, kind) } }
    }
    Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(Oc.round(22.dp)).background(c.surface).border(1.dp, c.accent.copy(alpha = 0.5f), Oc.round(22.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HelperGlyph("asking", 32.dp, agent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt("Ask ${Team.display(agent)} for help?", size = 16.sp, weight = FontWeight.Medium)
                Txt(Team.cost(ref), size = 13.sp, color = c.textDim)
            }
        }
        (r.metadata["description"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.let {
            Txt(it, Modifier.padding(top = 10.dp).fillMaxWidth().clip(Oc.round(12.dp)).background(c.bg).padding(12.dp), size = 14.sp)
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Allow once", { reply("once") }, filled = true)
            if (ref != null) SmallButton("Always", {
                Team.setAlways(ref, true)
                Graph.chats.forgetRules()
                reply("once")
            })
            Spacer(Modifier.weight(1f))
            SmallButton("Deny", { reply("reject") }, danger = true)
        }
    }
}

/** A cloud's caption: the model's name without "Free", cut with an ellipsis. */
private fun short(label: String): String {
    val t = label.removeSuffix(" Free").removeSuffix(" (free)").trim()
    return if (t.length <= 20) t else t.take(19).trimEnd() + "…"
}

/** Helper body colors: soft and flat, each with its darker step; the main cl0udly stays white. */
private val HELPER_TINTS = listOf(
    Color(0xFFA9C8FF) to Color(0xFF7F9FD6),
    Color(0xFFA8E6C8) to Color(0xFF79B898),
    Color(0xFFFFCFA6) to Color(0xFFD9A274),
    Color(0xFFCDBBFF) to Color(0xFF9F8CD6),
    Color(0xFFFFB8CC) to Color(0xFFD68A9E),
    Color(0xFFF2E49C) to Color(0xFFC7B86C),
    Color(0xFFA3E4EC) to Color(0xFF73B6BF),
    Color(0xFFE6DCCB) to Color(0xFFB8AD9B),
)

/** Which of the same model's helpers in this chat [r] is: 0 for the first, 1 for the second… */
internal fun variantOf(runs: List<HelperRun>, r: HelperRun) = runs.filter { it.agent == r.agent }.indexOfFirst { it.callId == r.callId }.coerceAtLeast(0)
