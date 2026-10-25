package app.ocmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.data.ScheduledTask
import app.ocmobile.data.Scheduler
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.EmptyState
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.OcSwitch
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.Segmented
import app.ocmobile.ui.components.SmallButton
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc

/** Вкладка Scheduled tasks: промпт в заданное время создаёт новую сессию. */
@Composable
fun ScheduledScreen(nav: Nav, onMenu: () -> Unit) {
    val c = Oc.c
    val ctx = LocalContext.current
    val tasks by Scheduler.tasks.collectAsState()
    var edit by remember { mutableStateOf<ScheduledTask?>(null) }
    var creating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Scheduled tasks", left = { IconBtn(Ic.Menu, onMenu) })
            if (tasks.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(Ic.Clock2, "No scheduled tasks yet", "Run a prompt on a schedule, like a daily summary of your project.")
                }
            } else LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(tasks, key = { it.id }) { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(Oc.round(30.dp)).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .22f)).border(1.dp, c.border.copy(alpha = .6f), Oc.round(30.dp))
                            .tap(scale = 0.985f) { edit = t }.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Txt(t.title, size = 19.sp, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            Txt("%02d:%02d · %s".format(t.hour, t.minute, when (t.repeat) { "daily" -> "every day"; "weekly" -> "every week"; else -> "once" }), size = 16.sp, color = c.textDim)
                        }
                        OcSwitch(t.enabled) { Scheduler.upsert(ctx, t.copy(enabled = it)) }
                    }
                }
            }
            Txt("Tasks run while the app and the server are alive. The app keeps its server running in the background.", Modifier.padding(horizontal = 24.dp).padding(bottom = 100.dp), size = 13.sp, color = c.textDim)
        }
        Pill("New task", { creating = true }, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp), icon = Ic.Plus)
    }
    if (creating) TaskSheet(null) { creating = false }
    edit?.let { t -> TaskSheet(t) { edit = null } }
}

@Composable
private fun TaskSheet(task: ScheduledTask?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(task?.title ?: "") }
    var prompt by remember { mutableStateOf(task?.prompt ?: "") }
    var hour by remember { mutableStateOf((task?.hour ?: 9).toString()) }
    var minute by remember { mutableStateOf((task?.minute ?: 0).toString().padStart(2, '0')) }
    var repeat by remember { mutableStateOf(task?.repeat ?: "daily") }
    OcSheet(onDismiss, if (task == null) "New task" else "Edit task", skipHalf = false) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OcTextField(title, { title = it }, placeholder = "Name")
            OcTextField(prompt, { prompt = it }, placeholder = "What should OpenCode do?", singleLine = false, minLines = 3)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("Time", Modifier.weight(1f), size = 17.sp)
                OcTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, Modifier.width(70.dp))
                Txt(" : ", size = 20.sp)
                OcTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, Modifier.width(70.dp))
            }
            Segmented(listOf("once" to "Once", "daily" to "Daily", "weekly" to "Weekly"), repeat, { repeat = it }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (task != null) SmallButton("Delete", { Scheduler.remove(ctx, task.id); onDismiss() }, danger = true)
                Spacer(Modifier.weight(1f))
                SmallButton("Save", {
                    if (prompt.isBlank()) return@SmallButton
                    Scheduler.upsert(ctx, ScheduledTask(
                        task?.id ?: java.util.UUID.randomUUID().toString().take(8), title.ifBlank { prompt.take(30) }, prompt.trim(),
                        (if (Graph.devices.isRemote) Graph.prefs.currentDir else app.ocmobile.data.CHAT_DIR), (hour.toIntOrNull() ?: 9).coerceIn(0, 23), (minute.toIntOrNull() ?: 0).coerceIn(0, 59), repeat,
                    ))
                    onDismiss()
                }, filled = true)
            }
        }
    }
}
