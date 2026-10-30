package app.ocmobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc

/**
 * Меню «/» над полем ввода, как в Claude: навыки и команды, фильтр по набранному.
 * Выбор подставляет «/имя », дальше можно дописать задачу.
 */
@Composable
fun SlashMenu(state: ComposerState, nav: Nav) {
    val c = Oc.c
    val all by Graph.catalog.commands.collectAsState()
    val t = state.text
    val open = t.startsWith("/") && t.none { it == ' ' || it == '\n' }
    val q = t.drop(1).lowercase()
    val items = if (open) all.filter { it.name.lowercase().contains(q) }
        .sortedWith(compareBy({ !it.name.lowercase().startsWith(q) }, { it.source != "skill" }, { it.name })) else emptyList()
    val look = Oc.look.id
    val lined = look == "hairline" || look == "terminal"
    val shape = when (look) { "terminal" -> RectangleShape; "hairline" -> RoundedCornerShape(2.dp); "kimi" -> RoundedCornerShape(26.dp); else -> Oc.round(22.dp) }
    val mono = if (lined) Oc.mono else null
    AnimatedVisibility(open, enter = fadeIn(tween(120)) + expandVertically(tween(160)), exit = fadeOut(tween(90)) + shrinkVertically(tween(120))) {
        Column(
            Modifier.padding(horizontal = 12.dp).padding(top = 4.dp).fillMaxWidth().clip(shape)
                .background(if (lined) c.bg else c.surface).border(1.dp, c.border, shape)
                .heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
        ) {
            if (items.isEmpty()) Txt(if (all.isEmpty()) "Loading skills…" else "No skill named /$q", Modifier.padding(horizontal = 18.dp, vertical = 12.dp), size = 15.sp, color = c.textDim, family = mono)
            items.forEach { it ->
                Row(
                    Modifier.fillMaxWidth().tap { state.text = "/" + it.name + " " }.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(30.dp).clip(if (lined) shape else RoundedCornerShape(10.dp)).background(c.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Ico(if (it.source == "skill") Ic.Scroll else if (it.source == "mcp") Ic.Puzzle else Ic.Terminal, tint = c.accent, size = 16.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt("/" + it.name, size = 15.sp, weight = FontWeight.Medium, family = mono, maxLines = 1)
                        it.description?.takeIf { d -> d.isNotBlank() }?.let { d -> Txt(d, size = 13.sp, color = c.textDim, maxLines = 1) }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().tap { state.text = ""; nav.push(Route.Skills) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { Ico(Ic.Plus, tint = c.textDim, size = 18.dp) }
                Spacer(Modifier.width(12.dp))
                Txt(if (lined) "MANAGE SKILLS" else "Manage skills", size = if (lined) 12.sp else 15.sp, color = c.textDim, family = mono)
            }
        }
    }
}
