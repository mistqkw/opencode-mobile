package app.ocmobile.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Линейные иконки 24×24 (контуры в стиле Feather): красятся через tint. */
object Ic {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.75f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private const val CIRCLE = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z"

    val Menu by lazy { icon("menu", "M4 7h16", "M4 12h11", "M4 17h7") }
    val Close by lazy { icon("close", "M18 6L6 18", "M6 6l12 12") }
    val Plus by lazy { icon("plus", "M12 5v14", "M5 12h14") }
    val Back by lazy { icon("back", "M19 12H5", "M12 19l-7-7 7-7") }
    val ArrowUp by lazy { icon("up", "M12 19V5", "M5 12l7-7 7 7") }
    val ChevronRight by lazy { icon("chev_r", "M9 18l6-6-6-6") }
    val ChevronDown by lazy { icon("chev_d", "M6 9l6 6 6-6") }
    val Check by lazy { icon("check", "M20 6L9 17l-5-5") }
    val Search by lazy { icon("search", "M11 3a8 8 0 1 0 0 16a8 8 0 1 0 0-16z", "M21 21l-4.35-4.35") }
    val Copy by lazy { icon("copy", "M11 9h9a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1V10a1 1 0 0 1 1-1z", "M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1") }
    val Trash by lazy { icon("trash", "M3 6h18", "M8 6V4h8v2", "M19 6l-1 14H6L5 6", "M10 11v6", "M14 11v6") }
    val Sliders by lazy { icon("sliders", "M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M1 14h6", "M9 8h6", "M17 16h6") }
    val Clock by lazy { icon("clock", CIRCLE, "M12 7v5l3 2") }
    val Code by lazy { icon("code", "M16 18l6-6-6-6", "M8 6l-6 6 6 6") }
    val Folder by lazy { icon("folder", "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z") }
    val Image by lazy { icon("image", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M8.5 7.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 0 0 0-3z", "M21 15l-5-5L5 21") }
    val FileUp by lazy { icon("file_up", "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z", "M14 3v5h5", "M12 17v-6", "M9 13l3-3 3 3") }
    val File by lazy { icon("file", "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z", "M14 3v5h5", "M9 13h6", "M9 17h6") }
    val Camera by lazy { icon("camera", "M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z", "M12 9a4 4 0 1 0 0 8a4 4 0 0 0 0-8z") }
    val User by lazy { icon("user", CIRCLE, "M12 10a2.6 2.6 0 1 0 0 .01", "M6.5 18.5c1-2.2 3-3.3 5.5-3.3s4.5 1.1 5.5 3.3") }
    val Dollar by lazy { icon("dollar", CIRCLE, "M12 6.5v11", "M14.8 9.4c0-1-1.2-1.9-2.8-1.9s-2.8.8-2.8 1.9 1.2 1.7 2.8 2.1 2.8 1.1 2.8 2.2-1.2 1.9-2.8 1.9-2.8-.9-2.8-1.9") }
    val Drop by lazy { icon("drop", "M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z", "M12 17.2a2.4 2.4 0 0 1-2.2-2.2") }
    val Puzzle by lazy { icon("puzzle", "M4 4h7v7H4z", "M13 13h7v7h-7z", "M4 15h5v5H4z", "M16.5 3a3.5 3.5 0 1 0 0 7a3.5 3.5 0 0 0 0-7z") }
    val Scroll by lazy { icon("scroll", "M8 3h10a2 2 0 0 1 2 2v13", "M8 3a2 2 0 0 0-2 2v13a3 3 0 0 0 3 3h11a2 2 0 0 0 2-2v-1H9", "M10 8h6", "M10 12h6") }
    val Shield by lazy { icon("shield", "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z", "M9 12l2 2 4-4") }
    val Cpu by lazy { icon("cpu", "M6 6h12v12H6z", "M9 9h6v6H9z", "M9 2v4", "M15 2v4", "M9 18v4", "M15 18v4", "M2 9h4", "M2 15h4", "M18 9h4", "M18 15h4") }
    val Terminal by lazy { icon("terminal", "M4 17l6-6-6-6", "M12 19h8") }
    val Pin by lazy { icon("pin", "M12 17v5", "M9 3h6l-1 7 3 3v2H7v-2l3-3z") }
    val Archive by lazy { icon("archive", "M21 8v13H3V8", "M1 3h22v5H1z", "M10 12h4") }
    val Refresh by lazy { icon("refresh", "M23 4v6h-6", "M1 20v-6h6", "M3.5 9a9 9 0 0 1 14.9-3.4L23 10", "M1 14l4.6 4.4A9 9 0 0 0 20.5 15") }
    val Edit by lazy { icon("edit", "M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z") }
    val External by lazy { icon("external", "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6", "M15 3h6v6", "M10 14L21 3") }
    val ListCheck by lazy { icon("list_check", "M3 6l1.5 1.5L7 5", "M3 12l1.5 1.5L7 11", "M3 18l1.5 1.5L7 17", "M11 6h10", "M11 12h10", "M11 18h10") }
    val Warning by lazy { icon("warning", "M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z", "M12 9v4", "M12 17h.01") }
    val Info by lazy { icon("info", CIRCLE, "M12 16v-4", "M12 8h.01") }
    val Sun by lazy { icon("sun", "M12 8a4 4 0 1 0 0 8a4 4 0 0 0 0-8z", "M12 2v2", "M12 20v2", "M4.9 4.9l1.4 1.4", "M17.7 17.7l1.4 1.4", "M2 12h2", "M20 12h2", "M4.9 19.1l1.4-1.4", "M17.7 6.3l1.4-1.4") }
    val Moon by lazy { icon("moon", "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z") }
    val Phone by lazy { icon("phone", "M7 2h10a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z", "M11 18h2") }
    val Vibrate by lazy { icon("vibrate", "M8 4h8a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M3 9v6", "M21 9v6") }
    val Wrench by lazy { icon("wrench", "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94z") }
    val Globe by lazy { icon("globe", CIRCLE, "M3 12h18", "M12 3a14 14 0 0 1 0 18a14 14 0 0 1 0-18z") }
    val Bot by lazy { icon("bot", "M5 8h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z", "M12 2v6", "M9 14h.01", "M15 14h.01") }
    val Chat by lazy { icon("chat", "M21 12a8 8 0 0 1-11.6 7.1L4 20.5l1.4-4.6A8 8 0 1 1 21 12z") }
    val FastForward by lazy { icon("ff", "M3 5l8 7-8 7z", "M12 5l8 7-8 7z") }
    val Sparkle by lazy { icon("sparkle", "M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z") }
    val Download by lazy { icon("download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5", "M12 15V3") }
    val Server by lazy { icon("server", "M4 3h16a1 1 0 0 1 1 1v6H3V4a1 1 0 0 1 1-1z", "M3 14h18v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z", "M7 7h.01", "M7 17h.01") }
    val Key by lazy { icon("key", "M15 3a6 6 0 1 0 0 12a6 6 0 0 0 0-12z", "M10.8 13.2L3 21", "M7 17l2 2", "M5 19l2 2") }
    val Zap by lazy { icon("zap", "M13 2L3 14h8l-1 8 10-12h-8z") }
    val Stop by lazy { icon("stop", "M7 7h10v10H7z") }
    val More by lazy { icon("more", "M12 5h.01", "M12 12h.01", "M12 19h.01") }
    val Eye by lazy { icon("eye", "M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z", "M12 9a3 3 0 1 0 0 6a3 3 0 0 0 0-6z") }
    val FilePlus by lazy { icon("file_plus", "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z", "M14 3v5h5", "M12 12v6", "M9 15h6") }
    val Diff by lazy { icon("diff", "M12 3v10", "M7 8h10", "M7 19h10") }
    val Git by lazy { icon("git", "M6 3v12", "M18 9a3 3 0 1 0 0 .01", "M6 18a3 3 0 1 0 0 .01", "M18 12a9 9 0 0 1-9 9") }
    val Undo by lazy { icon("undo", "M3 7v6h6", "M21 17a9 9 0 0 0-15-6.7L3 13") }
    val Mic by lazy { icon("mic", "M12 2a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z", "M19 10v1a7 7 0 0 1-14 0v-1", "M12 18v4", "M8 22h8") }
    val Ghost by lazy { icon("ghost", "M12 3a7 7 0 0 0-7 7v11l2.3-2 2.3 2 2.4-2 2.4 2 2.3-2 2.3 2V10a7 7 0 0 0-7-7z", "M9.5 10.5h.01", "M14.5 10.5h.01") }
    val IBeam by lazy { icon("ibeam", "M9 4h6", "M12 4v16", "M9 20h6") }
    val Share by lazy { icon("share", "M12 3v12", "M7 8l5-5 5 5", "M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6") }
    val Expand by lazy { icon("expand", "M15 3h6v6", "M9 21H3v-6", "M21 3l-7 7", "M3 21l7-7") }
    val Laptop by lazy { icon("laptop", "M5 5h14a1 1 0 0 1 1 1v10H4V6a1 1 0 0 1 1-1z", "M2 19h20") }
    val Cloud by lazy { icon("cloud", "M7 18a4 4 0 0 1-.5-7.97A6 6 0 0 1 18 9.5a4.25 4.25 0 0 1-.5 8.5z") }
    val Clock2 by lazy { icon("clock2", CIRCLE, "M12 7v5l3 2") }
    val Mail by lazy { icon("mail", "M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M3 7l9 6.5L21 7") }
    /** Знак GitHub — залитый, а не контурный. */
    val GitHub by lazy {
        ImageVector.Builder("github", 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = addPathNodes("M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"),
            fill = SolidColor(Color.Black),
        ).build()
    }
    val Play by lazy { icon("play", "M7 4.5v15l12-7.5z") }
    val Branch by lazy { icon("branch", "M6 3v12", "M18 9a3 3 0 1 0 0 .01", "M6 18a3 3 0 1 0 0 .01", "M18 12a9 9 0 0 1-9 9") }
}
