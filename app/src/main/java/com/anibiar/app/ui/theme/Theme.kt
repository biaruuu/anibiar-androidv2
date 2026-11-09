package com.anibiar.app.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anibiar.app.R

/** Onyx palette sampled from the Miruro reference screenshots. */
object Ani {
    val Onyx = Color(0xFF0B0B0D)
    val Panel = Color(0xFF1B1B1F)
    val Item = Color(0xFF212125)
    val Border = Color(0xFF3A3A40)
    val Chip = Color(0xFF222226)
    val ChipOnItem = Color(0xFF2C2C31)
    val ButtonDark = Color(0xFF2A2A2E)
    val Muted = Color(0xFF9B98A0)
    val Soft = Color(0xFFD7D3DA)
    val Lavender = Color(0xFFB9A1DF)
    val Lime = Color(0xFFAAFF01)
    val Amber = Color(0xFFFEA500)
}

/** Trebuchet MS, used for video captions (Crunchyroll-style). Bold is synthesized from the regular face. */
val TrebuchetFamily = FontFamily(
    Font(R.font.trebuchet_ms, FontWeight.Normal),
    Font(R.font.trebuchet_ms_italic, FontWeight.Normal, androidx.compose.ui.text.font.FontStyle.Italic),
)

/** Netflix Sans for the whole app (name kept so every `inter(...)` call picks it up). Netflix has no SemiBold, so it uses Bold. */
val InterFamily = FontFamily(
    Font(R.font.netflix_sans_light, FontWeight.Light),
    Font(R.font.netflix_sans_regular, FontWeight.Normal),
    Font(R.font.netflix_sans_medium, FontWeight.Medium),
    Font(R.font.netflix_sans_bold, FontWeight.SemiBold),
    Font(R.font.netflix_sans_bold, FontWeight.Bold),
)

/** Text style helper: every text in the app uses Inter. */
fun inter(
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Float = 0f,
    letterSpacing: Float = if (size >= 16f) -0.01f * size else 0f,
): TextStyle = TextStyle(
    fontFamily = InterFamily,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = if (lineHeight > 0f) lineHeight.sp else (size * 1.3f).sp,
    letterSpacing = letterSpacing.sp,
)

@Composable
fun AniBiarTheme(content: @Composable () -> Unit) {
    val base = androidx.compose.material3.Typography()
    val type = androidx.compose.material3.Typography(
        displayLarge = base.displayLarge.copy(fontFamily = InterFamily), displayMedium = base.displayMedium.copy(fontFamily = InterFamily), displaySmall = base.displaySmall.copy(fontFamily = InterFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = InterFamily), headlineMedium = base.headlineMedium.copy(fontFamily = InterFamily), headlineSmall = base.headlineSmall.copy(fontFamily = InterFamily),
        titleLarge = base.titleLarge.copy(fontFamily = InterFamily), titleMedium = base.titleMedium.copy(fontFamily = InterFamily), titleSmall = base.titleSmall.copy(fontFamily = InterFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = InterFamily), bodyMedium = base.bodyMedium.copy(fontFamily = InterFamily), bodySmall = base.bodySmall.copy(fontFamily = InterFamily),
        labelLarge = base.labelLarge.copy(fontFamily = InterFamily), labelMedium = base.labelMedium.copy(fontFamily = InterFamily), labelSmall = base.labelSmall.copy(fontFamily = InterFamily),
    )
    MaterialTheme(
        typography = type,
        colorScheme = darkColorScheme(
            primary = Ani.Lavender,
            background = Ani.Onyx,
            surface = Ani.Onyx,
            onBackground = Color.White,
            onSurface = Color.White,
            surfaceContainer = Color(0xFF1E1E22),
            surfaceContainerHigh = Color(0xFF232327),
            surfaceContainerHighest = Color(0xFF2A2A2E),
            surfaceVariant = Color(0xFF2A2A2E),
            outline = Color(0xFF3A3A40),
        ),
        shapes = androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Ani.Onyx,
            contentColor = Color.White,
            content = content,
        )
    }
}
