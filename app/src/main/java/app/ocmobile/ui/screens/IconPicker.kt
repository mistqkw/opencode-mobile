package app.ocmobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.AppIcon
import app.ocmobile.ui.theme.AppIcons
import app.ocmobile.ui.theme.Oc

/** Выбор иконки приложения: превью повторяют маску адаптивной иконки (видна центральная часть 72/108). */
@Composable
fun IconPickerSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val c = Oc.c
    OcSheet(onDismiss, "App icon", skipHalf = false) {
        LazyVerticalGrid(
            GridCells.Fixed(3), Modifier.padding(horizontal = 12.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(AppIcons.all, key = { it.id }) { icon ->
                val sel = Graph.prefs.appIcon == icon.id
                Column(
                    Modifier.fillMaxWidth().clip(Oc.round(20.dp))
                        .clickable { Graph.prefs.appIcon = icon.id; AppIcons.apply(ctx, icon.id) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(icon.bg)
                            .border(if (sel) 3.dp else 1.dp, if (sel) c.link else c.border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Image(painterResource(icon.fgRes), null, Modifier.requiredSize(108.dp)) }
                    Txt(icon.label, Modifier.padding(top = 8.dp), size = 14.sp, color = if (sel) c.link else c.text, align = TextAlign.Center, maxLines = 1)
                }
            }
        }
        Txt("The launcher may take a few seconds to refresh the icon.", size = 13.sp, color = c.textDim, modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp))
    }
}
