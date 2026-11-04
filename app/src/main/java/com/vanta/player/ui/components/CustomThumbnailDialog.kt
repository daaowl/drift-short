package com.vanta.player.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vanta.player.data.ThumbnailCacheManager
import com.vanta.player.model.VideoItem
import com.vanta.player.model.formatTimestampMs
import com.vanta.player.ui.theme.LocalVantaColors
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Implements Section 4 (CUSTOM THUMBNAILS — REQUIRED):
 * - Opened from grid long-press or paused player menu.
 * - Large frame preview, timestamp label, and seek slider covering the video's duration.
 * - Moving the slider updates the selected time; releasing it asynchronously renders the selected frame.
 * - Ignores stale preview results via monotonically increasing request token.
 * - Save stores the chosen timestamp for that video. Cancel leaves it unchanged. Use default removes override.
 */
@Composable
fun CustomThumbnailDialog(
    video: VideoItem,
    thumbnailCache: ThumbnailCacheManager,
    onSaveCustomTimestamp: (Long) -> Unit,
    onUseDefault: () -> Unit,
    onCancel: () -> Unit
) {
    val vantaColors = LocalVantaColors.current
    val durationMs = video.durationMs.coerceAtLeast(1000L)

    var sliderPositionMs by remember(video.stableId) {
        mutableFloatStateOf(video.effectiveThumbnailMs.toFloat().coerceIn(0f, durationMs.toFloat()))
    }
    var committedPreviewMs by remember(video.stableId) {
        mutableStateOf(video.effectiveThumbnailMs.coerceIn(0L, durationMs))
    }
    var requestToken by remember { mutableIntStateOf(0) }
    var isRenderingPreview by remember { mutableStateOf(false) }

    var previewBitmap by remember(video.stableId) {
        mutableStateOf<Bitmap?>(thumbnailCache.getMemoryCached(video))
    }

    LaunchedEffect(video.stableId, committedPreviewMs, requestToken) {
        val myToken = requestToken
        isRenderingPreview = true
        val extracted = thumbnailCache.extractPreviewFrame(
            video = video,
            requestedTimestampMs = committedPreviewMs
        )
        // Ignore stale preview results
        if (myToken == requestToken && extracted != null) {
            previewBitmap = extracted
        }
        if (myToken == requestToken) {
            isRenderingPreview = false
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = vantaColors.surfaceCharcoal,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(18.dp)
            ) {
                Text(
                    text = "Change thumbnail",
                    color = vantaColors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.titleWithoutExtension,
                    color = vantaColors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Large 9:16 Frame Preview
                Box(
                    modifier = Modifier
                        .height(290.dp)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = previewBitmap
                    if (bmp != null && !bmp.isRecycled) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Selected frame preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (isRenderingPreview) {
                        CircularProgressIndicator(
                            color = vantaColors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Timestamp readout including tenths of a second
                val secondsFloat = sliderPositionMs / 1000f
                Text(
                    text = "${formatTimestampMs(sliderPositionMs.roundToLong())} (${String.format(Locale.US, "%.1fs", secondsFloat)}) / ${video.formattedDuration}",
                    color = vantaColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(6.dp))

                Slider(
                    value = sliderPositionMs,
                    onValueChange = { newValue ->
                        sliderPositionMs = newValue
                    },
                    onValueChangeFinished = {
                        requestToken += 1
                        committedPreviewMs = sliderPositionMs.roundToLong()
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = vantaColors.accent,
                        activeTrackColor = vantaColors.accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onUseDefault) {
                        Text(
                            text = "Use default",
                            color = vantaColors.textSecondary,
                            fontSize = 13.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onCancel) {
                            Text(
                                text = "Cancel",
                                color = vantaColors.textPrimary,
                                fontSize = 13.sp
                            )
                        }
                        Button(
                            onClick = { onSaveCustomTimestamp(sliderPositionMs.roundToLong()) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = vantaColors.accent,
                                contentColor = Color(0xFF0F0F0F)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Save",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
