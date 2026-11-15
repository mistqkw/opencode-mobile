package app.ocmobile.ui.screens

import app.ocmobile.ui.theme.screenBackdrop
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.data.UsageWindow
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.OcDialog
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.SectionLabel
import app.ocmobile.ui.components.SettingsGroup
import app.ocmobile.ui.components.SettingsRow
import app.ocmobile.ui.components.SmallButton
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.UsageBar
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.launch

private fun resetText(w: UsageWindow): String {
    val at = w.resetAt ?: return "No usage in this window"
    val ms = at - System.currentTimeMillis()
    if (ms <= 0) return "Resetting now"
    val min = ms / 60_000
    return "Frees up in " + when {
        min < 60 -> "$min min"
        min < 60 * 48 -> "${min / 60} hr ${min % 60} min"
        else -> "${min / 1440} d ${(min % 1440) / 60} hr"
    }
}

private fun money(x: Double) = app.ocmobile.ui.components.Fmt.money(x)

@Composable
private fun UsageCard(title: String, w: UsageWindow) {
    val c = Oc.c
    val pct = w.percent
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(Oc.round(24.dp)).background(c.surface).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Txt(title, size = 21.sp, modifier = Modifier.weight(1f))
            Txt(if (pct > 0 && pct < 1) "<1% used" else app.ocmobile.ui.components.Fmt.percent(pct) + " used", size = 17.sp, color = c.textDim)
        }
        Spacer(Modifier.height(14.dp))
        UsageBar(w.fraction.toFloat(), warn = pct >= 85)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Txt(resetText(w), size = 16.sp, color = c.textDim, modifier = Modifier.weight(1f))
            Txt("${money(w.used)} / ${money(w.limit)}", size = 16.sp, color = c.textDim)
        }
    }
}

/** Three rings, one inside the other: 5 hours outside, the week, the month inside. They fill in when the screen opens. */
@Composable
private fun UsageHero(snap: app.ocmobile.data.UsageSnapshot) {
    val c = Oc.c
    val wins = listOf(Triple("5 hours", snap.fiveHour, 1f), Triple("7 days", snap.week, 0.62f), Triple("30 days", snap.month, 0.36f))
    val grow = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 120f)) }
    fun tint(w: UsageWindow, a: Float) = if (w.percent >= 85) c.danger else c.accent.copy(alpha = a)
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(Oc.round(28.dp)).background(c.surface).border(1.dp, c.border, Oc.round(28.dp)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val stroke = 11.dp.toPx(); val gap = 5.dp.toPx()
                    wins.forEachIndexed { i, (_, w, a) ->
                        val inset = stroke / 2 + i * (stroke + gap)
                        val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                        val sz = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                        val st = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        drawArc(c.border, -90f, 360f, false, tl, sz, style = st)
                        val sweep = (w.fraction.toFloat().coerceIn(0f, 1f) * 360f * grow.value).coerceAtLeast(if (w.used > 0) 4f else 0f)
                        if (sweep > 0f) drawArc(tint(w, a), -90f, sweep, false, tl, sz, style = st)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val p = snap.fiveHour.percent
                    Txt(if (p > 0 && p < 1) "<1%" else "${(p * grow.value).toInt()}%", size = 26.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Txt("5 hours", size = 12.sp, color = c.textDim)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                wins.forEach { (name, w, a) ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(Oc.round(4.dp)).background(tint(w, a)))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Txt("$name  ${money(w.used)} / ${money(w.limit)}", size = 14.sp)
                            Txt(resetText(w), size = 12.sp, color = c.textDim)
                        }
                    }
                }
            }
        }
    }
}

/** Cost per day as bars that rise one after another; today is the bright one. */
@Composable
private fun DailyBars(days: List<Double>) {
    val c = Oc.c
    val max = (days.maxOrNull() ?: 0.0).coerceAtLeast(0.0001)
    val grow = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(Oc.round(24.dp)).background(c.surface).border(1.dp, c.border, Oc.round(24.dp)).padding(18.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Txt("Today ${money(days.lastOrNull() ?: 0.0)}", size = 15.sp, modifier = Modifier.weight(1f))
            Txt("14 days ${money(days.sum())}", size = 13.sp, color = c.textDim)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
            days.forEachIndexed { i, d ->
                // each bar starts a little after the one before it
                val t = ((grow.value * 1.6f - i / days.size.toFloat() * 0.6f)).coerceIn(0f, 1f)
                val h = (d / max).toFloat() * t
                Box(Modifier.weight(1f).fillMaxHeight(h.coerceAtLeast(0.03f)).clip(Oc.round(4.dp))
                    .background(if (i == days.lastIndex) c.accent else c.accent.copy(alpha = if (d > 0) 0.35f else 0.12f)))
            }
        }
    }
}

@Composable
fun UsageScreen(nav: Nav) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    val snap by Graph.usage.snapshot.collectAsState()
    val refreshing by Graph.usage.refreshing.collectAsState()
    var info by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { Graph.usage.recompute(); runCatching { Graph.usage.backfill() } }

    Column(Modifier.fillMaxSize().screenBackdrop("UsageScreen1")) {
        TopBar("Usage", left = { IconBtn(Ic.Back, { nav.pop() }) }, right = { IconBtn(Ic.Info, { info = true }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            UsageHero(snap)
            SectionLabel("Last 14 days", Modifier.padding(bottom = 0.dp))
            DailyBars(snap.daily)
            SectionLabel("Models · last 30 days", Modifier.padding(bottom = 0.dp))
            if (snap.byModel.isEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(Oc.round(24.dp)).background(c.surface).padding(20.dp)) {
                    Txt("No paid usage yet", size = 20.sp)
                    Txt("Once the agent answers, each model's cost shows up here.", size = 16.sp, color = c.textDim, modifier = Modifier.padding(top = 4.dp))
                }
            } else {
                SettingsGroup {
                    snap.byModel.take(12).forEach { m ->
                        SettingsRow(
                            title = m.model.ifBlank { "unknown" }, subtitle = "${m.provider} · ${app.ocmobile.ui.components.Fmt.count(m.messages.toLong(), "reply", "replies")} · ${app.ocmobile.ui.components.Fmt.tokens(m.tokens)} tokens",
                            trailing = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Txt(money(m.cost), size = 17.sp)
                                    val share = if (snap.totalMonth > 0) (m.cost / snap.totalMonth).toFloat() else 0f
                                    Box(Modifier.padding(top = 6.dp).width(56.dp).height(3.dp).clip(Oc.round(2.dp)).background(c.border)) {
                                        Box(Modifier.fillMaxHeight().fillMaxWidth(share.coerceIn(0.02f, 1f)).background(c.accent))
                                    }
                                }
                            },
                        )
                    }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallButton("Edit limits", { edit = true })
                SmallButton(if (refreshing) "Refreshing…" else "Refresh", { scope.launch { Graph.usage.backfill() } })
                if (refreshing) Spinner(18.dp, stroke = 2.dp)
            }
            Txt(
                "Tracked from ${app.ocmobile.ui.components.Fmt.count(snap.tracked.toLong(), "reply", "replies")} on this device. Totals for the last 30 days: ${money(snap.totalMonth)}.",
                size = 14.sp, color = c.textDim, modifier = Modifier.padding(horizontal = 20.dp), lineHeight = 20.sp,
            )
        }
    }

    if (info) OcDialog("How this is counted", { info = false }) {
        Txt(
            "OpenCode has no public API for the remaining Go allowance, so the app adds up the cost of every reply that passed through this phone's server " +
                "over rolling windows of 5 hours, 7 days and 30 days, and compares it with the limits you set (defaults are the published Go limits: " +
                "\$12 / \$30 / \$60). Usage from other devices or the website is not included — the official numbers live at opencode.ai/console.",
            size = 15.sp, color = Oc.c.textDim, lineHeight = 22.sp,
        )
    }
    if (edit) EditLimitsDialog { edit = false }
}

@Composable
private fun EditLimitsDialog(onDone: () -> Unit) {
    val p = Graph.prefs
    var a by remember { mutableStateOf(p.limit5h.toString().removeSuffix(".0")) }
    var w by remember { mutableStateOf(p.limitWeek.toString().removeSuffix(".0")) }
    var m by remember { mutableStateOf(p.limitMonth.toString().removeSuffix(".0")) }
    var prov by remember { mutableStateOf(p.limitProviders) }
    OcDialog("Limits (USD)", onDone, "Save", {
        a.toDoubleOrNull()?.let { p.limit5h = it }; w.toDoubleOrNull()?.let { p.limitWeek = it }; m.toDoubleOrNull()?.let { p.limitMonth = it }
        p.limitProviders = prov; Graph.usage.recompute(); onDone()
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt("5 hours", size = 14.sp, color = Oc.c.textDim); OcTextField(a, { a = it }, keyboardType = KeyboardType.Decimal)
            Txt("7 days", size = 14.sp, color = Oc.c.textDim); OcTextField(w, { w = it }, keyboardType = KeyboardType.Decimal)
            Txt("30 days", size = 14.sp, color = Oc.c.textDim); OcTextField(m, { m = it }, keyboardType = KeyboardType.Decimal)
            Txt("Providers counted (comma separated, empty = all)", size = 14.sp, color = Oc.c.textDim); OcTextField(prov, { prov = it })
        }
    }
}
