package com.localshorts.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localshorts.app.R
import com.localshorts.app.data.LookStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild

/** Hollow's palette: deep warm black, layered charcoal, soft ivory ink. */
object Hc {
    val Bg = Color(0xFF09090B)
    val Surface = Color(0xFF151619)
    val Surface2 = Color(0xFF202227)
    val Raised = Color(0xFF2A2D33)
    val Ink = Color(0xFFF3F0EA)
    val Mute = Color(0xFF8D9198)
    val Faint = Color(0xFF5E6269)
    val Hairline = Color(0x17FFFFFF)
    val Glass = Color(0x99121316)
}

/** Editorial serif for the wordmark and headlines, geometric sans for everything else. */
val Serif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

val Sans = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_500, FontWeight.Medium),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold),
    Font(R.font.manrope_800, FontWeight.ExtraBold),
)

val Nunito = FontFamily(
    Font(R.font.nunito_400, FontWeight.Normal),
    Font(R.font.nunito_500, FontWeight.Medium),
    Font(R.font.nunito_600, FontWeight.SemiBold),
    Font(R.font.nunito_700, FontWeight.Bold),
    Font(R.font.nunito_800, FontWeight.ExtraBold),
)

val Outfit = FontFamily(
    Font(R.font.outfit_400, FontWeight.Normal),
    Font(R.font.outfit_500, FontWeight.Medium),
    Font(R.font.outfit_600, FontWeight.SemiBold),
    Font(R.font.outfit_700, FontWeight.Bold),
    Font(R.font.outfit_800, FontWeight.ExtraBold),
)

val Fredoka = FontFamily(
    Font(R.font.fredoka_400, FontWeight.Normal),
    Font(R.font.fredoka_500, FontWeight.Medium),
    Font(R.font.fredoka_600, FontWeight.SemiBold),
    Font(R.font.fredoka_700, FontWeight.Bold),
)

/** How filter tabs are drawn for a look. */
enum class TabStyle { GLASS, CHIPS, UNDERLINE, SEGMENT }

/** Everything a [LookStyle] changes: body font, the headline/wordmark voice, tab style. */
data class Look(
    val style: LookStyle,
    val body: FontFamily,
    /** Big titles: the wordmark, page and sheet titles. */
    val headline: TextStyle,
    val wordmark: String,
    val tabs: TabStyle,
)

fun lookFor(style: LookStyle): Look = when (style) {
    LookStyle.CLASSIC -> Look(style, Sans, TextStyle(fontFamily = Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal), "Hollow", TabStyle.GLASS)
    LookStyle.ROUNDED -> Look(style, Nunito, TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp), "hollow", TabStyle.CHIPS)
    LookStyle.CLEAN -> Look(style, Outfit, TextStyle(fontFamily = Outfit, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp), "hollow", TabStyle.UNDERLINE)
    LookStyle.BOLD -> Look(style, Fredoka, TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp), "hollow", TabStyle.SEGMENT)
    LookStyle.SYSTEM -> Look(style, FontFamily.Default, TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Black, letterSpacing = (-1).sp), "hollow", TabStyle.CHIPS)
}

private fun typographyFor(family: FontFamily): Typography {
    val base = TextStyle(fontFamily = family, letterSpacing = 0.sp)
    return Typography(
        displayLarge = base, displayMedium = base, displaySmall = base,
        headlineLarge = base, headlineMedium = base, headlineSmall = base,
        titleLarge = base, titleMedium = base, titleSmall = base,
        bodyLarge = base, bodyMedium = base, bodySmall = base,
        labelLarge = base, labelMedium = base, labelSmall = base,
    )
}

val LocalLook = staticCompositionLocalOf { lookFor(LookStyle.CLASSIC) }

val LocalAccent = staticCompositionLocalOf { Color(0xFFE5484D) }
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

fun onAccent(accent: Color): Color = if (accent.luminance() > 0.45f) Hc.Bg else Color.White

@Composable
fun HollowTheme(accent: Color, look: LookStyle = LookStyle.CLASSIC, content: @Composable () -> Unit) {
    val l = androidx.compose.runtime.remember(look) { lookFor(look) }
    val type = androidx.compose.runtime.remember(look) { typographyFor(l.body) }
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent(accent),
        background = Hc.Bg,
        onBackground = Hc.Ink,
        surface = Hc.Surface,
        onSurface = Hc.Ink,
        surfaceVariant = Hc.Surface2,
        onSurfaceVariant = Hc.Mute,
    )
    CompositionLocalProvider(LocalAccent provides accent, LocalLook provides l) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}

/**
 * Frosted glass: blurs whatever scrolls underneath (Android 12+), with a dark tint, fine grain and a
 * hairline edge. On older phones it falls back to the tint alone, which still reads as glass.
 */
fun Modifier.glass(
    state: HazeState?,
    shape: Shape,
    tint: Color = Hc.Glass,
    edgeColor: Color = Hc.Hairline,
    fallback: Color? = null,
): Modifier {
    val edge = this.border(0.6.dp, edgeColor, shape)
    // Without real blur (older Android, or no backdrop), lean on a more solid tint so text stays legible
    val solid = fallback ?: tint.copy(alpha = maxOf(tint.alpha, 0.9f))
    return if (state != null && android.os.Build.VERSION.SDK_INT >= 31) {
        edge.hazeChild(state = state, shape = shape, style = HazeStyle(tint = tint, blurRadius = 28.dp, noiseFactor = 0.06f))
    } else {
        edge.background(solid, shape)
    }
}

/** Gentle spring squeeze while pressed, for anything tappable. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.96f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

/** In-app logo for each launcher icon choice. */
fun logoRes(iconId: String): Int = when (iconId) {
    "stack" -> R.drawable.logo_stack
    "orbit" -> R.drawable.logo_orbit
    "mono" -> R.drawable.logo_mono
    "prism" -> R.drawable.logo_prism
    else -> R.drawable.logo_hollow
}

/** Text shadow for labels drawn straight over video. */
val VideoTextShadow = androidx.compose.ui.graphics.Shadow(
    color = Color(0x99000000),
    offset = androidx.compose.ui.geometry.Offset(0f, 1.5f),
    blurRadius = 6f,
)
