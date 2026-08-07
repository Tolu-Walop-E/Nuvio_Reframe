package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Theme-aware chrome for Netflix surfaces. Near-black cinematic base with
 * accent-tinted wash from the selected [AppTheme].
 */
internal object NetflixThemeChrome {
    val background: Color
        @Composable
        @ReadOnlyComposable
        get() {
            val primary = NuvioTheme.colors.Primary
            return Color(
                red = NetflixHomeTokens.Background.red * 0.88f + primary.red * 0.12f,
                green = NetflixHomeTokens.Background.green * 0.88f + primary.green * 0.12f,
                blue = NetflixHomeTokens.Background.blue * 0.88f + primary.blue * 0.12f,
                alpha = 1f
            )
        }

    val surface: Color
        @Composable
        @ReadOnlyComposable
        get() {
            val primary = NuvioTheme.colors.Primary
            return Color(
                red = NetflixHomeTokens.Surface.red * 0.90f + primary.red * 0.10f,
                green = NetflixHomeTokens.Surface.green * 0.90f + primary.green * 0.10f,
                blue = NetflixHomeTokens.Surface.blue * 0.90f + primary.blue * 0.10f,
                alpha = 1f
            )
        }

    val textPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = NetflixHomeTokens.TextPrimary

    val textSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = NetflixHomeTokens.TextSecondary

    val textMuted: Color
        @Composable
        @ReadOnlyComposable
        get() = NetflixHomeTokens.TextMuted

    val focus: Color
        @Composable
        @ReadOnlyComposable
        get() = NuvioTheme.extendedColors.focusRing

    val accent: Color
        @Composable
        @ReadOnlyComposable
        get() = NuvioTheme.colors.Primary

    /** Resume fill — saturated brand tone, not the neutral Primary gray. */
    val progress: Color
        @Composable
        @ReadOnlyComposable
        get() = NuvioTheme.colors.Secondary
}
