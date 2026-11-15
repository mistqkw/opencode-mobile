package app.ocmobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Memory as a graph, like Obsidian's: you in the middle, the shelves around you, each shelf's facts
 * around it, and facts that share a rare word (or a [[link]]) joined across shelves. The layout is a
 * small force simulation: nodes push apart, links pull together, so it settles by itself and moves
 * when you drag a node. A fact saved since the last look rings once, so you see what's new.
 * The same file is used by cl0udly on the phone and on the computer.
 */

class MemNode(val id: String, val label: String, val kind: Int, val file: File?) {
    var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
    var held = false
    /** When it first showed up as new (ms), 0 for an old one. */
    var born = 0L
    val r get() = when (kind) { 0 -> 15f; 1 -> 9f; else -> 4.5f }
}

class MemGraph(val nodes: List<MemNode>, val links: List<Pair<Int, Int>>) {
    private val near = Array(nodes.size) { mutableListOf<Int>() }.also { a -> links.forEach { (i, j) -> a[i] += j; a[j] += i } }
    fun neighbours(i: Int): List<Int> = near[i]

    /** One step of the layout; returns how much it moved, so the loop can rest once it's still. */
    /** 1 = moving freely, 0 = frozen. A drag or a new fact warms it up again. */
    var heat = 1f

    fun step(): Float {
        val n = nodes.size
        for (i in 0 until n) {
            val a = nodes[i]
            for (j in i + 1 until n) {
                val b = nodes[j]
                var dx = a.x - b.x; var dy = a.y - b.y
                var d2 = dx * dx + dy * dy
                if (d2 < 0.01f) { dx = (i - j).toFloat(); dy = 1f; d2 = dx * dx + 1f }
                if (d2 > 160_000f) continue
                val f = (if (a.kind == 2 && b.kind == 2) 900f else 2600f) / d2
                val d = sqrt(d2)
                a.vx += dx / d * f; a.vy += dy / d * f; b.vx -= dx / d * f; b.vy -= dy / d * f
            }
        }
        for ((i, j) in links) {
            val a = nodes[i]; val b = nodes[j]
            val rest = when { a.kind == 0 || b.kind == 0 -> 120f; a.kind == 1 || b.kind == 1 -> 46f; else -> 70f }
            val dx = b.x - a.x; val dy = b.y - a.y
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.1f)
            val k = if (a.kind == 2 && b.kind == 2) 0.004f else 0.03f
            val f = (d - rest) * k
            a.vx += dx / d * f; a.vy += dy / d * f; b.vx -= dx / d * f; b.vy -= dy / d * f
        }
        var moved = 0f
        for (a in nodes) {
            // a soft pull to the middle keeps lone pieces from drifting off
            a.vx -= a.x * 0.0015f; a.vy -= a.y * 0.0015f
            if (a.held || a.kind == 0 && !a.held) { if (a.kind == 0 && !a.held) { a.x *= 0.9f; a.y *= 0.9f }; a.vx = 0f; a.vy = 0f; continue }
            a.vx *= 0.82f; a.vy *= 0.82f
            // the layout cools down: moves shrink every step until everything stands still, so it
            // settles instead of trembling forever (the trembling looked like flicker)
            a.x += a.vx * heat; a.y += a.vy * heat
            moved += (abs(a.vx) + abs(a.vy)) * heat
        }
        return moved
    }

    companion object {
        private val stop = setOf(
            "about", "after", "again", "always", "because", "before", "being", "could", "every", "never", "other", "should",
            "their", "there", "these", "thing", "things", "think", "which", "while", "would", "where", "with", "without",
            "который", "которые", "потому", "только", "сейчас", "всегда", "никогда", "можно", "нужно", "через", "после", "перед",
            "очень", "когда", "чтобы", "тоже", "также", "этого", "этой", "этот", "будет", "было", "были",
        )
        private fun words(s: String) = Regex("[\\p{L}\\p{N}]{5,}").findAll(s.lowercase()).map { it.value }.filter { it !in stop }.toSet()

        /** Reads the memory folder; nodes keep their places from [old], ids not in [seen] come in as new. */
        fun read(dir: File, old: MemGraph?, seen: MutableSet<String>, firstLook: Boolean): MemGraph {
            val nodes = ArrayList<MemNode>()
            val links = ArrayList<Pair<Int, Int>>()
            nodes += MemNode("you", "You", 0, File(dir, "MEMORY.md"))
            val shelves = dir.listFiles { f -> f.isFile && f.extension == "md" && f.name != "MEMORY.md" }?.sortedBy { it.name }.orEmpty()
            val factWords = ArrayList<Pair<Int, Set<String>>>()
            val shelfIndex = HashMap<String, Int>()
            shelves.forEach { f ->
                val si = nodes.size
                shelfIndex[f.nameWithoutExtension.lowercase()] = si
                nodes += MemNode("shelf:${f.name}", f.nameWithoutExtension.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }, 1, f)
                links += 0 to si
                runCatching { f.readLines() }.getOrDefault(emptyList()).map { it.trim() }.filter { it.startsWith("- ") || it.startsWith("* ") }.forEach { line ->
                    val text = line.drop(2).trim()
                    if (text.isEmpty()) return@forEach
                    val fi = nodes.size
                    nodes += MemNode("fact:${f.name}:${text.hashCode()}", text, 2, f)
                    links += si to fi
                    factWords += fi to words(text)
                }
            }
            // [[links]] to a shelf by name
            factWords.forEach { (fi, _) ->
                Regex("\\[\\[([^\\]]+)]]").findAll(nodes[fi].label).forEach { m -> shelfIndex[m.groupValues[1].lowercase().replace(' ', '-')]?.let { links += fi to it } }
            }
            // facts sharing a rare word: the web between shelves (at most 3 per fact, so it stays readable)
            val count = HashMap<String, Int>()
            factWords.forEach { (_, w) -> w.forEach { count[it] = (count[it] ?: 0) + 1 } }
            val per = HashMap<Int, Int>()
            for (a in factWords.indices) for (b in a + 1 until factWords.size) {
                val (ia, wa) = factWords[a]; val (ib, wb) = factWords[b]
                if (nodes[ia].file == nodes[ib].file) continue
                if ((per[ia] ?: 0) >= 3 || (per[ib] ?: 0) >= 3) continue
                if (wa.any { it in wb && (count[it] ?: 0) in 2..4 }) { links += ia to ib; per[ia] = (per[ia] ?: 0) + 1; per[ib] = (per[ib] ?: 0) + 1 }
            }
            // places: kept from before, new ones start next to the node they hang on
            val was = old?.nodes?.associateBy { it.id }.orEmpty()
            val now = System.currentTimeMillis()
            nodes.forEachIndexed { i, n ->
                val o = was[n.id]
                if (o != null) { n.x = o.x; n.y = o.y; n.born = o.born }
                else {
                    val parent = links.firstOrNull { it.second == i }?.first?.let { nodes[it] }
                    val a = (n.id.hashCode() % 628) / 100f
                    n.x = (parent?.x ?: 0f) + cos(a) * (if (n.kind == 1) 110f else 30f)
                    n.y = (parent?.y ?: 0f) + sin(a) * (if (n.kind == 1) 110f else 30f)
                    if (!firstLook && n.kind == 2 && n.id !in seen) n.born = now
                }
                seen += n.id
            }
            return MemGraph(nodes, links)
        }
    }
}

class GraphColors(val line: Color, val text: Color, val dim: Color, val accent: Color, val node: Color, val bg: Color)

/**
 * The graph view. Drag the empty space to look around, pinch (or the mouse wheel) to zoom, drag a
 * node and its neighbours follow, tap a node to light it up with its links. [rev] re-reads the folder.
 */
@Composable
fun MemoryGraphView(
    dir: File,
    seenFile: File,
    rev: Int,
    colors: GraphColors,
    modifier: Modifier = Modifier,
    onSelect: (MemNode?) -> Unit = {},
    onNew: () -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    // the layout loop is running: a drag then just moves the node instead of restarting the loop
    val running = remember { booleanArrayOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var graph by remember { mutableStateOf<MemGraph?>(null) }
    // laid-out names, kept between frames: measuring hundreds of names every frame was what made it lag
    val labels = remember(graph) { HashMap<Long, androidx.compose.ui.text.TextLayoutResult>() }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var selected by remember { mutableIntStateOf(-1) }
    var tick by remember { mutableIntStateOf(0) }
    var wake by remember { mutableIntStateOf(0) }

    LaunchedEffect(rev) {
        val seen = runCatching { seenFile.readLines().toMutableSet() }.getOrDefault(mutableSetOf())
        val first = !seenFile.exists()
        graph = MemGraph.read(dir, graph, seen, first).also { it.heat = if (graph == null) 1f else 0.5f }
        if (graph!!.nodes.any { it.born > 0 && System.currentTimeMillis() - it.born < 1000 }) onNew()
        runCatching { seenFile.parentFile?.mkdirs(); seenFile.writeText(seen.joinToString("\n")) }
        wake++
    }
    // the layout runs while something moves and rests when it's still (no frames, no battery)
    LaunchedEffect(graph, wake) {
        val g = graph ?: return@LaunchedEffect
        g.heat = maxOf(g.heat, if (g.nodes.any { it.held }) 0.5f else 0.35f)
        running[0] = true
        try { while (true) {
            withFrameNanos { }
            g.step()
            val ringing = g.nodes.any { it.born > 0 && System.currentTimeMillis() - it.born < RING_MS }
            val held = g.nodes.any { it.held }
            g.heat = if (held) maxOf(g.heat, 0.5f) else g.heat * 0.965f
            tick++
            if (g.heat < 0.01f && !ringing && !held) break
        } } finally { running[0] = false }
    }

    fun toWorld(p: Offset) = Offset((p.x - size.width / 2f - pan.x) / scale, (p.y - size.height / 2f - pan.y) / scale)
    fun hit(p: Offset): Int {
        val g = graph ?: return -1
        val w = toWorld(p)
        var best = -1; var bd = Float.MAX_VALUE
        g.nodes.forEachIndexed { i, n ->
            val d = hypot(n.x - w.x, n.y - w.y)
            if (d < (n.r + 14f / scale) && d < bd) { best = i; bd = d }
        }
        return best
    }

    Box(
        // clipped: dragged far or zoomed in, the dots must not paint over the bar above
        modifier.fillMaxSize().clipToBounds().onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var grab = -1; var start = Offset.Zero; var moved = false
                    var lastSpan = 0f; var lastCenter = Offset.Zero
                    while (true) {
                        val e = awaitPointerEvent()
                        val g = graph
                        if (e.type == PointerEventType.Scroll) {
                            // mouse wheel: zoom around the pointer
                            val ch = e.changes.first()
                            val f = if (ch.scrollDelta.y > 0) 0.9f else 1.1f
                            val c = ch.position - Offset(size.width / 2f, size.height / 2f)
                            val ns = (scale * f).coerceIn(0.3f, 4f)
                            pan = c - (c - pan) * (ns / scale); scale = ns
                            ch.consume(); continue
                        }
                        val down = e.changes.filter { it.pressed }
                        when {
                            down.size >= 2 -> {
                                val a = down[0].position; val b = down[1].position
                                val span = (a - b).getDistance(); val center = (a + b) / 2f
                                if (lastSpan > 0f) {
                                    val c = center - Offset(size.width / 2f, size.height / 2f)
                                    val ns = (scale * span / lastSpan).coerceIn(0.3f, 4f)
                                    pan = c - (c - pan) * (ns / scale) + (center - lastCenter); scale = ns
                                }
                                lastSpan = span; lastCenter = center; moved = true
                                if (grab >= 0) { g?.nodes?.getOrNull(grab)?.held = false; grab = -1 }
                                down.forEach { it.consume() }
                            }
                            down.size == 1 -> {
                                val ch = down[0]
                                lastSpan = 0f
                                if (ch.previousPressed.not()) {
                                    start = ch.position; moved = false
                                    grab = hit(ch.position)
                                    if (grab >= 0) { g?.nodes?.get(grab)?.held = true; if (!running[0]) wake++ }
                                } else if (ch.positionChanged()) {
                                    val d = ch.position - ch.previousPosition
                                    if ((ch.position - start).getDistance() > 8f) moved = true
                                    if (grab >= 0 && g != null) { val n = g.nodes[grab]; n.x += d.x / scale; n.y += d.y / scale; if (!running[0]) wake++ }
                                    else pan += d
                                    tick++
                                }
                                ch.consume()
                            }
                            else -> {
                                // all fingers up: a short touch without moving is a tap
                                if (e.changes.any { it.previousPressed } && !moved) {
                                    val i = hit(start)
                                    selected = if (i == selected) -1 else i
                                    onSelect(graph?.nodes?.getOrNull(selected))
                                }
                                if (grab >= 0) { g?.nodes?.getOrNull(grab)?.held = false; grab = -1; wake++ }
                                lastSpan = 0f
                            }
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            tick // redraw on every layout step
            val g = graph ?: return@Canvas
            val now = System.currentTimeMillis()
            val sel = selected
            val lit = if (sel >= 0) (g.neighbours(sel) + sel).toSet() else emptySet()
            withTransform({ translate(size.width / 2f + pan.x, size.height / 2f + pan.y); scale(scale, scale, Offset.Zero) }) {
                val w = 1f / scale
                for ((i, j) in g.links) {
                    val a = g.nodes[i]; val b = g.nodes[j]
                    val on = sel >= 0 && (i == sel || j == sel)
                    val cross = a.kind == 2 && b.kind == 2
                    drawLine(
                        if (on) colors.accent else colors.line.copy(alpha = if (sel >= 0) 0.25f else if (cross) 0.45f else 0.8f),
                        Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = (if (on) 1.6f else 1f) * w,
                    )
                }
                g.nodes.forEachIndexed { i, n ->
                    val dim = sel >= 0 && i !in lit
                    val col = when { i == sel || n.kind == 0 -> colors.accent; n.kind == 1 -> colors.node; else -> colors.dim }
                    // a new fact: pops in, then a thin ring goes out from it twice
                    val age = if (n.born > 0) now - n.born else Long.MAX_VALUE
                    val pop = if (age < 260) (age / 260f).let { 0.4f + 0.6f * it + 0.25f * sin(it * Math.PI.toFloat()) } else 1f
                    drawCircle(col.copy(alpha = if (dim) 0.25f else 1f), n.r * pop, Offset(n.x, n.y))
                    if (age < RING_MS) {
                        val k = (age % 1200) / 1200f
                        drawCircle(colors.accent.copy(alpha = (1f - k) * 0.9f), n.r + 4f + k * 26f, Offset(n.x, n.y), style = Stroke(1.4f * w))
                    }
                }
            }
            // names, in screen space: you and the shelves always, facts when zoomed in or lit. Each name is
            // laid out once (per size step) and kept; ones off the screen aren't drawn at all
            val f = (scale / 0.6f).coerceAtMost(1f).let { (it * 10).roundToInt() / 10f }.coerceAtLeast(0.1f)
            val cx = size.width / 2f + pan.x; val cy = size.height / 2f + pan.y
            g.nodes.forEachIndexed { i, n ->
                val show = n.kind < 2 || i in lit || scale > 1.7f || (n.born > 0 && now - n.born < RING_MS)
                if (!show) return@forEachIndexed
                val sx = cx + n.x * scale; val sy = cy + (n.y + n.r) * scale + 3f * scale.coerceAtMost(1f)
                if (sx < -260 * density || sx > size.width + 260 * density || sy < -60 * density || sy > size.height) return@forEachIndexed
                val big = i == sel
                val key = (i.toLong() shl 8) or ((f * 10).toLong() shl 1) or (if (big) 1L else 0L)
                val t = labels.getOrPut(key) {
                    measurer.measure(
                        n.label, TextStyle(fontSize = (if (n.kind == 2) 11 else if (n.kind == 1) 13 else 14).sp * f),
                        maxLines = if (n.kind == 2 && !big) 1 else 3, overflow = TextOverflow.Ellipsis,
                        constraints = Constraints(maxWidth = ((if (big) 260 else 170) * density * f).toInt().coerceAtLeast(10)),
                    )
                }
                drawText(t, color = if (n.kind == 2) colors.dim else colors.text, topLeft = Offset(sx - t.size.width / 2f, sy), alpha = if (sel >= 0 && i !in lit) 0.3f else 1f)
            }
        }
    }
}

private const val RING_MS = 2400L

