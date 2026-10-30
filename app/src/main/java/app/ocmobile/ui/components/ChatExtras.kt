package app.ocmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.data.Chat
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.screens.previewTargets
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Сервер остановился посреди задачи: продолжить с того места или отменить. */
@Composable
fun InterruptedCard(id: String) {
    val c = Oc.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val set by Graph.chats.interrupted.collectAsState()
    if (id !in set) return
    val look = Oc.look.id
    val shape = when (look) { "terminal" -> RectangleShape; "hairline" -> RoundedCornerShape(2.dp); "kimi" -> RoundedCornerShape(28.dp); else -> RoundedCornerShape(20.dp) }
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().clip(shape).background(if (look == "hairline" || look == "terminal") c.bg else c.surface)
            .border(1.dp, c.warn.copy(alpha = 0.5f), shape).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(Ic.Warning, tint = c.warn, size = 18.dp); Spacer(Modifier.width(10.dp))
            Txt(if (look == "hairline") "TASK INTERRUPTED" else "The task was interrupted", size = if (look == "hairline") 12.sp else 16.sp, weight = FontWeight.Medium, family = if (look == "hairline" || look == "terminal") Oc.mono else null)
        }
        Spacer(Modifier.height(6.dp))
        Txt("The server stopped while OpenCode was working, so it didn't finish. Continue from where it stopped?", size = 14.sp, color = c.textDim, lineHeight = 20.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Cancel", { Graph.chats.dismissInterrupted(id) })
            SmallButton("Continue", { scope.launch { runCatching { Graph.chats.resumeInterrupted(ctx, id) }.onFailure { Graph.chats.toasts.tryEmit(it.message ?: "Could not continue") } } }, filled = true)
        }
    }
}

/** На главном: список прерванных задач, чтобы не искать их по чатам. */
@Composable
fun InterruptedBanner(onOpen: (String) -> Unit) {
    val c = Oc.c
    val set by Graph.chats.interrupted.collectAsState()
    val ids = set.filter { Graph.chats.session(it) != null }
    if (ids.isEmpty()) return
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(CircleShape).background(c.surface).border(1.dp, c.warn.copy(alpha = 0.45f), CircleShape)
            .tap(scale = 0.98f) { onOpen(ids.first()) }.padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(Ic.Warning, tint = c.warn, size = 17.dp); Spacer(Modifier.width(10.dp))
        Txt(
            if (ids.size == 1) "Interrupted: " + (Graph.chats.session(ids.first())?.displayTitle ?: "a task") else "${ids.size} tasks were interrupted",
            Modifier.weight(1f), size = 14.sp, maxLines = 1,
        )
        Txt("Open", size = 14.sp, color = c.accent)
    }
}

/** Над полем ввода: открыть в превью сайт, который агент запустил или записал. */
@Composable
fun PreviewChips(chat: Chat, id: String, nav: Nav) {
    val c = Oc.c
    val targets = remember(chat.messages.size, chat.messages.lastOrNull()?.parts?.size) { previewTargets(chat) }
    if (targets.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        targets.forEach { (label, url) ->
            Row(
                Modifier.clip(CircleShape).background(c.surface).border(1.dp, c.border, CircleShape).tap(scale = 0.96f) { nav.push(Route.Preview(url, id)) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(Ic.Play, tint = c.accent, size = 15.dp); Spacer(Modifier.width(7.dp))
                Txt("Preview $label", size = 14.sp, maxLines = 1)
            }
        }
    }
}

/**
 * Сколько примерно обойдётся сообщение: контекст чата (уже в кеше), новый текст и ответ средней длины
 * по ценам модели. Если модель считается в лимитах — ещё и доля 5-часового лимита.
 */
@Composable
fun CostHint(chat: Chat?, text: String, attachments: Int) {
    val prefs = Graph.prefs
    if (!prefs.costHint || text.isBlank()) return
    val c = Oc.c
    val look = Oc.look.id
    val m = Graph.catalog.model(prefs.providerId, prefs.modelId) ?: return
    fun price(k: String) = m.cost?.get(k)?.jsonPrimitive?.doubleOrNull ?: 0.0
    val pin = price("input"); val pout = price("output"); val pcache = m.cost?.get("cache_read")?.jsonPrimitive?.doubleOrNull ?: pin
    val hint = if (pin == 0.0 && pout == 0.0) "Free model" else {
        val replies = chat?.messages?.mapNotNull { it.info.tokens?.takeIf { t -> t.output > 0 } }.orEmpty()
        val ctxTokens = replies.lastOrNull()?.let { it.input + it.cache.read + it.cache.write + it.output } ?: 12_000L
        val outAvg = if (replies.isEmpty()) 800L else replies.takeLast(5).map { it.output + it.reasoning }.average().toLong()
        val newTokens = text.length / 4 + attachments * 1_200
        val usd = (ctxTokens * pcache + newTokens * pin + outAvg * pout) / 1_000_000.0
        val counted = prefs.limitProviders.split(',').map { it.trim() }.let { it.isEmpty() || prefs.providerId in it }
        val share = if (counted && prefs.limit5h > 0) usd / prefs.limit5h * 100 else null
        "≈ " + (if (usd < 0.01) "<$0.01" else "$%.2f".format(java.util.Locale.US, usd)) +
            (share?.let { " · " + (if (it < 0.1) "<0.1" else "%.1f".format(java.util.Locale.US, it)) + "% of 5h" } ?: "")
    }
    val mod = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 2.dp)
    when (look) {
        "hairline" -> Txt(hint.uppercase(), mod, size = 10.sp, color = c.textDim, family = Oc.label, align = androidx.compose.ui.text.style.TextAlign.End)
        "terminal" -> Txt("~ " + hint.removePrefix("≈ "), mod, size = 12.sp, color = c.textDim, family = Oc.mono, align = androidx.compose.ui.text.style.TextAlign.End)
        "notebook" -> androidx.compose.material3.Text(hint, mod, fontSize = 13.sp, color = c.textDim, fontFamily = Oc.reply, fontStyle = FontStyle.Italic, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        else -> Txt(hint, mod, size = 12.sp, color = c.textDim, align = androidx.compose.ui.text.style.TextAlign.End)
    }
}
