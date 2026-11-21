package app.waylink.ui.components

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waylink.ui.theme.T
import app.waylink.ui.theme.W
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * A small street map around one point with a pin in the middle, made of OpenStreetMap tiles (the standard tile
 * server allows light use like this with attribution). No map SDK, no key. Offline it shows a plain grid and the pin.
 */
@Composable
fun MapPeek(lat: Double, lng: Double, modifier: Modifier = Modifier, zoom: Int = 17, label: String = "Map of where you parked") {
    val c = W.c
    // Tile coordinates of the point, as fractions, at this zoom.
    val n = 1 shl zoom
    val fx = (lng + 180.0) / 360.0 * n
    val latR = lat * PI / 180.0
    val fy = (1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0 * n
    val tx = floor(fx).toInt(); val ty = floor(fy).toInt()
    var tiles by remember(tx, ty, zoom) { mutableStateOf<Map<Pair<Int, Int>, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(tx, ty, zoom) {
        if (Build.FINGERPRINT == "robolectric") return@LaunchedEffect
        tiles = Tiles.around(zoom, tx, ty)
    }
    val accent = c.accent; val line = c.inkA(0.08f); val card = c.card
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(c.inkA(0.04f)).semantics { contentDescription = label }) {
        Canvas(Modifier.matchParentSize()) {
            val px = 256.dp.toPx() * 0.75f // a 256 tile drawn a little smaller, so streets read at phone size
            val cx = size.width / 2f; val cy = size.height / 2f
            if (tiles.isEmpty()) {
                var gx = cx % 40.dp.toPx(); while (gx < size.width) { drawLine(line, Offset(gx, 0f), Offset(gx, size.height), 1.dp.toPx()); gx += 40.dp.toPx() }
                var gy = cy % 40.dp.toPx(); while (gy < size.height) { drawLine(line, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx()); gy += 40.dp.toPx() }
            }
            tiles.forEach { (k, img) ->
                val left = cx + ((k.first - fx) * px).toFloat()
                val top = cy + ((k.second - fy) * px).toFloat()
                drawImage(img, dstOffset = IntOffset(left.toInt(), top.toInt()), dstSize = IntSize(px.toInt() + 1, px.toInt() + 1))
            }
            // The pin: a drop with a white dot, its tip on the spot.
            val r = 11.dp.toPx()
            val tip = Offset(cx, cy)
            val head = Offset(cx, cy - r * 2.2f)
            val p = Path().apply {
                moveTo(tip.x, tip.y)
                cubicTo(tip.x - r * 0.9f, tip.y - r * 1.1f, head.x - r, head.y + r * 0.6f, head.x - r, head.y)
                arcTo(androidx.compose.ui.geometry.Rect(head, r), 180f, 180f, false)
                cubicTo(head.x + r, head.y + r * 0.6f, tip.x + r * 0.9f, tip.y - r * 1.1f, tip.x, tip.y)
                close()
            }
            drawCircle(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f), r * 0.5f, Offset(cx, cy + 2.dp.toPx()))
            drawPath(p, accent)
            drawPath(p, card, style = Stroke(2.dp.toPx()))
            drawCircle(card, r * 0.38f, head)
        }
        if (tiles.isNotEmpty()) Txt("© OpenStreetMap", T.rowSub.copy(fontSize = 9.sp, lineHeight = 11.sp), c.ink3,
            Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(4.dp)).background(c.card.copy(alpha = 0.8f)).padding(horizontal = 4.dp, vertical = 1.dp))
    }
}

/** Fetches and keeps a few map tiles in memory for the session. */
private object Tiles {
    private val cache = object : LinkedHashMap<String, ImageBitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 24
    }

    suspend fun around(z: Int, x: Int, y: Int): Map<Pair<Int, Int>, ImageBitmap> = withContext(Dispatchers.IO) {
        val n = 1 shl z
        coroutineScope {
            (-1..1).flatMap { dx -> (-1..1).map { dy -> (x + dx) to (y + dy) } }
                .filter { (_, ty) -> ty in 0 until n }
                .map { k -> async { fetch(z, Math.floorMod(k.first, n), k.second)?.let { k to it } } }
                .awaitAll().filterNotNull().toMap()
        }
    }

    private fun fetch(z: Int, x: Int, y: Int): ImageBitmap? {
        val key = "$z/$x/$y"
        synchronized(cache) { cache[key] }?.let { return it }
        return runCatching {
            val c = java.net.URL("https://tile.openstreetmap.org/$key.png").openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 6000; c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Waylink-Android")
            try {
                if (c.responseCode != 200) return@runCatching null
                c.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            } finally { c.disconnect() }
        }.getOrNull()?.also { img -> synchronized(cache) { cache[key] = img } }
    }
}
