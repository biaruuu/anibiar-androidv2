package com.anibiar.app.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/** Shrinks picked images so they upload quickly (uploaded to Supabase Storage). */
object ImageProc {
    const val MAX_GIF_BYTES = 4 * 1024 * 1024

    class Banner(val bytes: ByteArray, val mime: String, val ext: String)

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

    /** A square 400px JPEG. */
    fun avatar(context: Context, uri: Uri): ByteArray {
        val src = decode(read(context, uri), 400)
        return jpeg(squareCrop(src, 400), 88)
    }

    const val MAX_VIDEO_BYTES = 10 * 1024 * 1024

    private fun indexOf(b: ByteArray, pat: ByteArray, from: Int = 0, to: Int = b.size): Int {
        var i = from
        val end = minOf(to, b.size) - pat.size
        while (i <= end) { var k = 0; while (k < pat.size && b[i + k] == pat[k]) k++; if (k == pat.size) return i; i++ }
        return -1
    }
    private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)
    private fun isMp4(b: ByteArray) = b.size > 12 && indexOf(b, ascii("ftyp"), 0, 12) == 4

    /** Banner: stills become JPEG; GIF, animated WebP/APNG, videos and motion photos are kept as they are. */
    fun banner(context: Context, uri: Uri): Banner {
        val bytes = read(context, uri)
        val type = context.contentResolver.getType(uri).orEmpty()
        if (bytes.size > 6 && String(bytes, 0, 3, Charsets.US_ASCII) == "GIF") {
            if (bytes.size > MAX_GIF_BYTES) error("That GIF is too large. Pick one under 4 MB.")
            return Banner(bytes, "image/gif", "gif")
        }
        // Animated WebP: RIFF....WEBP with an ANIM chunk in the header area.
        if (bytes.size > 30 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" && indexOf(bytes, ascii("ANIM"), 12, 64) > 0) {
            if (bytes.size > MAX_GIF_BYTES) error("That animation is too large. Pick one under 4 MB.")
            return Banner(bytes, "image/webp", "webp")
        }
        // Animated PNG: an acTL chunk before the image data.
        if (bytes.size > 40 && bytes[1] == 'P'.code.toByte() && indexOf(bytes, ascii("acTL"), 8, 128) > 0) {
            if (bytes.size > MAX_GIF_BYTES) error("That animation is too large. Pick one under 4 MB.")
            return Banner(bytes, "image/png", "png")
        }
        // A video (live photos often arrive as one).
        if (type.startsWith("video/") || isMp4(bytes)) {
            if (bytes.size > MAX_VIDEO_BYTES) error("That video is too large. Pick a short clip under 10 MB.")
            if (!isMp4(bytes)) error("Please use an MP4 video.")
            return Banner(bytes, "video/mp4", "mp4")
        }
        // Motion photo: a JPEG with an MP4 appended after the image.
        val isJpeg = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        if (isJpeg) {
            val ft = indexOf(bytes, ascii("ftypmp4"), 1024).takeIf { it > 0 } ?: indexOf(bytes, ascii("ftypisom"), 1024).takeIf { it > 0 } ?: -1
            if (ft >= 4 && String(bytes, 0, minOf(bytes.size, 200_000), Charsets.ISO_8859_1).let { it.contains("MotionPhoto") || it.contains("MicroVideo") || it.contains("GCamera") }) {
                val video = bytes.copyOfRange(ft - 4, bytes.size)
                if (video.size in 20_000..MAX_VIDEO_BYTES) return Banner(video, "video/mp4", "mp4")
            }
        }
        var bmp = decode(bytes, 1080)
        if (bmp.width > 1080) bmp = Bitmap.createScaledBitmap(bmp, 1080, (bmp.height * 1080f / bmp.width).toInt(), true)
        var q = 82
        var out = jpeg(bmp, q)
        while (out.size > 1500 * 1024 && q > 40) { q -= 10; out = jpeg(bmp, q) }
        return Banner(out, "image/jpeg", "jpg")
    }
}
