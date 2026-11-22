package app.waylink.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import app.waylink.core.Fmt
import app.waylink.core.Trip
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max

/**
 * A picture of one ride to share: the route drawn on paper, the distance, time, average and top speed, the bike
 * and the date. Drawn with plain Android Canvas at 1080 × 1350 (a 4:5 post), saved to the cache and shared through
 * the app's file provider. No map tiles, so nothing about where you live leaves the phone unless the route shows it.
 */
object RideCard {
    fun share(ctx: Context, t: Trip, bike: String, f: Fmt, accent: Int): Boolean = runCatching {
        val bmp = draw(t, bike, f, accent)
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "waylink-ride-${t.n}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "${t.title} · ${f.num(t.km, 1)} km on my $bike").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Share ride").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    fun draw(t: Trip, bike: String, f: Fmt, accent: Int): Bitmap {
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(Color.rgb(0xF3, 0xEE, 0xE4))
        val ink = Color.rgb(0x1C, 0x1A, 0x17); val ink3 = Color.rgb(0x7A, 0x73, 0x68)
        val sans = Typeface.create("sans-serif", Typeface.NORMAL); val bold = Typeface.create("sans-serif", Typeface.BOLD)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // The route, fitted into the top panel, on a card.
        val box = RectF(60f, 60f, w - 60f, 760f)
        p.color = Color.WHITE; p.alpha = 150; cv.drawRoundRect(box, 48f, 48f, p); p.alpha = 255
        val pts = t.pts.filter { it.size >= 2 }
        if (pts.size >= 2) {
            val lat0 = pts.sumOf { it[0] } / pts.size
            val k = cos(Math.toRadians(lat0))
            val xs = pts.map { it[1] * k }; val ys = pts.map { -it[0] }
            val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
            val inner = RectF(box.left + 80, box.top + 80, box.right - 80, box.bottom - 80)
            val s = minOf(inner.width() / max(maxX - minX, 1e-9).toFloat(), inner.height() / max(maxY - minY, 1e-9).toFloat())
            val ox = inner.left + (inner.width() - (maxX - minX).toFloat() * s) / 2; val oy = inner.top + (inner.height() - (maxY - minY).toFloat() * s) / 2
            val path = Path()
            xs.indices.forEach { i -> val x = ox + (xs[i] - minX).toFloat() * s; val y = oy + (ys[i] - minY).toFloat() * s; if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
            p.color = Color.argb(40, 0, 0, 0); p.strokeWidth = 26f; cv.drawPath(path, p)
            p.color = accent; p.strokeWidth = 14f; cv.drawPath(path, p)
            p.style = Paint.Style.FILL
            fun dot(i: Int, fill: Int) { val x = ox + (xs[i] - minX).toFloat() * s; val y = oy + (ys[i] - minY).toFloat() * s; p.color = Color.WHITE; cv.drawCircle(x, y, 22f, p); p.color = fill; cv.drawCircle(x, y, 14f, p) }
            dot(0, ink); dot(xs.lastIndex, accent)
        } else {
            p.color = ink3; p.typeface = sans; p.textSize = 40f; p.textAlign = Paint.Align.CENTER
            cv.drawText("No route recorded", box.centerX(), box.centerY(), p); p.textAlign = Paint.Align.LEFT
        }
        // Title and date.
        p.style = Paint.Style.FILL; p.color = ink; p.typeface = bold; p.textSize = 64f
        cv.drawText(t.title.take(28), 72f, 870f, p)
        p.color = ink3; p.typeface = sans; p.textSize = 36f
        cv.drawText("${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(t.start))} · $bike".take(48), 72f, 930f, p)
        // Four numbers.
        val mins = Math.round(t.sec / 60)
        val time = if (mins < 60) "$mins min" else "${mins / 60} h ${mins % 60} min"
        val stats = listOf("Distance" to "${f.num(t.km, 1)} km", "Time" to time, "Average" to "${f.num(t.avg)} km/h", "Top speed" to "${f.num(t.top)} km/h")
        stats.forEachIndexed { i, (k, v) ->
            val x = 72f + (i % 2) * 480f; val y = 1050f + (i / 2) * 140f
            p.color = ink3; p.typeface = sans; p.textSize = 32f; cv.drawText(k, x, y, p)
            p.color = ink; p.typeface = bold; p.textSize = 56f; cv.drawText(v, x, y + 64f, p)
        }
        p.color = accent; p.typeface = bold; p.textSize = 34f; p.textAlign = Paint.Align.RIGHT
        cv.drawText("Waylink", w - 72f, h - 56f, p)
        return bmp
    }
}
