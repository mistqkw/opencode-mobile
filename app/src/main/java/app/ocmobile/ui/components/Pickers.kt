package app.ocmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc

/**
 * Окна выбора («Select model», «Add to session») в стиле выбранной редакции:
 * Claude — нижняя шторка; Terminal — прямоугольное окно как диалог TUI (заголовок и «esc»);
 * Desktop — всплывающая панель со скруглением, как меню в приложении.
 */
@Composable
fun PickerFrame(
    title: String,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    skipHalf: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (Oc.claudeLike) { OcSheet(onDismiss = onDismiss, title = title, onBack = onBack, skipHalf = skipHalf, content = content); return }
    val c = Oc.c
    val terminal = Oc.terminal
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 12.dp).fillMaxWidth().heightIn(max = 600.dp)
                .clip(Oc.round(if (terminal) 0.dp else 30.dp)).background(if (terminal) c.bg else c.surface)
                .border(1.dp, c.border, Oc.round(if (terminal) 0.dp else 30.dp)),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = if (terminal) 16.dp else 8.dp, end = 8.dp, top = 6.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!terminal) {
                    IconBtn(if (onBack != null) Ic.Back else Ic.Close, onBack ?: onDismiss, size = 40.dp)
                    Spacer(Modifier.width(4.dp))
                }
                Txt(title, Modifier.weight(1f), size = if (terminal) 15.sp else 16.sp, weight = FontWeight.Medium, color = if (terminal) c.text else c.text)
                if (terminal) Txt(if (onBack != null) "esc back" else "esc", Modifier.clip(Oc.round(0.dp)).clickable { (onBack ?: onDismiss)() }.padding(horizontal = 12.dp, vertical = 10.dp), size = 13.sp, color = c.textDim)
            }
            content()
        }
    }
}

@Composable
fun PickHeader(text: String) {
    val c = Oc.c
    when {
        Oc.terminal -> Txt(text, Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp), size = 13.sp, color = c.accent, weight = FontWeight.Bold)
        Oc.desktop -> Txt(text, Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp), size = 12.sp, color = c.textDim, weight = FontWeight.Medium)
        else -> SectionLabel(text)
    }
}

@Composable
fun PickSearch(value: String, onChange: (String) -> Unit, placeholder: String) {
    val c = Oc.c
    when {
        Oc.terminal -> Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().height(44.dp).background(c.surface)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(c.link))
            Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Txt(placeholder, size = 14.sp, color = c.textDim)
                BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 14.sp, fontFamily = Oc.body), cursorBrush = SolidColor(c.text), modifier = Modifier.fillMaxWidth())
            }
        }
        Oc.desktop -> Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().height(42.dp).clip(Oc.round(20.dp)).background(c.surfaceHigh).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ico(Ic.Search, tint = c.textDim, size = 18.dp); Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Txt(placeholder, size = 14.sp, color = c.textDim)
                BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 14.sp, fontFamily = Oc.body), cursorBrush = SolidColor(c.text), modifier = Modifier.fillMaxWidth())
            }
        }
        else -> Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { OcTextField(value, onChange, placeholder = placeholder) }
    }
}

/** Строка выбора: у Claude — крупная, у Terminal — одна строка моно, выбранная закрашена акцентом, у Desktop — компактная со значком выбора. */
@Composable
fun PickRow(
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    icon: ImageVector? = null,
    subtitle: String? = null,
    trailing: String? = null,
    badge: String? = null,
    chevron: Boolean = false,
) {
    val c = Oc.c
    when {
        Oc.terminal -> Row(
            Modifier.fillMaxWidth().then(if (selected) Modifier.background(c.accent) else Modifier).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fg = if (selected) c.bg else c.text
            val dim = if (selected) c.bg.copy(alpha = .7f) else c.textDim
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(label, Modifier.weight(1f, fill = false), size = 14.sp, color = fg, weight = if (selected) FontWeight.Bold else null, maxLines = 1)
                    if (badge != null) { Spacer(Modifier.width(8.dp)); Txt(badge, size = 12.sp, color = dim) }
                }
                if (subtitle != null) Txt(subtitle, size = 11.sp, color = dim, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            }
            if (trailing != null) Txt(trailing, Modifier.padding(start = 12.dp).widthIn(max = 150.dp), size = 12.sp, color = dim, maxLines = 1)
            if (chevron) Txt(" >", size = 13.sp, color = dim)
        }
        Oc.desktop -> Row(
            Modifier.padding(horizontal = 6.dp).fillMaxWidth().clip(Oc.round(20.dp)).then(if (selected) Modifier.background(c.surfaceHigh) else Modifier)
                .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) { Ico(icon, tint = c.textDim, size = 19.dp); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(label, Modifier.weight(1f, fill = false), size = 14.sp, maxLines = 1)
                    if (badge != null) { Spacer(Modifier.width(8.dp)); Chip(badge) }
                }
                if (subtitle != null) Txt(subtitle, size = 12.sp, color = c.textDim, maxLines = 1, modifier = Modifier.padding(top = 1.dp))
            }
            if (trailing != null) { Spacer(Modifier.width(8.dp)); Txt(trailing, size = 13.sp, color = c.textDim, maxLines = 1) }
            if (selected) { Spacer(Modifier.width(8.dp)); Ico(Ic.Check, tint = c.link, size = 17.dp) }
            if (chevron) { Spacer(Modifier.width(4.dp)); Ico(Ic.ChevronRight, tint = c.textDim, size = 17.dp) }
        }
        else -> {
            if (trailing != null || chevron) {
                SettingsGroupRow(label, subtitle ?: trailing, icon, chevron, onClick)
            } else ChoiceRow(label, selected, onClick, icon = icon, subtitle = subtitle, badge = badge)
        }
    }
}

@Composable
private fun SettingsGroupRow(label: String, subtitle: String?, icon: ImageVector?, chevron: Boolean, onClick: () -> Unit) {
    SettingsRow(label, subtitle = subtitle, icon = icon, chevron = chevron, onClick = onClick)
}
