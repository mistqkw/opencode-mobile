package app.ocmobile.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.data.Chat
import app.ocmobile.data.Team
import app.ocmobile.data.Timeline
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Oc

/**
 * The chat's timeline: who did what and when, on one line down the left, helpers in their own
 * color. On top: how long the whole thing took, how many steps and helpers, and what it cost.
 */
@Composable
fun TimelineSheet(chat: Chat, onDismiss: () -> Unit) {
    val c = Oc.c
    val steps = remember(chat.messages.size, chat.messages.lastOrNull()?.parts?.size) { Timeline.of(chat.messages.toList()) { Team.display(it) } }
    val fmt = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }
    OcSheet(onDismiss, "Timeline", skipHalf = false) {
        val span = if (steps.size > 1) steps.last().at - steps.first().at else 0L
        val helpers = steps.count { it.kind == Timeline.Kind.Helper }
        val cost = steps.sumOf { it.cost }
        Txt(
            listOfNotNull(
                Timeline.duration(span), "${steps.count { it.kind == Timeline.Kind.Tool }} steps",
                if (helpers > 0) "$helpers helpers" else null, if (cost > 0) app.ocmobile.ui.components.Fmt.money(cost) else null,
            ).joinToString("  ·  "),
            Modifier.padding(horizontal = 24.dp, vertical = 4.dp), size = 14.sp, color = c.textDim,
        )
        if (steps.isEmpty()) Txt("Nothing has happened in this chat yet.", Modifier.padding(24.dp), size = 15.sp, color = c.textDim)
        LazyColumn(Modifier.heightIn(max = 620.dp).padding(top = 8.dp, bottom = 24.dp)) {
            items(steps) { s ->
                val dot = when (s.kind) {
                    Timeline.Kind.You -> c.text
                    Timeline.Kind.Reply -> c.accent
                    Timeline.Kind.Helper -> androidx.compose.ui.graphics.Color(0xFF7CC6A4)
                    else -> c.textDim
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Txt(fmt.format(java.util.Date(s.at)), Modifier.width(64.dp).padding(top = 2.dp), size = 12.sp, color = c.textDim, family = Oc.mono)
                    // the line through the day, a dot per step
                    Column(Modifier.width(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.padding(top = 5.dp).size(if (s.kind == Timeline.Kind.Tool || s.kind == Timeline.Kind.Thought) 6.dp else 9.dp).clip(CircleShape).background(dot))
                        Box(Modifier.width(1.dp).height(30.dp).background(c.border))
                    }
                    Column(Modifier.weight(1f).padding(start = 8.dp, bottom = 10.dp)) {
                        if (s.who.isNotBlank()) Txt(s.who, size = 13.sp, weight = FontWeight.Medium, color = if (s.kind == Timeline.Kind.Helper) dot else c.text, maxLines = 1)
                        Txt(s.what, size = 14.sp, color = if (s.kind == Timeline.Kind.You) c.text else c.textDim, maxLines = 2, raw = true)
                    }
                    Txt(
                        when { s.running -> "now"; s.ms != null && s.kind != Timeline.Kind.You -> Timeline.duration(s.ms); else -> "" },
                        Modifier.padding(start = 8.dp, top = 2.dp), size = 12.sp, color = if (s.running) c.accent else c.textDim,
                    )
                }
            }
        }
    }
}
