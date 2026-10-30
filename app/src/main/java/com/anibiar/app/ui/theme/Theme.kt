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
import androidx.compose.ui.unit.sp
import com.anibiar.app.R

/** Onyx palette sampled from the Miruro reference screenshots. */
object Ani {
    val Onyx = Color(0xFF121214)
    val Panel = Color(0xFF151517)
    val Item = Color(0xFF1A1A1C)
    val Border = Color(0xFF343338)
    val Chip = Color(0xFF1C1B20)
    val ChipOnItem = Color(0xFF26252A)
    val ButtonDark = Color(0xFF232325)
    val Muted = Color(0xFF9B98A0)
    val Soft = Color(0xFFD7D3DA)
    val Lavender = Color(0xFFB9A1DF)
    val Lime = Color(0xFFAAFF01)
    val Amber = Color(0xFFFEA500)
}

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Text style helper: every text in the app uses Inter. */
fun inter(
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Float = 0f,
    letterSpacing: Float = 0f,
): TextStyle = TextStyle(
    fontFamily = InterFamily,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = if (lineHeight > 0f) lineHeight.sp else (size * 1.3f).sp,
    letterSpacing = letterSpacing.sp,
)

@Composable
fun AniBiarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ani.Lavender,
            background = Ani.Onyx,
            surface = Ani.Onyx,
            onBackground = Color.White,
            onSurface = Color.White,
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
