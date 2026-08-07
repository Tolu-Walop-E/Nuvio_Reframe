package com.nuvio.tv.ui.theme

import androidx.compose.ui.graphics.Color
import com.nuvio.tv.domain.model.AppTheme

data class ThemeColorPalette(
    val secondary: Color,
    val secondaryVariant: Color,
    val onSecondary: Color = NuvioPrimitives.white,
    val onSecondaryVariant: Color = NuvioPrimitives.white,
    val focusRing: Color,
    val focusBackground: Color,
    val background: Color = NuvioPrimitives.neutral950,
    val backgroundElevated: Color = NuvioPrimitives.neutral900,
    val backgroundCard: Color = NuvioPrimitives.neutral825,
    val surface: Color = NuvioPrimitives.neutral875,
    val surfaceVariant: Color = NuvioPrimitives.neutral800,
    val panel: Color = NuvioPrimitives.neutral900,
    val overlay: Color = Color(0xD9000000),
    val field: Color = NuvioPrimitives.neutral850,
    val menu: Color = NuvioPrimitives.neutral875,
    val modal: Color = NuvioPrimitives.neutral900,
    val playerOverlay: Color = Color(0xCC000000)
)

object ThemeColors {
    val Crimson = ThemeColorPalette(
        secondary = NuvioPrimitives.red500,
        secondaryVariant = NuvioPrimitives.red600,
        focusRing = NuvioPrimitives.red300,
        focusBackground = Color(0xFF3D1A1A),
        backgroundCard = Color(0xFF241A1A)
    )

    val Ocean = ThemeColorPalette(
        secondary = NuvioPrimitives.blue500,
        secondaryVariant = NuvioPrimitives.blue700,
        focusRing = NuvioPrimitives.blue300,
        focusBackground = Color(0xFF1A2D3D),
        background = Color(0xFF0D0D0F),
        backgroundElevated = Color(0xFF1A1A1E),
        backgroundCard = Color(0xFF1A1F24)
    )

    val Violet = ThemeColorPalette(
        secondary = NuvioPrimitives.violet500,
        secondaryVariant = NuvioPrimitives.violet700,
        focusRing = NuvioPrimitives.violet300,
        focusBackground = Color(0xFF2D1A3D),
        background = Color(0xFF0D0D0F),
        backgroundElevated = Color(0xFF1A1A1E),
        backgroundCard = Color(0xFF1F1A24)
    )

    val Emerald = ThemeColorPalette(
        secondary = NuvioPrimitives.green500,
        secondaryVariant = NuvioPrimitives.green700,
        focusRing = NuvioPrimitives.green300,
        focusBackground = Color(0xFF1A3D1E),
        backgroundCard = Color(0xFF1A241A)
    )

    val Amber = ThemeColorPalette(
        secondary = NuvioPrimitives.amber500,
        secondaryVariant = NuvioPrimitives.amber700,
        focusRing = NuvioPrimitives.amber300,
        focusBackground = Color(0xFF3D2D1A),
        background = Color(0xFF0F0D0D),
        backgroundElevated = Color(0xFF1E1A1A),
        backgroundCard = Color(0xFF24201A)
    )

    val Rose = ThemeColorPalette(
        secondary = NuvioPrimitives.rose500,
        secondaryVariant = NuvioPrimitives.rose700,
        focusRing = NuvioPrimitives.rose300,
        focusBackground = Color(0xFF3D1A2D),
        backgroundCard = Color(0xFF241A1F)
    )

    val White = ThemeColorPalette(
        secondary = NuvioPrimitives.neutral100,
        secondaryVariant = NuvioPrimitives.neutral200,
        onSecondary = NuvioPrimitives.neutral925,
        onSecondaryVariant = NuvioPrimitives.neutral925,
        focusRing = NuvioPrimitives.white,
        focusBackground = Color(0xFF303030),
        backgroundCard = NuvioPrimitives.neutral850
    )

    val Slate = ThemeColorPalette(
        secondary = NuvioPrimitives.slate500,
        secondaryVariant = NuvioPrimitives.slate700,
        focusRing = NuvioPrimitives.slate300,
        focusBackground = Color(0xFF1A222C),
        background = Color(0xFF0B0E12),
        backgroundElevated = Color(0xFF151A21),
        backgroundCard = Color(0xFF1A222C)
    )

    val Cyan = ThemeColorPalette(
        secondary = NuvioPrimitives.cyan500,
        secondaryVariant = NuvioPrimitives.cyan700,
        focusRing = NuvioPrimitives.cyan300,
        focusBackground = Color(0xFF0F2A30),
        backgroundCard = Color(0xFF142428)
    )

    val Gold = ThemeColorPalette(
        secondary = NuvioPrimitives.gold500,
        secondaryVariant = NuvioPrimitives.gold700,
        onSecondary = NuvioPrimitives.neutral925,
        onSecondaryVariant = NuvioPrimitives.neutral925,
        focusRing = NuvioPrimitives.gold300,
        focusBackground = Color(0xFF2E2410),
        backgroundCard = Color(0xFF242014)
    )

    val Magenta = ThemeColorPalette(
        secondary = NuvioPrimitives.magenta500,
        secondaryVariant = NuvioPrimitives.magenta700,
        focusRing = NuvioPrimitives.magenta300,
        focusBackground = Color(0xFF2C1020),
        backgroundCard = Color(0xFF24141C)
    )

    val Graphite = ThemeColorPalette(
        secondary = NuvioPrimitives.graphite500,
        secondaryVariant = NuvioPrimitives.graphite700,
        focusRing = NuvioPrimitives.graphite300,
        focusBackground = Color(0xFF262626),
        background = Color(0xFF0A0A0A),
        backgroundElevated = Color(0xFF161616),
        backgroundCard = Color(0xFF1C1C1C)
    )

    fun getColorPalette(theme: AppTheme): ThemeColorPalette {
        return when (theme) {
            AppTheme.CRIMSON -> Crimson
            AppTheme.OCEAN -> Ocean
            AppTheme.VIOLET -> Violet
            AppTheme.EMERALD -> Emerald
            AppTheme.AMBER -> Amber
            AppTheme.ROSE -> Rose
            AppTheme.WHITE -> White
            AppTheme.SLATE -> Slate
            AppTheme.CYAN -> Cyan
            AppTheme.GOLD -> Gold
            AppTheme.MAGENTA -> Magenta
            AppTheme.GRAPHITE -> Graphite
        }
    }
}
