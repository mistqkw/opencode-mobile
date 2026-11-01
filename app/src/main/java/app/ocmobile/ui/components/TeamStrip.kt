package app.ocmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.Nav
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc

/**
 * The team in a chat: a line above the input while the chat has helpers (child sessions).
 * "Team · 2 working of 3"; tapping opens a list: who, on which model, working or done.
 * Tapping a helper opens its session, so you can see what it did.
 */
@Composable
fun TeamStrip(id: String, nav: Nav) {
    val c = Oc.c
    val all by Graph.chats.allSessions.collectAsState()
    val statuses by Graph.chats.statuses.collectAsState()
    val kids = remember(all, id) { all.values.filter { it.parentID == id }.sortedBy { it.time.created } }
    if (kids.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val working = kids.count { (statuses[it.id] ?: "idle") != "idle" }
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).clip(CircleShape).background(c.surface).border(1.dp, c.border, CircleShape)
            .tap(scale = 0.97f) { open = true }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(Ic.Users, tint = c.textDim, size = 16.dp); Spacer(Modifier.width(8.dp))
        Txt(if (working > 0) "Team · $working working of ${kids.size}" else "Team · ${kids.size} done", size = 14.sp)
    }
    if (open) PickerFrame("Team", { open = false }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            kids.forEach { s ->
                val busy = (statuses[s.id] ?: "idle") != "idle"
                val model = s.model?.let { m -> m.modelID?.let { Graph.catalog.model(m.providerID, it)?.name ?: it } }
                Row(
                    Modifier.fillMaxWidth().tap(scale = 1f) { open = false; nav.push(app.ocmobile.ui.Route.Chat(s.id)) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (busy) c.accent else c.border))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(s.title.replace(Regex("""\s*\(@[^)]*\)\s*$"""), "").ifBlank { "Helper" }, size = 16.sp, maxLines = 2)
                        Txt(listOfNotNull(s.agent?.removePrefix("team-")?.replace('-', ' '), model, if (busy) "working" else "done").joinToString(" · "), size = 13.sp, color = c.textDim, maxLines = 1)
                    }
                    Ico(Ic.ChevronRight, tint = c.textDim, size = 18.dp)
                }
            }
        }
    }
}
