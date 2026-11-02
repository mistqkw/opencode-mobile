package app.ocmobile.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import app.ocmobile.R

class AppIcon(val id: String, val label: String, val component: String, val bg: Color, val fgRes: Int)

/** The app's icon set: each one is a separate activity-alias in the manifest. */
object AppIcons {
    val all = listOf(
    // for the interface looks
    AppIcon("look_cloud", "cl0udly", "app.ocmobile.IconLookCloud", Color(0xFF232428), R.drawable.ic_fg_look_cloud),
    AppIcon("cloud_light", "cl0udly · light", "app.ocmobile.IconCloudLight", Color(0xFFF4F5F7), R.drawable.ic_fg_cloud_light),
    AppIcon("look_claude", "Warm", "app.ocmobile.IconLookClaude", Color(0xFFD97757), R.drawable.ic_fg_look_claude),
    AppIcon("look_terminal", "OpenCode · Terminal", "app.ocmobile.IconLookTerminal", Color(0xFF0A0A0A), R.drawable.ic_fg_look_terminal),
    AppIcon("look_desktop", "OpenCode · Desktop", "app.ocmobile.IconLookDesktop", Color(0xFF1E1E1E), R.drawable.ic_fg_look_desktop),
    AppIcon("look_hairline", "Hairline", "app.ocmobile.IconLookHairline", Color(0xFF000000), R.drawable.ic_fg_look_hairline),
    AppIcon("look_chatgpt", "ChatGPT", "app.ocmobile.IconLookChatgpt", Color(0xFF000000), R.drawable.ic_fg_look_chatgpt),
    AppIcon("look_kimi", "Kimi", "app.ocmobile.IconLookKimi", Color(0xFF111111), R.drawable.ic_fg_look_kimi),
    AppIcon("look_notebook", "Notebook", "app.ocmobile.IconLookNotebook", Color(0xFFF7F3EA), R.drawable.ic_fg_look_notebook),
    // colored
    AppIcon("terracotta", "Terracotta", "app.ocmobile.IconTerracotta", Color(0xFFDE7356), R.drawable.ic_fg_terracotta),
    AppIcon("classic", "Classic", "app.ocmobile.IconClassic", Color(0xFF0E0F13), R.drawable.ic_fg_classic),
    AppIcon("blue", "Blue", "app.ocmobile.IconBlue", Color(0xFF4A7DFB), R.drawable.ic_fg_blue),
    AppIcon("cream", "Cream", "app.ocmobile.IconCream", Color(0xFFF3ECE0), R.drawable.ic_fg_cream),
    AppIcon("cream_blue", "Cream & blue", "app.ocmobile.IconCreamBlue", Color(0xFFF3ECE0), R.drawable.ic_fg_cream_blue),
    AppIcon("graphite", "Graphite", "app.ocmobile.IconGraphite", Color(0xFF1C1D22), R.drawable.ic_fg_graphite),
    AppIcon("green", "Green", "app.ocmobile.IconGreen", Color(0xFF3F8F6B), R.drawable.ic_fg_green),
    AppIcon("mustard", "Mustard", "app.ocmobile.IconMustard", Color(0xFFE5A93D), R.drawable.ic_fg_mustard),
    AppIcon("pink", "Pink", "app.ocmobile.IconPink", Color(0xFFE46A8F), R.drawable.ic_fg_pink),
    AppIcon("teal", "Teal", "app.ocmobile.IconTeal", Color(0xFF2B8A96), R.drawable.ic_fg_teal),
    AppIcon("violet", "Violet", "app.ocmobile.IconViolet", Color(0xFF7B61D9), R.drawable.ic_fg_violet),
    )

    const val DEFAULT = "look_cloud"

    /** Enables the chosen alias and disables the rest; the app doesn't restart. */
    fun apply(ctx: Context, id: String) {
        val pm = ctx.packageManager
        // Enable the new one first, then disable the old ones: otherwise for a moment there's no entry point at all.
        all.sortedByDescending { it.id == id }.forEach { icon ->
            val state = if (icon.id == id) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            pm.setComponentEnabledSetting(ComponentName(ctx, icon.component), state, PackageManager.DONT_KILL_APP)
        }
    }
}
