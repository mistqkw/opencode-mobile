package app.ocmobile.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Виды, у которых свой пустой экран вместо надписи OpenCode. */
val lookHomes = setOf("hairline", "notebook", "messenger", "cards", "chatgpt")

private class Idea(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val sub: String, val prompt: String)

private fun ideas(code: Boolean) = if (code) listOf(
    Idea(Ic.Search, "Explain", "this project", "Look around this project and explain how it is put together."),
    Idea(Ic.Wrench, "Fix", "a failing build", "Run the build, find what fails and fix it."),
    Idea(Ic.ListCheck, "Plan", "a new feature", "Help me plan a new feature for this project: "),
    Idea(Ic.Diff, "Review", "my changes", "Review my uncommitted changes and point out problems."),
) else listOf(
    Idea(Ic.Edit, "Write", "or edit a text", "Help me write "),
    Idea(Ic.Globe, "Search", "the web", "Search the web for "),
    Idea(Ic.Sparkle, "Brainstorm", "a few ideas", "Brainstorm ideas for "),
    Idea(Ic.Code, "Code", "a small script", "Write a small script that "),
)

/** Пустой экран под вид: каждый со своим характером, по тапу на подсказку — текст в поле ввода. */
@Composable
fun LookHome(id: String, code: Boolean, onPick: (String) -> Unit) {
    when (id) {
        "hairline" -> HairlineHome(code, onPick)
        "notebook" -> NotebookHome(code, onPick)
        "messenger" -> MessengerHome(code, onPick)
        "cards" -> CardsHome(code, onPick)
        "chatgpt" -> ChatGptHome(code, onPick)
    }
}

// Hairline: моно-подпись, широкий заголовок с одним синим словом, список через линии 1px
@Composable
private fun HairlineHome(code: Boolean, onPick: (String) -> Unit) {
    val c = Oc.c
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Txt(if (code) "OPENCODE / CODE" else "OPENCODE / CHAT", size = 11.sp, color = c.textDim, family = Oc.label)
        Spacer(Modifier.height(14.dp))
        Text(
            buildAnnotatedString {
                append(if (code) "Ship it " else "Ready when ")
                withStyle(SpanStyle(color = c.accent)) { append(if (code) "today." else "you are.") }
            },
            fontFamily = Oc.heading, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, color = c.text,
        )
        Spacer(Modifier.height(26.dp))
        ideas(code).forEach { i ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Row(Modifier.fillMaxWidth().tap(scale = 0.99f) { onPick(i.prompt) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(i.title.uppercase(), Modifier.width(110.dp), size = 12.sp, family = Oc.label)
                Txt(i.sub, Modifier.weight(1f), size = 15.sp, color = c.textDim, maxLines = 1)
                Txt("→", size = 15.sp, color = c.textDim, family = Oc.label)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

// Notebook: дата курсивом, вопрос с засечками, тонкая линейка и «строчки» подсказок
@Composable
private fun NotebookHome(code: Boolean, onPick: (String) -> Unit) {
    val c = Oc.c
    val date = remember { SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH).format(Date()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
        Text(date, fontFamily = Oc.reply, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = c.textDim)
        Spacer(Modifier.height(8.dp))
        Txt(if (code) "What are we building today?" else "What shall we write today?", size = 30.sp, family = Oc.heading, lineHeight = 36.sp)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.width(48.dp).height(2.dp).background(c.accent))
        Spacer(Modifier.height(18.dp))
        ideas(code).forEach { i ->
            Row(Modifier.fillMaxWidth().tap(scale = 0.99f) { onPick(i.prompt) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("—", size = 16.sp, color = c.accent, family = Oc.reply)
                Spacer(Modifier.width(10.dp))
                Text("${i.title} ${i.sub}", fontFamily = Oc.reply, fontStyle = FontStyle.Italic, fontSize = 17.sp, color = c.textDim)
            }
        }
    }
}

// Messenger: «профиль» собеседника и пустой чат с приветствиями-стикерами
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessengerHome(code: Boolean, onPick: (String) -> Unit) {
    val c = Oc.c
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
            Txt("OC", size = 26.sp, color = Color.White, weight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Txt("OpenCode", size = 21.sp, weight = FontWeight.SemiBold)
        Txt(if (code) "online · ready to code" else "online", size = 14.sp, color = c.link)
        Spacer(Modifier.height(22.dp))
        Column(
            Modifier.clip(Oc.round(18.dp)).background(c.surface).padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt("No messages here yet…", size = 15.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Txt("Send a message or tap a greeting below.", size = 14.sp, color = c.textDim, align = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ideas(code).forEach { i ->
                    Txt(i.title, Modifier.clip(Oc.round(14.dp)).background(c.userBubble).tap { onPick(i.prompt) }.padding(horizontal = 12.dp, vertical = 7.dp), size = 14.sp)
                }
            }
        }
    }
}

// Cards: сетка 2×2 из карточек-подсказок
@Composable
private fun CardsHome(code: Boolean, onPick: (String) -> Unit) {
    val c = Oc.c
    val list = ideas(code)
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Txt(if (code) "Start a task" else "Start something", Modifier.padding(start = 4.dp), size = 24.sp, family = Oc.heading, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Txt("Pick a card or just type below.", Modifier.padding(start = 4.dp), size = 14.sp, color = c.textDim)
        Spacer(Modifier.height(16.dp))
        list.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { i ->
                    Column(
                        Modifier.weight(1f).clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp))
                            .tap(scale = 0.97f) { onPick(i.prompt) }.padding(14.dp),
                    ) {
                        Box(Modifier.size(34.dp).clip(Oc.round(10.dp)).background(c.progressTrack), contentAlignment = Alignment.Center) {
                            Ico(i.icon, tint = c.accent, size = 18.dp)
                        }
                        Spacer(Modifier.height(14.dp))
                        Txt(i.title, size = 16.sp, weight = FontWeight.SemiBold)
                        Txt(i.sub, size = 14.sp, color = c.textDim, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ChatGPT: короткий вопрос по центру и чипы-подсказки
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatGptHome(code: Boolean, onPick: (String) -> Unit) {
    val c = Oc.c
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Txt(if (code) "What are we working on?" else "What can I help with?", size = 25.sp, family = Oc.heading, weight = FontWeight.SemiBold, align = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ideas(code).forEach { i ->
                Row(
                    Modifier.clip(CircleShape).border(1.dp, c.border, CircleShape).tap { onPick(i.prompt) }.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ico(i.icon, tint = c.textDim, size = 16.dp)
                    Spacer(Modifier.width(8.dp))
                    Txt("${i.title} ${i.sub}", size = 14.sp, color = c.textDim, maxLines = 1)
                }
            }
        }
    }
}

