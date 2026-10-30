package app.ocmobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    color: Color = Oc.c.text,
    weight: FontWeight? = null,
    family: FontFamily? = null,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text, modifier = modifier, color = color, fontSize = size, fontWeight = weight,
        fontFamily = family ?: Oc.body, maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        textAlign = align, lineHeight = lineHeight,
    )
}

/** Заголовок с засечками. */
@Composable
fun Heading(text: String, modifier: Modifier = Modifier, size: TextUnit = 26.sp, color: Color = Oc.c.text, align: TextAlign? = null) =
    Txt(text, modifier, size, color, FontWeight.Normal, Oc.heading, align = align, lineHeight = size * 1.22f)

@Composable
fun Ico(v: ImageVector, modifier: Modifier = Modifier, tint: Color = Oc.c.text, size: Dp = 24.dp) {
    androidx.compose.foundation.Image(
        imageVector = v, contentDescription = null, modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
fun rememberHaptic(): () -> Unit {
    val h = LocalHapticFeedback.current
    return { if (Graph.prefs.haptics) h.performHapticFeedback(HapticFeedbackType.LongPress) }
}

@Composable
fun IconBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Oc.c.text,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    bg: Color? = null,
) {
    // Kimi: кнопки верхней панели — круги с обводкой
    if (LocalInTopBar.current && Oc.look.id == "kimi") return KimiCircleBtn(icon, onClick, tint = if (tint == Oc.c.textDim) Oc.c.text else tint)
    Box(
        modifier.size(size).clip(Oc.pill).then(if (bg != null) Modifier.background(bg) else Modifier).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, tint = tint, size = iconSize) }
}

@Composable
fun TopBar(
    title: String? = null,
    left: @Composable (() -> Unit)? = null,
    right: @Composable RowScope.() -> Unit = {},
    serif: Boolean = false,
) {
    Box(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 8.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) { androidx.compose.runtime.CompositionLocalProvider(LocalInTopBar provides true) { left?.invoke() } }
        if (title != null) when (Oc.look.id) {
            // Hairline: моно-заголовок капсом; Messenger: заголовок слева, как в Telegram
            "hairline" -> Txt(title.uppercase(), Modifier.align(Alignment.Center).padding(horizontal = 56.dp), size = 13.sp, family = Oc.label, maxLines = 1)
            "kimi" -> Txt(title, Modifier.align(Alignment.Center).padding(horizontal = 64.dp), size = 20.sp, weight = FontWeight.SemiBold, maxLines = 1)
            "messenger" -> Txt(title, Modifier.align(Alignment.CenterStart).padding(start = if (left != null) 60.dp else 12.dp, end = 56.dp), size = 20.sp, weight = FontWeight.SemiBold, maxLines = 1)
            else -> if (serif) Heading(title, Modifier.align(Alignment.Center).padding(horizontal = 56.dp), size = 22.sp)
                else Txt(title, Modifier.align(Alignment.Center).padding(horizontal = 56.dp), size = 20.sp, weight = FontWeight.Medium, maxLines = 1)
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalInTopBar provides true) {
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (Oc.look.id == "kimi") 8.dp else 0.dp), content = right)
        }
    }
}

@Composable
fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = Oc.c
    val bg = if (filled) c.text else Color.Transparent
    val fg = if (filled) c.bg else c.text
    Row(
        modifier.clip(Oc.pill)
            .then(if (filled) Modifier.background(bg) else Modifier.border(1.dp, c.border, Oc.pill))
            .tap(enabled = enabled, onClick = onClick)
            .height(48.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Ico(icon, tint = fg, size = 22.dp); Spacer(Modifier.width(10.dp)) }
        Txt(text, size = 16.sp, color = fg.copy(alpha = if (enabled) 1f else 0.5f), weight = FontWeight.Medium)
    }
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, danger: Boolean = false) {
    val c = Oc.c
    val shape = Oc.pill
    val fg = if (filled && danger) androidx.compose.ui.graphics.Color.White else if (filled) c.bg else if (danger) c.danger else c.text
    Box(
        modifier.clip(shape)
            .then(if (filled) Modifier.background(if (danger) c.danger else c.text) else Modifier.border(1.dp, if (danger) c.danger.copy(alpha = .5f) else c.border, shape))
            .tap(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, size = 14.sp, color = fg, weight = FontWeight.Medium) }
}

@Composable
fun OcSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Oc.c
    val track by animateColorAsState(if (checked) c.toggleOn else c.surfaceHigh, tween(160), label = "track")
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, tween(160), label = "thumb")
    if (Oc.look.id == "hairline") {
        // Hairline: прямоугольный переключатель из линий
        Box(Modifier.width(48.dp).height(26.dp).border(1.dp, if (checked) c.accent else c.border, RoundedCornerShape(3.dp)).clickable { onChange(!checked) }) {
            Box(Modifier.offset(x * 0.86f + 1.dp, 3.dp).size(20.dp).clip(RoundedCornerShape(2.dp)).background(if (checked) c.accent else c.textDim))
        }
        return
    }
    Box(
        Modifier.width(52.dp).height(32.dp).clip(CircleShape).background(track).clickable { onChange(!checked) },
    ) {
        Box(Modifier.offset(x, 2.dp).size(28.dp).clip(CircleShape).background(if (checked) Color.White else c.textDim.copy(alpha = if (c.isDark) 1f else .8f)))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = Oc.c
    val m = modifier.padding(start = 20.dp, top = 22.dp, bottom = 10.dp)
    when (Oc.look.id) {
        "hairline" -> Txt(text.uppercase(), m, size = 11.sp, color = c.textDim, family = Oc.label)
        "notebook" -> Text(text, m.padding(start = 8.dp), fontSize = 16.sp, color = c.textDim, fontFamily = Oc.reply, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        "messenger" -> Txt(text, m, size = 15.sp, color = c.link, weight = FontWeight.SemiBold)
        "cards" -> Txt(text, m, size = 13.sp, color = c.textDim, weight = FontWeight.SemiBold)
        else -> Txt(text, m, size = 15.sp, color = c.textDim)
    }
}

/** Цветные квадратики под иконками настроек в Messenger — как в Telegram. */
private val tgColors = listOf(0xFFF2564E, 0xFFF49B34, 0xFF45B649, 0xFF3D8BF2, 0xFF9B6BEB, 0xFF3DB4E6, 0xFFEF5DA8, 0xFF5C6BC0)
private fun tgColor(key: String) = Color(tgColors[(key.hashCode() and 0x7fffffff) % tgColors.size])

/** Группа строк в одной «плитке»: между строками щель в 2 dp, края скруглены. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Oc.c
    // щели между строками окрашены фоном группы: так получаются линии 1px без отдельных разделителей
    val m = when (Oc.look.id) {
        "hairline" -> modifier.padding(horizontal = 16.dp).fillMaxWidth().border(1.dp, c.border).background(c.border)
        "notebook" -> modifier.padding(horizontal = 24.dp).fillMaxWidth().background(c.border).padding(vertical = 1.dp)
        "messenger" -> modifier.fillMaxWidth().background(c.border)
        "cards" -> modifier.padding(horizontal = 16.dp).fillMaxWidth()
        "chatgpt" -> modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(Oc.round(18.dp)).background(c.border)
        else -> modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(Oc.round(24.dp))
    }
    val gap = when (Oc.look.id) { "hairline", "notebook", "messenger", "chatgpt" -> 1.dp; "cards" -> 8.dp; else -> 2.dp }
    Column(m, verticalArrangement = Arrangement.spacedBy(gap), content = content)
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = false,
    titleColor: Color = Oc.c.text,
    circleIcon: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Oc.c
    val look = Oc.look.id
    val bg = when (look) {
        "hairline", "notebook" -> modifier.fillMaxWidth().background(c.bg)
        "cards" -> modifier.fillMaxWidth().clip(Oc.round(16.dp)).background(c.surface).border(1.dp, c.border, Oc.round(16.dp))
        else -> modifier.fillMaxWidth().background(c.surface)
    }
    val pad = when (look) {
        "hairline" -> Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        "notebook" -> Modifier.padding(horizontal = 4.dp, vertical = 14.dp)
        "messenger" -> Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
        else -> Modifier.padding(horizontal = 20.dp, vertical = 15.dp)
    }
    Row(
        bg.then(if (onClick != null) Modifier.tap(scale = 1f, onClick = onClick) else Modifier).then(pad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) when {
            look == "messenger" && titleColor == c.text -> {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(tgColor(title)), contentAlignment = Alignment.Center) { Ico(icon, tint = Color.White, size = 19.dp) }
                Spacer(Modifier.width(18.dp))
            }
            look == "cards" -> {
                Box(Modifier.size(36.dp).clip(Oc.round(11.dp)).background(c.progressTrack), contentAlignment = Alignment.Center) { Ico(icon, tint = c.accent, size = 19.dp) }
                Spacer(Modifier.width(14.dp))
            }
            look == "hairline" -> { Ico(icon, tint = c.textDim, size = 18.dp); Spacer(Modifier.width(16.dp)) }
            look == "notebook" -> { Ico(icon, tint = c.accent, size = 20.dp); Spacer(Modifier.width(16.dp)) }
            circleIcon || look == "kimi" -> {
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) { Ico(icon, tint = titleColor, size = 21.dp) }
                Spacer(Modifier.width(16.dp))
            }
            else -> { Ico(icon, tint = titleColor, size = if (look == "chatgpt") 22.dp else 26.dp); Spacer(Modifier.width(18.dp)) }
        }
        Column(Modifier.weight(1f)) {
            when (look) {
                "hairline" -> {
                    Txt(title, size = 15.sp, color = titleColor)
                    if (subtitle != null) Txt(subtitle.uppercase(), size = 10.sp, color = c.textDim, family = Oc.label, modifier = Modifier.padding(top = 3.dp), maxLines = 1)
                }
                "notebook" -> {
                    Txt(title, size = 17.sp, color = titleColor, family = Oc.reply)
                    if (subtitle != null) Text(subtitle, Modifier.padding(top = 1.dp), fontSize = 14.sp, color = c.textDim, fontFamily = Oc.reply, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
                "messenger" -> {
                    Txt(title, size = 16.sp, color = titleColor)
                    if (subtitle != null) Txt(subtitle, size = 13.sp, color = c.link, modifier = Modifier.padding(top = 1.dp), maxLines = 1)
                }
                else -> {
                    Txt(title, size = 17.sp, color = titleColor)
                    if (subtitle != null) Txt(subtitle, size = 14.sp, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
        if (chevron || (onClick != null && trailing == null && look == "hairline")) {
            if (look == "hairline") Txt("→", size = 14.sp, color = c.textDim, family = Oc.label)
            else Ico(Ic.ChevronRight, tint = c.textDim, size = 22.dp)
        }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, shape: Shape = Oc.round(24.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(shape).background(Oc.c.surface), content = content)
}

@Composable
fun Logo(size: Dp = 56.dp, color: Color = Oc.c.accent) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val w = s * 0.095f
        val st = Stroke(width = w, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * .22f, s * .22f); lineTo(s * .58f, s * .5f); lineTo(s * .22f, s * .78f)
        }
        drawPath(p, color, style = st)
        drawLine(color, Offset(s * .62f, s * .8f), Offset(s * .84f, s * .8f), strokeWidth = w, cap = StrokeCap.Round)
    }
}

@Composable
fun Spinner(size: Dp = 22.dp, color: Color = Oc.c.accent, stroke: Dp = 2.5.dp) {
    val t = rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
    Canvas(Modifier.size(size).rotate(a)) {
        val w = stroke.toPx()
        drawArc(color, 0f, 270f, false, topLeft = Offset(w / 2, w / 2), size = Size(this.size.width - w, this.size.height - w), style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** Индикатор работы: «>» и мигающий курсор — как в терминале. */
@Composable
fun PulsingLogo(size: Dp = 22.dp, color: Color = Oc.c.accent) {
    val t = rememberInfiniteTransition(label = "cursor")
    val blink by t.animateFloat(0.1f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "blink")
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val w = s * 0.095f
        val p = androidx.compose.ui.graphics.Path().apply { moveTo(s * .22f, s * .22f); lineTo(s * .58f, s * .5f); lineTo(s * .22f, s * .78f) }
        drawPath(p, color, style = Stroke(w, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawLine(color.copy(alpha = blink), Offset(s * .62f, s * .8f), Offset(s * .84f, s * .8f), strokeWidth = w, cap = StrokeCap.Round)
    }
}

@Composable
fun UsageBar(fraction: Float, modifier: Modifier = Modifier, warn: Boolean = false) {
    val c = Oc.c
    val animated by androidx.compose.animation.core.animateFloatAsState(fraction, tween(380), label = "bar")
    val fill = if (warn) c.warn else c.progressFill
    Box(modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(c.progressTrack)) {
        Box(Modifier.fillMaxWidth(animated.coerceIn(0f, 1f).coerceAtLeast(0.04f)).height(14.dp).clip(CircleShape).background(fill))
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, color: Color = Oc.c.textDim, bg: Color = Oc.c.surfaceHigh) {
    Box(modifier.clip(Oc.pill).background(bg).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Txt(text, size = 13.sp, color = color, weight = FontWeight.Medium)
    }
}

@Composable
fun OcTextField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    password: Boolean = false,
    mono: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val c = Oc.c
    Box(modifier.fillMaxWidth().clip(Oc.round(20.dp)).background(c.surface).border(1.dp, c.border, Oc.round(20.dp)).padding(horizontal = 20.dp, vertical = 16.dp)) {
        if (value.isEmpty()) Txt(placeholder, size = 17.sp, color = c.textDim)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = singleLine, minLines = minLines,
            textStyle = TextStyle(color = c.text, fontSize = 17.sp, fontFamily = if (mono) FontFamily.Monospace else Oc.body),
            cursorBrush = SolidColor(c.text),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    skipHalf: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Oc.c
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipHalf),
        containerColor = c.bg,
        scrimColor = c.scrim,
        shape = if (Oc.terminal) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).width(40.dp).height(5.dp).clip(CircleShape).background(c.border))
        },
    ) {
        Column(Modifier.navigationBarsPadding()) {
            if (title != null) {
                Box(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp)) {
                    IconBtn(if (onBack != null) Ic.Back else Ic.Close, onBack ?: onDismiss, Modifier.align(Alignment.CenterStart))
                    Txt(title, Modifier.align(Alignment.Center), size = 20.sp, weight = FontWeight.Medium)
                }
            }
            content()
        }
    }
}

@Composable
fun OcDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String = "OK",
    onConfirm: (() -> Unit)? = null,
    dismissText: String = "Cancel",
    danger: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = Oc.c
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = Oc.round(28.dp),
        title = { Txt(title, size = 20.sp, weight = FontWeight.Medium) },
        text = content,
        confirmButton = {
            if (onConfirm != null) Box(Modifier.clip(Oc.pill).clickable(onClick = onConfirm).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Txt(confirmText, size = 16.sp, color = if (danger) c.danger else c.link, weight = FontWeight.Medium)
            }
        },
        dismissButton = {
            Box(Modifier.clip(Oc.pill).clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Txt(if (onConfirm == null) "OK" else dismissText, size = 16.sp, color = c.textDim)
            }
        },
    )
}

/** Строка выбора в диалогах/листах: подсветка синим и галочка у выбранного. */
@Composable
fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null, subtitle: String? = null, badge: String? = null) {
    val c = Oc.c
    val col = if (selected) c.link else c.text
    Row(
        Modifier.fillMaxWidth().tap(scale = 1f, onClick = onClick).padding(horizontal = 24.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Ico(icon, tint = col, size = 26.dp); Spacer(Modifier.width(20.dp)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(label, size = 19.sp, color = col)
                if (badge != null) { Spacer(Modifier.width(10.dp)); Chip(badge) }
            }
            if (subtitle != null) Txt(subtitle, size = 14.sp, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
        }
        if (selected) Ico(Ic.Check, tint = c.link, size = 24.dp)
    }
}

@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Oc.c
    Row(modifier.clip(Oc.pill).background(c.surfaceHigh).padding(3.dp)) {
        options.forEach { (key, label) ->
            val sel = key == selected
            Box(
                Modifier.weight(1f).clip(Oc.pill).then(if (sel) Modifier.background(c.bg) else Modifier)
                    .clickable { onSelect(key) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Txt(label, size = 14.sp, color = if (sel) c.text else c.textDim, weight = if (sel) FontWeight.Medium else null) }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Ico(icon, tint = Oc.c.textDim, size = 72.dp)
        Spacer(Modifier.height(24.dp))
        Txt(title, size = 22.sp, color = Oc.c.textDim, align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Txt(text, size = 17.sp, color = Oc.c.textDim.copy(alpha = .85f), align = TextAlign.Center)
    }
}
