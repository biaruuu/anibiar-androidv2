package com.anibiar.app.ui.profile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.anibiar.app.social.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A shareable picture of a profile: theme colour, round avatar, name, handle and link. */
object ShareCard {
    suspend fun share(context: Context, p: UserProfile) {
        val file = withContext(Dispatchers.Default) { render(context, p) } ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, p.linkSlug?.takeIf { it.isNotBlank() }?.let { "Add me on Kiro: kiro://u/$it" } ?: "Find me on Kiro")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share profile"))
    }

    private suspend fun render(context: Context, p: UserProfile): File? = runCatching {
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val accent = Looks.theme(p.profileTheme)?.color?.toArgb() ?: 0xFF3D7BFF.toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(accent, 0xFF151517.toInt(), 0xFF0B0B0D.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Avatar (round)
        val cx = w / 2f; val cy = 470f; val r = 190f
        val avatar = p.avatarUrl?.let { url ->
            val res = ImageLoader(context).execute(ImageRequest.Builder(context).data(url).allowHardware(false).size(500).build())
            (res as? SuccessResult)?.drawable?.toBitmap(380, 380, Bitmap.Config.ARGB_8888)
        }
        paint.color = 0x33FFFFFF
        c.drawCircle(cx, cy, r + 10f, paint)
        if (avatar != null) {
            val save = c.save()
            c.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
            c.drawBitmap(avatar, cx - r, cy - r, null)
            c.restoreToCount(save)
        } else {
            paint.color = 0xFF2A2A2E.toInt(); c.drawCircle(cx, cy, r, paint)
            paint.color = Color.WHITE; paint.textSize = 190f; paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.DEFAULT_BOLD
            c.drawText(p.shownName.take(1).uppercase(), cx, cy + 66f, paint)
        }

        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); paint.textSize = 84f
        c.drawText(p.shownName.take(22), cx, 800f, paint)
        paint.color = 0xFFB4B1BA.toInt(); paint.typeface = Typeface.DEFAULT; paint.textSize = 48f
        c.drawText("@${p.username}", cx, 872f, paint)
        p.bio?.takeIf { it.isNotBlank() }?.let { bio ->
            paint.color = 0xFFD8D6DC.toInt(); paint.textSize = 40f
            bio.take(90).chunked(34).take(3).forEachIndexed { i, line -> c.drawText(line, cx, 960f + i * 54f, paint) }
        }
        paint.color = Color.WHITE; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); paint.textSize = 52f
        c.drawText("Kiro · AniBiar TV", cx, 1240f, paint)
        paint.color = 0xFFB4B1BA.toInt(); paint.typeface = Typeface.DEFAULT; paint.textSize = 38f
        p.linkSlug?.takeIf { it.isNotBlank() }?.let { c.drawText("kiro://u/$it", cx, 1296f, paint) }

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        File(dir, "profile.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }.getOrNull()
}
