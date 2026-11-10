package app.ocmobile.ui.components

import java.util.Locale

/**
 * Money, tokens and percents in one format everywhere (the usage panel, the Usage screen, the price hint),
 * with the decimal separator of the phone's locale.
 */
object Fmt {
    private val loc: Locale get() = Locale.getDefault()

    fun money(x: Double): String = "$" + if (x >= 100) String.format(loc, "%.0f", x) else String.format(loc, "%.2f", x)

    /** A price that may be tiny: "<$0.01" instead of "$0.00". */
    fun price(x: Double): String = if (x > 0 && x < 0.01) "<" + money(0.01) else money(x)

    fun tokens(n: Long): String = when {
        n >= 1_000_000 -> String.format(loc, "%.1fM", n / 1_000_000.0)
        n >= 10_000 -> String.format(loc, "%.0fk", n / 1000.0)
        n >= 1000 -> String.format(loc, "%.1fk", n / 1000.0)
        else -> n.toString()
    }

    /** [p] in percent (0..100). */
    fun percent(p: Double, digits: Int = 0): String = String.format(loc, "%.${digits}f%%", p)

    /** "1 reply", "2 replies". */
    fun count(n: Long, one: String, many: String) = "$n " + if (n == 1L) one else many
}
