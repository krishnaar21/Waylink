package app.waylink.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Number and time wording, matching web-v0/js/views.js (num, short, big, tripv, ago).
 * Indian locales group as 12,34,567 like the browser does for en-IN.
 */
class Fmt(locale: String = "en-IN") {
    private val indian = locale.endsWith("IN")

    /** Grouped number with exactly [d] decimals, or an em dash for missing values. */
    fun num(n: Double?, d: Int = 0): String {
        if (n == null || n.isNaN()) return "—"
        val bd = BigDecimal.valueOf(n).setScale(d, RoundingMode.HALF_UP)
        val neg = bd.signum() < 0
        val plain = bd.abs().toPlainString()
        val int = plain.substringBefore('.')
        val frac = if (d > 0) "." + plain.substringAfter('.') else ""
        val r = (if (neg && (int != "0" || frac.any { it in '1'..'9' })) "-" else "") + group(int) + frac
        return r
    }

    fun num(n: Int, d: Int = 0) = num(n.toDouble(), d)

    private fun group(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        var rest = digits.dropLast(3)
        val size = if (indian) 2 else 3
        val parts = ArrayDeque<String>()
        while (rest.length > size) {
            parts.addFirst(rest.takeLast(size)); rest = rest.dropLast(size)
        }
        if (rest.isNotEmpty()) parts.addFirst(rest)
        return (parts + last3).joinToString(",")
    }

    private fun trunc(x: Double, d: Int): Double { val p = Math.pow(10.0, d.toDouble()); return floor(x * p + 1e-9) / p }
    private fun sig3(x: Double): String {
        val v = when { x >= 100 -> trunc(x, 0); x >= 10 -> trunc(x, 1); else -> trunc(x, 2) }
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    /** Compact for big values: 12.2K, 1.4 L, 2.1 Cr. Small values stay whole. */
    fun short(n: Double?): String {
        if (n == null || n.isNaN()) return "—"
        val a = abs(n)
        val sg = if (n < 0) "−" else ""
        if (a < 1e4) return if (a > 0 && a < 1) num(n, 1) else sg + num(floor(a))
        val steps = if (indian) listOf(1e7 to " Cr", 1e5 to " L", 1e3 to "K") else listOf(1e9 to "B", 1e6 to "M", 1e3 to "K")
        for ((v, u) in steps) if (a >= v) return sg + sig3(a / v) + u
        return sg + num(floor(a))
    }

    /** Odometer: grouped up to 99,999, compact above. */
    fun big(n: Double?): String {
        if (n == null || n.isNaN() || abs(n) < 100_000) return num(n)
        val f = floor(n / 1000) * 1000
        val steps = if (indian) listOf(1e7 to "Cr", 1e5 to "L", 1e3 to "K") else listOf(1e9 to "B", 1e6 to "M", 1e3 to "K")
        for ((v, u) in steps) if (f >= v) {
            val x = BigDecimal.valueOf(f / v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            return x + u
        }
        return num(f)
    }

    /** Trip meters: one decimal while small, whole km from 1,000, compact from 10,000. */
    fun tripv(km: Double): String = if (km < 1000) num(km, 1) else short(km)

    fun ago(ts: Long, now: Long = System.currentTimeMillis()): String {
        val m = ((now - ts) / 60000.0).roundToLong()
        if (m < 1) return "just now"
        if (m < 60) return "$m min ago"
        val h = (m / 60.0).roundToLong()
        if (h < 24) return "$h h ago"
        val d = (h / 24.0).roundToLong()
        return if (d == 1L) "yesterday" else "$d days ago"
    }
}

fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

/** First name if the full name is too long for a row. */
fun shortName(n: String): String {
    val t = n.trim().replace(Regex("\\s+"), " ")
    if (t.length <= 14) return t
    val f = t.split(' ').first()
    return if (f.length <= 14) f else f.take(13) + "…"
}
