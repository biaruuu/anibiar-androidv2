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
    val Play by lazy { icon("Play", "M5,3L19,12L5,21Z") }
    val FastForward by lazy { icon("FastForward", "M13,19L22,12L13,5Z M2,19L11,12L2,5Z") }
    val SkipForward by lazy { icon("SkipForward", "M5,4L15,12L5,20Z M19,5L19,19") }
    val Edit by lazy { icon("Edit", "M17,3a2.828,2.828 0 0 1 4,4L7.5,20.5 2,22l1.5,-5.5L17,3z") }
    val Share by lazy { icon("Share", "M4,12v8a2,2 0 0 0 2,2h12a2,2 0 0 0 2,-2v-8 M16,6l-4,-4 -4,4 M12,2v13") }
    val Plus by lazy { icon("Plus", "M12,5L12,19 M5,12L19,12") }
    val Search by lazy { icon("Search", "M19,11a8,8 0 1,1 -16,0a8,8 0 1,1 16,0 M21,21L16.65,16.65") }
    val Sliders by lazy { icon("Sliders", "M4,21L4,14 M4,10L4,3 M12,21L12,12 M12,8L12,3 M20,21L20,16 M20,12L20,3 M1,14L7,14 M9,8L15,8 M17,16L23,16") }
    val Info by lazy { icon("Info", "M22,12a10,10 0 1,1 -20,0a10,10 0 1,1 20,0 M12,16L12,12 M12,8L12.01,8") }
    val Bookmark by lazy { icon("Bookmark", "M19,21l-7,-5 -7,5V5a2,2 0 0 1 2,-2h10a2,2 0 0 1 2,2z") }
    val XCircle by lazy { icon("XCircle", "M22,12a10,10 0 1,1 -20,0a10,10 0 1,1 20,0 M15,9L9,15 M9,9L15,15") }
    val Check by lazy { icon("Check", "M20,6L9,17L4,12") }
    val Home by lazy { icon("Home", "M3,9l9,-7 9,7v11a2,2 0 0 1 -2,2H5a2,2 0 0 1 -2,-2z M9,22V12h6v10") }
    val Message by lazy { icon("Message", "M21,15a2,2 0 0 1 -2,2H7l-4,4V5a2,2 0 0 1 2,-2h14a2,2 0 0 1 2,2z") }
    val User by lazy { icon("User", "M20,21v-2a4,4 0 0 0 -4,-4H8a4,4 0 0 0 -4,4v2 M16,7a4,4 0 1,1 -8,0a4,4 0 1,1 8,0") }
    val Clock by lazy { icon("Clock", "M22,12a10,10 0 1,1 -20,0a10,10 0 1,1 20,0 M12,6L12,12L16,14") }
    val ChevronLeft by lazy { icon("ChevronLeft", "M15,18L9,12L15,6") }
    val Settings by lazy { icon("Settings", "M15,12a3,3 0 1,1 -6,0a3,3 0 1,1 6,0 M19.4,15a1.65,1.65 0 0 0 0.33,1.82l0.06,0.06a2,2 0 0 1 0,2.83 2,2 0 0 1 -2.83,0l-0.06,-0.06a1.65,1.65 0 0 0 -1.82,-0.33 1.65,1.65 0 0 0 -1,1.51V21a2,2 0 0 1 -2,2 2,2 0 0 1 -2,-2v-0.09A1.65,1.65 0 0 0 9,19.4a1.65,1.65 0 0 0 -1.82,0.33l-0.06,0.06a2,2 0 0 1 -2.83,0 2,2 0 0 1 0,-2.83l0.06,-0.06a1.65,1.65 0 0 0 0.33,-1.82 1.65,1.65 0 0 0 -1.51,-1H3a2,2 0 0 1 -2,-2 2,2 0 0 1 2,-2h0.09A1.65,1.65 0 0 0 4.6,9a1.65,1.65 0 0 0 -0.33,-1.82l-0.06,-0.06a2,2 0 0 1 0,-2.83 2,2 0 0 1 2.83,0l0.06,0.06a1.65,1.65 0 0 0 1.82,0.33H9a1.65,1.65 0 0 0 1,-1.51V3a2,2 0 0 1 2,-2 2,2 0 0 1 2,2v0.09a1.65,1.65 0 0 0 1,1.51 1.65,1.65 0 0 0 1.82,-0.33l0.06,-0.06a2,2 0 0 1 2.83,0 2,2 0 0 1 0,2.83l-0.06,0.06a1.65,1.65 0 0 0 -0.33,1.82V9a1.65,1.65 0 0 0 1.51,1H21a2,2 0 0 1 2,2 2,2 0 0 1 -2,2h-0.09a1.65,1.65 0 0 0 -1.51,1z") }
}
