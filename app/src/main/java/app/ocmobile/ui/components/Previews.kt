package app.ocmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ocmobile.Graph
import app.ocmobile.data.Prefs
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.LocalOc
import app.ocmobile.ui.theme.Looks

/** The cl0udly look in one theme, for previews (prefs are made on the spot: there's no app in the preview). */
@Composable
private fun CloudPreview(dark: Boolean, content: @Composable () -> Unit) {
    if (!Graph.isPrefsReady()) Graph.prefs = Prefs(LocalContext.current)
    val colors = if (dark) Looks.cloud.dark else Looks.cloud.light
    CompositionLocalProvider(LocalOc provides colors) {
        Column(Modifier.background(colors.bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun Kit() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Spinner()
        OcSwitch(true) {}
        OcSwitch(false) {}
        IconBtn(Ic.Menu, {})
        Monogram("openrouter", "OpenRouter")
        MiniCloudIcon(40.dp)
    }
    SettingsRow("Model", subtitle = "Free model", icon = Ic.Palette, onClick = {}, chevron = true)
    Skeleton(Modifier.width(280.dp), lines = 3)
}

@Preview(name = "cloud dark", widthDp = 360)
@Composable
private fun KitDark() = CloudPreview(true) { Kit() }

@Preview(name = "cloud light", widthDp = 360)
@Composable
private fun KitLight() = CloudPreview(false) { Kit() }
