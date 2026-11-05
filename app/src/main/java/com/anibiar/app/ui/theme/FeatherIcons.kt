package com.anibiar.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Feather-style outline icons (the same set the web app uses), drawn from path data. */
object FeatherIcons {
    private fun icon(name: String, d: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = PathParser().parsePathString(d).toNodes(), fill = null, stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ).build()

    val Repeat by lazy { icon("Repeat", "M17,1L21,5L17,9 M3,11V9a4,4 0 0 1 4,-4h14 M7,23L3,19L7,15 M21,13v2a4,4 0 0 1 -4,4H3") }
    val Eye by lazy { icon("Eye", "M1,12s4,-8 11,-8 11,8 11,8 -4,8 -11,8 -11,-8 -11,-8z M15,12a3,3 0 1,0 -6,0a3,3 0 1,0 6,0") }
    val EyeOff by lazy { icon("EyeOff", "M17.94,17.94A10.07,10.07 0 0 1 12,20c-7,0 -11,-8 -11,-8a18.45,18.45 0 0 1 5.06,-5.94M9.9,4.24A9.12,9.12 0 0 1 12,4c7,0 11,8 11,8a18.5,18.5 0 0 1 -2.16,3.19m-6.72,-1.07a3,3 0 1 1 -4.24,-4.24 M1,1L23,23") }
    val Grid by lazy { icon("Grid", "M3,3h7v7h-7z M14,3h7v7h-7z M14,14h7v7h-7z M3,14h7v7h-7z") }
    val List by lazy { icon("List", "M8,6L21,6 M8,12L21,12 M8,18L21,18 M3,6L3.01,6 M3,12L3.01,12 M3,18L3.01,18") }
    val X by lazy { icon("X", "M18,6L6,18 M6,6L18,18") }
    val Zap by lazy { icon("Zap", "M13,2L3,14L12,14L11,22L21,10L12,10Z") }
    val MoreVertical by lazy { icon("MoreVertical", "M12,5L12.01,5 M12,12L12.01,12 M12,19L12.01,19") }
    val ChevronDown by lazy { icon("ChevronDown", "M6,9L12,15L18,9") }
    val ChevronRight by lazy { icon("ChevronRight", "M9,18L15,12L9,6") }
    val Calendar by lazy { icon("Calendar", "M5,4h14a2,2 0 0 1 2,2v14a2,2 0 0 1 -2,2H5a2,2 0 0 1 -2,-2V6a2,2 0 0 1 2,-2z M16,2L16,6 M8,2L8,6 M3,10L21,10") }
    val Star by lazy { icon("Star", "M12,2L15.09,8.26L22,9.27L17,14.14L18.18,21.02L12,17.77L5.82,21.02L7,14.14L2,9.27L8.91,8.26Z") }
    val Plus by lazy { icon("Plus", "M12,5L12,19 M5,12L19,12") }
}
