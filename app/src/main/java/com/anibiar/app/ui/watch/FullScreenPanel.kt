package com.anibiar.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anibiar.app.ui.theme.Ani
import com.anibiar.app.ui.theme.AniIcons
import com.anibiar.app.ui.theme.inter

/** A page that covers the whole screen (like Discord's call screens) instead of a half-height sheet. */
@Composable
fun FullScreenPanel(title: String, onBack: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(Ani.Onyx).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    AniIcons.ArrowBack, "Back", tint = Color.White,
                    modifier = Modifier.padding(start = 8.dp).size(44.dp).clip(CircleShape).clickable(onClick = onBack).padding(10.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(title, style = inter(20f, FontWeight.Bold), color = Color.White)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ani.Border))
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) { content() }
        }
    }
}
