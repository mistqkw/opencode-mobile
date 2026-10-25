package app.ocmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.runtime.RuntimeState
import app.ocmobile.ui.components.Heading
import app.ocmobile.ui.components.Logo
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.UsageBar
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.launch

/** Первый запуск: ставим Ubuntu и OpenCode внутрь телефона. */
@Composable
fun SetupScreen(rt: RuntimeState) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    val log by Graph.runtime.log.collectAsState()
    Column(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Logo(80.dp)
        Spacer(Modifier.height(28.dp))
        Heading("OpenCode on this phone", size = 30.sp, align = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Txt(
            "The agent runs in a Linux container (Ubuntu via proot) right on your device — no computer needed. " +
                "Setup downloads about 110 MB once.",
            size = 16.sp, color = c.textDim, align = TextAlign.Center, lineHeight = 23.sp,
        )
        Spacer(Modifier.height(32.dp))
        when (rt) {
            is RuntimeState.Installing -> {
                Txt(rt.step, size = 18.sp)
                Spacer(Modifier.height(14.dp))
                UsageBar(rt.progress ?: 0.04f)
                if (rt.detail.isNotBlank()) Txt(rt.detail, size = 14.sp, color = c.textDim, modifier = Modifier.padding(top = 10.dp))
            }
            is RuntimeState.InstallFailed -> {
                Txt(rt.message, size = 15.sp, color = c.danger, align = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Pill("Try again", { scope.launch { Graph.runtime.install() } })
            }
            else -> Pill("Set up", { scope.launch { Graph.runtime.install() } })
        }
        if (log.isNotEmpty() && rt !is RuntimeState.NotInstalled) {
            Spacer(Modifier.height(24.dp))
            Txt(
                log.takeLast(6).joinToString("\n"), size = 11.sp, color = c.textDim, family = Oc.mono, maxLines = 8,
                modifier = Modifier.fillMaxWidth().clip(Oc.round(14.dp)).background(c.codeBg).padding(12.dp),
            )
        }
    }
}
