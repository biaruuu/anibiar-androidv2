package com.anibiar.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Flat SVG-path glyphs for streaks, levels, badges, moods and reactions (no emoji anywhere). */
object EngageIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black)).build()

    val Flame by lazy { icon("Flame", "M12,2C12,2 5.5,8.5 5.5,14.5a6.5,6.5 0 0 0 13,0c0,-2.6 -1.4,-4.7 -3,-6.5c-0.1,2 -1,3.2 -2.2,3.6C13.6,8 13,4.6 12,2zM12,20a3,3 0 0 1 -3,-3c0,-1.6 1.4,-2.6 3,-4.5c1.6,1.9 3,2.9 3,4.5a3,3 0 0 1 -3,3z") }
    val Trophy by lazy { icon("Trophy", "M7,3h10v6a5,5 0 0 1 -10,0zM3.5,5H7v2H5.5a1.5,1.5 0 0 0 1.6,2L7,11C4.8,10.6 3.5,8.9 3.5,7zM20.5,5H17v2h1.5a1.5,1.5 0 0 1 -1.6,2L17,11c2.2,-0.4 3.5,-2.1 3.5,-4zM11,14h2v3h3v2H8v-2h3z") }
    val Bolt by lazy { icon("Bolt", "M13,2L4,14h6l-1,8 9,-12h-6z") }
    val Moon by lazy { icon("Moon", "M20,14.5A8.5,8.5 0 1 1 9.5,4a6.8,6.8 0 0 0 10.5,10.5z") }
    val Heart by lazy { icon("Heart", "M12,21l-1.5,-1.4C5.4,15 2,12 2,8.5A5.5,5.5 0 0 1 12,6a5.5,5.5 0 0 1 10,2.5c0,3.5 -3.4,6.5 -8.5,11.1z") }
    val Book by lazy { icon("Book", "M5,3h13a1,1 0 0 1 1,1v14H6a1,1 0 0 0 0,2h13v1H6a2,2 0 0 1 -2,-2V4a1,1 0 0 1 1,-1z") }
    val Crown by lazy { icon("Crown", "M3,7l4.5,4L12,4l4.5,7L21,7l-2,12H5z") }
    val Diamond by lazy { icon("Diamond", "M12,2l8,7 -8,13 -8,-13z") }
    val Sparkle by lazy { icon("Sparkle", "M12,2l2.2,7.8L22,12l-7.8,2.2L12,22l-2.2,-7.8L2,12l7.8,-2.2z") }
    val Cup by lazy { icon("Cup", "M4,8h12v6a4,4 0 0 1 -4,4H8a4,4 0 0 1 -4,-4zM16,9.5h1.5a2.5,2.5 0 0 1 0,5H16v-1.6h1.5a0.9,0.9 0 0 0 0,-1.8H16zM5,20h10v1.5H5z") }
    val Drop by lazy { icon("Drop", "M12,2C12,2 5,10 5,15a7,7 0 0 0 14,0C19,10 12,2 12,2z") }
    val Eye by lazy { icon("Eye", "M12,5C6,5 2,12 2,12s4,7 10,7 10,-7 10,-7 -4,-7 -10,-7zM12,16a4,4 0 1 0 0,-8 4,4 0 0 0 0,8z") }
    val Dice by lazy { icon("Dice", "M5,3h14a2,2 0 0 1 2,2v14a2,2 0 0 1 -2,2H5a2,2 0 0 1 -2,-2V5a2,2 0 0 1 2,-2zM8,8.5a1.5,1.5 0 1 0 0.01,0zM16,8.5a1.5,1.5 0 1 0 0.01,0zM8,14.5a1.5,1.5 0 1 0 0.01,0zM16,14.5a1.5,1.5 0 1 0 0.01,0z") }
    val Minus by lazy { icon("Minus", "M5,11h14v2H5z") }
    val Medal by lazy { icon("Medal", "M12,9a5,5 0 1 0 0.01,0zM7,2h3l2,4 -2.5,1zM17,2h-3l-2,4 2.5,1z") }
    val Mountain by lazy { icon("Mountain", "M2,20L9,7l4,7 3,-4 6,10z") }
    val List by lazy { icon("List", "M4,6h16v2H4zM4,11h16v2H4zM4,16h10v2H4z") }
    val Lock by lazy { icon("LockSmall", "M17,9V7a5,5 0 0 0 -10,0v2H5v12h14V9zM9,7a3,3 0 0 1 6,0v2H9z") }
}
