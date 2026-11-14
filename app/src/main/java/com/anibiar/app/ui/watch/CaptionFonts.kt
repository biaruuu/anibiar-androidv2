package com.anibiar.app.ui.watch

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.anibiar.app.R
import com.anibiar.app.ui.theme.InterFamily
import com.anibiar.app.ui.theme.NetflixFamily
import com.anibiar.app.ui.theme.TrebuchetFamily
import java.io.File

/**
 * Caption fonts the viewer can pick. All bundled faces are open-licensed (OFL / Apache);
 * "Custom file" lets the viewer load any .ttf / .otf of their own.
 */
object CaptionFonts {
    val names = listOf("Netflix Sans", "Inter", "Trebuchet MS", "Roboto", "Open Sans", "Poppins", "Montserrat", "Cinzel", "Roboto Mono", "Comic Neue", "Custom file")
    const val CUSTOM = 10
    val BACKGROUNDS = listOf("Transparent", "Semi-transparent", "Solid")
    val EDGES = listOf("None", "Subtle shadow", "Outline")
    /** Display order for the four sizes; the stored value stays the old index (0 normal, 1 large, 2 extra large, 3 small). */
    val SIZE_ORDER = listOf(3, 0, 1, 2)
    val SIZE_LABELS = mapOf(3 to "Small", 0 to "Medium", 1 to "Large", 2 to "Extra large")

    fun customFile(c: Context) = File(c.filesDir, "caption_font.ttf")

    @OptIn(ExperimentalTextApi::class)
    private fun variable(res: Int, w: Int) = FontFamily(Font(res, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(w))))

    fun family(c: Context, i: Int): FontFamily = when (i) {
        0 -> NetflixFamily
        1 -> InterFamily
        2 -> TrebuchetFamily
        3 -> variable(R.font.cap_roboto, 500)
        4 -> variable(R.font.cap_opensans, 500)
        5 -> FontFamily(Font(R.font.cap_poppins, FontWeight.Medium))
        6 -> variable(R.font.cap_montserrat, 500)
        7 -> variable(R.font.cap_cinzel, 600)
        8 -> variable(R.font.cap_robotomono, 500)
        9 -> FontFamily(Font(R.font.cap_comicneue, FontWeight.Medium))
        else -> runCatching { FontFamily(Typeface.createFromFile(customFile(c))) }.getOrDefault(NetflixFamily)
    }
}
