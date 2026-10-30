package app.ocmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.ocmobile.BuildConfig
import app.ocmobile.Graph
import app.ocmobile.data.Updater
import app.ocmobile.data.Updater.State
import app.ocmobile.ui.theme.Oc

/** Открыть окно обновления вручную (из «About»). */
object UpdateUi { val open = mutableStateOf(false) }

/**
 * Окно «Доступно обновление»: при запуске само проверяет GitHub и, если есть новая версия,
 * показывает её с кнопкой Install — дальше загрузка с прогрессом и системный установщик.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateHost() {
    val st by Updater.state.collectAsState()
    var dismissed by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { Updater.autoCheck() }
    val r = when (val s = st) {
        is State.Available -> s.r
        is State.Downloading -> s.r
        is State.Installing -> s.r
        is State.Failed -> s.r
        else -> null
    }
    // само по себе окно всплывает только для новой, не пропущенной и не закрытой версии
    val auto = r != null && r.tag != Graph.prefs.updateSkipped && r.tag != dismissed
    if (!UpdateUi.open.value && !auto) return
    if (UpdateUi.open.value && st is State.Idle) LaunchedEffect(Unit) { Updater.check() }

    val c = Oc.c
    val ctx = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var allowed by remember { mutableStateOf(Updater.canInstall(ctx)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) allowed = Updater.canInstall(ctx) }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    fun close() { UpdateUi.open.value = false; if (r != null) dismissed = r.tag }

    ModalBottomSheet(onDismissRequest = ::close, sheetState = sheet, containerColor = c.surface, dragHandle = null, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp)) {
            Txt(
                when (st) {
                    is State.Checking, is State.Idle -> "Checking for updates…"
                    is State.UpToDate -> "You're up to date"
                    is State.Failed -> "Update didn't go through"
                    else -> "Update available"
                },
                size = 22.sp, weight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Txt(
                if (r != null) "v${Updater.current}  →  ${r.name.removePrefix("v").let { "v$it" }}" else "v${Updater.current} is the newest version on GitHub.",
                size = 15.sp, color = c.textDim,
            )
            if (r != null && r.notes.isNotBlank() && st !is State.Failed) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Txt(r.notes.replace("**", "").replace(Regex("(?m)^- "), "•  ").trim(), size = 14.sp, lineHeight = 20.sp)
                }
            }
            (st as? State.Failed)?.let { Spacer(Modifier.height(12.dp)); Txt(it.message, size = 14.sp, color = c.danger, lineHeight = 20.sp) }
            if (BuildConfig.DEBUG && r != null) {
                Spacer(Modifier.height(10.dp))
                Txt("This is a test build: the release app installs next to it.", size = 13.sp, color = c.textDim)
            }
            Spacer(Modifier.height(20.dp))
            when (val s = st) {
                is State.Downloading -> {
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.surfaceHigh)) {
                        Box(Modifier.fillMaxWidth(s.progress.coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(c.accent))
                    }
                    Spacer(Modifier.height(10.dp))
                    Txt("Downloading… ${(s.progress * 100).toInt()}%" + if (s.r.size > 0) "  ·  ${s.r.size / 1_048_576} MB" else "", size = 14.sp, color = c.textDim)
                }
                is State.Installing -> Txt("Opening the installer…", size = 14.sp, color = c.textDim)
                is State.Available, is State.Failed -> if (r == null) Pill("Try again", { Updater.check() }, Modifier.fillMaxWidth())
                    else if (!allowed) {
                        Txt("Android asks once to let OpenCode install updates.", size = 14.sp, color = c.textDim, lineHeight = 20.sp)
                        Spacer(Modifier.height(12.dp))
                        Pill("Allow installs", { Updater.openInstallPermission(ctx) }, Modifier.fillMaxWidth())
                    } else Pill(if (s is State.Failed) "Try again" else "Install", { Updater.install(ctx, r) }, Modifier.fillMaxWidth())
                else -> Pill("Close", ::close, Modifier.fillMaxWidth(), filled = false)
            }
            if (r != null && (st is State.Available || st is State.Failed)) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt("Skip this version", Modifier.clip(CircleShape).tap(scale = 1f) { Graph.prefs.updateSkipped = r.tag; close() }.padding(vertical = 12.dp), size = 15.sp, color = c.textDim)
                    Txt("Later", Modifier.clip(CircleShape).tap(scale = 1f, onClick = ::close).padding(vertical = 12.dp), size = 15.sp, color = c.text)
                }
            }
        }
    }
}
