package com.anibiar.app.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/** Shrinks picked images so they fit comfortably in the Realtime Database (no Storage plan needed). */
object ImageProc {
    const val MAX_GIF_BYTES = 900 * 1024

    class Banner(val bytes: ByteArray, val gif: Boolean)

    private fun read(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't read that image")

    private fun decode(bytes: ByteArray, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("That file isn't a supported image")
    }

    private fun jpeg(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    private fun squareCrop(src: Bitmap, side: Int): Bitmap {
        val s = minOf(src.width, src.height)
        val cropped = Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
        return Bitmap.createScaledBitmap(cropped, side, side, true)
    }

    /** Returns (320px avatar, 96px thumbnail), both JPEG. */
    fun avatar(context: Context, uri: Uri): Pair<ByteArray, ByteArray> {
        val src = decode(read(context, uri), 320)
        return jpeg(squareCrop(src, 320), 88) to jpeg(squareCrop(src, 96), 82)
    }

    fun banner(context: Context, uri: Uri): Banner {
        val bytes = read(context, uri)
        val isGif = context.contentResolver.getType(uri) == "image/gif" ||
            (bytes.size > 6 && String(bytes, 0, 3, Charsets.US_ASCII) == "GIF")
        if (isGif) {
            if (bytes.size > MAX_GIF_BYTES) error("That GIF is too large. Pick one under 900 KB.")
            return Banner(bytes, true)
        }
        var bmp = decode(bytes, 1080)
        if (bmp.width > 1080) bmp = Bitmap.createScaledBitmap(bmp, 1080, (bmp.height * 1080f / bmp.width).toInt(), true)
        var q = 82
        var out = jpeg(bmp, q)
        while (out.size > 700 * 1024 && q > 40) { q -= 10; out = jpeg(bmp, q) }
        return Banner(out, false)
    }
}
