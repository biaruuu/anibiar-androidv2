package com.anibiar.app.ui.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32

/** A decoded animated PNG: fully composed frames plus their display time in ms. */
class Apng(val frames: List<Bitmap>, val delays: List<Long>)

object ApngDecoder {
    private val SIG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private class Ctl(val w: Int, val h: Int, val x: Int, val y: Int, val delay: Long, val dispose: Int, val blend: Int) {
        val data = ByteArrayOutputStream()
    }

    fun decode(bytes: ByteArray): Apng? {
        val bb = ByteBuffer.wrap(bytes)
        bb.position(8)
        var ihdr: ByteArray? = null
        val extra = ArrayList<Pair<String, ByteArray>>()   // PLTE, tRNS, ...
        val ctls = ArrayList<Ctl>()
        var cur: Ctl? = null
        var sawActl = false
        while (bb.remaining() >= 12) {
            val len = bb.int
            val type = ByteArray(4).also { bb.get(it) }.toString(Charsets.ISO_8859_1)
            val d = ByteArray(len).also { bb.get(it) }
            bb.int // crc
            when (type) {
                "IHDR" -> ihdr = d
                "PLTE", "tRNS" -> extra.add(type to d)
                "acTL" -> sawActl = true
                "fcTL" -> {
                    val b = ByteBuffer.wrap(d)
                    b.int // sequence
                    val w = b.int; val h = b.int; val x = b.int; val y = b.int
                    val num = b.short.toInt() and 0xFFFF; val den = (b.short.toInt() and 0xFFFF).let { if (it == 0) 100 else it }
                    val dispose = b.get().toInt(); val blend = b.get().toInt()
                    cur = Ctl(w, h, x, y, (num * 1000L / den).coerceAtLeast(20L), dispose, blend).also { ctls.add(it) }
                }
                "IDAT" -> cur?.data?.write(d)
                "fdAT" -> cur?.data?.write(d, 4, d.size - 4)
                "IEND" -> break
            }
        }
        if (!sawActl || ihdr == null || ctls.isEmpty()) return null
        val cw = ByteBuffer.wrap(ihdr).int; val ch = ByteBuffer.wrap(ihdr, 4, 4).int
        val canvasBmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBmp)
        val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        val frames = ArrayList<Bitmap>(); val delays = ArrayList<Long>()
        for (c in ctls) {
            val png = ByteArrayOutputStream()
            png.write(SIG)
            fun chunk(type: String, data: ByteArray) {
                val t = type.toByteArray(Charsets.ISO_8859_1)
                png.write(ByteBuffer.allocate(4).putInt(data.size).array()); png.write(t); png.write(data)
                png.write(ByteBuffer.allocate(4).putInt(CRC32().also { it.update(t); it.update(data) }.value.toInt()).array())
            }
            val h = ihdr.copyOf(); ByteBuffer.wrap(h).putInt(c.w).putInt(c.h)
            chunk("IHDR", h); extra.forEach { chunk(it.first, it.second) }
            chunk("IDAT", c.data.toByteArray()); chunk("IEND", ByteArray(0))
            val fb = BitmapFactory.decodeByteArray(png.toByteArray(), 0, png.size()) ?: continue
            val dst = Rect(c.x, c.y, c.x + c.w, c.y + c.h)
            val backup = if (c.dispose == 2) Bitmap.createBitmap(canvasBmp, 0, 0, cw, ch) else null
            if (c.blend == 0) canvas.drawRect(dst, clear)
            canvas.drawBitmap(fb, c.x.toFloat(), c.y.toFloat(), null)
            frames.add(canvasBmp.copy(Bitmap.Config.ARGB_8888, false)); delays.add(c.delay)
            fb.recycle()
            when (c.dispose) {
                1 -> canvas.drawRect(dst, clear)
                2 -> backup?.let { canvas.drawRect(0f, 0f, cw.toFloat(), ch.toFloat(), clear); canvas.drawBitmap(it, 0f, 0f, null) }
            }
        }
        canvasBmp.recycle()
        return if (frames.isEmpty()) null else Apng(frames, delays)
    }

    fun load(context: Context, asset: String): Apng? =
        try { decode(context.assets.open(asset).use { it.readBytes() }) } catch (_: Throwable) { null }
}

/** Plays an APNG from assets in a loop. Works on every Android version (no ImageDecoder needed). */
@Composable
fun ApngImage(asset: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val apng by produceState<Apng?>(null, asset) { value = withContext(Dispatchers.Default) { ApngDecoder.load(context, asset) } }
    var index by remember { mutableStateOf(0) }
    val a = apng ?: return
    LaunchedEffect(a) {
        while (true) { delay(a.delays[index % a.frames.size]); index = (index + 1) % a.frames.size }
    }
    Image(a.frames[index % a.frames.size].asImageBitmap(), contentDescription, modifier, contentScale = ContentScale.Fit)
}
