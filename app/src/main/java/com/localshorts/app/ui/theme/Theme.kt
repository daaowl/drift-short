package com.localshorts.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.localshorts.app.model.AccentPreset
import com.localshorts.app.model.GradientPreset
import com.localshorts.app.model.IconStylePreset

data class HollowExtendedColors(
   val accentPrimary: Color = AccentPreset.VIOLET.primaryColor,
   val accentSecondary: Color = AccentPreset.VIOLET.secondaryColor,
   val gradientTop: Color = GradientPreset.OBSIDIAN.topColor,
   val gradientBottom: Color = GradientPreset.OBSIDIAN.bottomColor,
   val surfaceCard: Color = Color(0xFF16171F),
   val surfaceElevated: Color = Color(0xFF20222D),
   val textPrimary: Color = Color(0xFFF5F5F7),
   val textSecondary: Color = Color(0xFFA0A3B1),
   val iconStyle: IconStylePreset = IconStylePreset.SOFT_GLASS
)

val LocalHollowColors = staticCompositionLocalOf { HollowExtendedColors() }

@Composable
fun HollowTheme(
   accentPreset: AccentPreset = AccentPreset.VIOLET,
   gradientPreset: GradientPreset = GradientPreset.OBSIDIAN,
   iconStylePreset: IconStylePreset = IconStylePreset.SOFT_GLASS,
   content: @Composable () -> Unit
) {
   val colorScheme = darkColorScheme(
       primary = accentPreset.primaryColor,
       secondary = accentPreset.secondaryColor,
       background = gradientPreset.bottomColor,
       surface = gradientPreset.topColor,
       onPrimary = Color(0xFF0A0A0E),
       onSecondary = Color.White,
       onBackground = Color(0xFFF5F5F7),
       onSurface = Color(0xFFF5F5F7),
       surfaceVariant = Color(0xFF1C1E26),
       onSurfaceVariant = Color(0xFFA6A9B8)
   )

   val extended = HollowExtendedColors(
       accentPrimary = accentPreset.primaryColor,
       accentSecondary = accentPreset.secondaryColor,
       gradientTop = gradientPreset.topColor,
       gradientBottom = gradientPreset.bottomColor,
       surfaceCard = Color(0xFF15161E),
       surfaceElevated = Color(0xFF21232F),
       textPrimary = Color(0xFFF5F5F7),
       textSecondary = Color(0xFFA2A5B5),
       iconStyle = iconStylePreset
   )

   CompositionLocalProvider(LocalHollowColors provides extended) {
       MaterialTheme(
           colorScheme = colorScheme,
           content = content
       )
   }
}
