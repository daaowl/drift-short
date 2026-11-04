package com.localshorts.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Covers the app until the phone's own unlock succeeds. Screenshots stay allowed. */
@Composable
fun LockScreen(iconId: String, onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    val accent = LocalAccent.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Hc.Bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(logoRes(iconId)), contentDescription = "Hollow", modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(18.dp))
        Text("Hollow is locked", color = Hc.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(accent)
                .clickable(onClick = onUnlock)
                .padding(horizontal = 28.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.LOCK, onAccent(accent), size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text("Unlock", color = onAccent(accent), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
