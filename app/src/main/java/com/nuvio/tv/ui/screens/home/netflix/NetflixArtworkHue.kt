package com.nuvio.tv.ui.screens.home.netflix

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap
import kotlin.math.max

private const val HUE_DEBOUNCE_MS = 40L
private const val HUE_CACHE_SIZE = 160
private const val HUE_CROSSFADE_MS = 350
private val SAMPLE_SIZE = Size(48, 48)

/**
 * Strong cinematic hue wash from the focused poster’s dominant colour.
 * Cache keeps sampling cheap; colour always blends with a buttery crossfade.
 */
@Composable
internal fun NetflixPosterBackdrop(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    accentScrim: Color = Color.Transparent
) {
    val context = LocalContext.current
    val fallback = remember(accentScrim) {
        if (accentScrim.alpha > 0.01f) {
            lerp(Color(0xFF050505), accentScrim, 0.45f)
        } else {
            Color(0xFF050505)
        }
    }

    var targetPrimary by remember { mutableStateOf(fallback) }

    val primary by animateColorAsState(
        targetValue = targetPrimary,
        animationSpec = tween(
            durationMillis = HUE_CROSSFADE_MS,
            easing = FastOutSlowInEasing
        ),
        label = "netflixPosterHuePrimary"
    )
    val secondary = remember(primary) { darken(shiftHue(primary, 18f), 0.42f) }

    LaunchedEffect(imageUrl) {
        val url = imageUrl?.takeIf { it.isNotBlank() }
        if (url == null) {
            targetPrimary = fallback
            return@LaunchedEffect
        }

        NetflixPosterHueCache[url]?.let { cached ->
            targetPrimary = cached
            return@LaunchedEffect
        }

        // Debounce only for cold path so rapid D-pad scrubbing cancels work.
        delay(HUE_DEBOUNCE_MS)
        val sampled = samplePosterPrimaryHue(context, url, fallback)
        if (sampled != null) {
            NetflixPosterHueCache[url] = sampled
            targetPrimary = sampled
        }
    }

    Box(
        modifier = modifier.drawBehind {
            // One draw pass — cheaper than stacked background Boxes.
            drawRect(color = Color.Black)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to primary.copy(alpha = 0.78f),
                    0.28f to primary.copy(alpha = 0.62f),
                    0.58f to secondary.copy(alpha = 0.48f),
                    0.82f to Color.Black.copy(alpha = 0.88f),
                    1f to Color.Black
                )
            )
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to primary.copy(alpha = 0.28f),
                        0.55f to Color.Transparent,
                        1f to secondary.copy(alpha = 0.34f)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height * 0.35f)
                )
            )
            drawRect(color = Color.Black.copy(alpha = 0.28f))
        }
    )
}

/**
 * Process-wide LRU so revisiting a card is an instant snap across recompositions / screens.
 */
private object NetflixPosterHueCache {
    private val lock = Any()
    private val map = object : LinkedHashMap<String, Color>(HUE_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?): Boolean {
            return size > HUE_CACHE_SIZE
        }
    }

    operator fun get(url: String): Color? = synchronized(lock) { map[url] }

    operator fun set(url: String, color: Color) = synchronized(lock) {
        map[url] = color
    }
}

private suspend fun samplePosterPrimaryHue(
    context: Context,
    imageUrl: String,
    fallback: Color
): Color? = withContext(Dispatchers.IO) {
    val bitmap = loadSampleBitmap(context, imageUrl) ?: return@withContext null
    saturate(sampleProminentColor(bitmap) ?: fallback, 0.72f, 0.78f)
}

/**
 * Prefer memory (and disk) — the focused card usually already decoded this URL.
 * Avoids network stalls on tint switches.
 */
private suspend fun loadSampleBitmap(context: Context, imageUrl: String): Bitmap? {
    val memoryFirst = ImageRequest.Builder(context)
        .data(imageUrl)
        .allowHardware(false)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.DISABLED)
        .size(SAMPLE_SIZE)
        .build()
    val memoryResult = runCatching { context.imageLoader.execute(memoryFirst) }.getOrNull()
    val memoryBitmap = ((memoryResult as? SuccessResult)?.image as? BitmapImage)?.bitmap
    if (memoryBitmap != null) return memoryBitmap

    val full = ImageRequest.Builder(context)
        .data(imageUrl)
        .allowHardware(false)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .size(SAMPLE_SIZE)
        .build()
    val result = runCatching { context.imageLoader.execute(full) }.getOrNull()
    return ((result as? SuccessResult)?.image as? BitmapImage)?.bitmap
}

private fun sampleProminentColor(bitmap: Bitmap): Color? {
    if (bitmap.width <= 0 || bitmap.height <= 0) return null
    // Coarse grid — 48px decode + ~6 samples/axis is enough for a wash.
    val stepX = max(1, bitmap.width / 6)
    val stepY = max(1, bitmap.height / 6)
    val hsv = FloatArray(3)
    var weightedRed = 0f
    var weightedGreen = 0f
    var weightedBlue = 0f
    var totalWeight = 0f

    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = android.graphics.Color.alpha(pixel)
            if (alpha < 90) {
                x += stepX
                continue
            }
            android.graphics.Color.colorToHSV(pixel, hsv)
            if (hsv[2] < 0.10f) {
                x += stepX
                continue
            }
            val saturation = hsv[1]
            val value = hsv[2]
            val weight = (alpha / 255f) * (0.25f + saturation * 2.1f) * (0.45f + value)
            weightedRed += android.graphics.Color.red(pixel) * weight
            weightedGreen += android.graphics.Color.green(pixel) * weight
            weightedBlue += android.graphics.Color.blue(pixel) * weight
            totalWeight += weight
            x += stepX
        }
        y += stepY
    }
    if (totalWeight <= 0f) return null
    return Color(
        red = (weightedRed / totalWeight) / 255f,
        green = (weightedGreen / totalWeight) / 255f,
        blue = (weightedBlue / totalWeight) / 255f,
        alpha = 1f
    )
}

private fun saturate(color: Color, minSaturation: Float, targetValue: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255),
        hsv
    )
    hsv[1] = max(hsv[1], minSaturation).coerceIn(0f, 1f)
    hsv[2] = targetValue.coerceIn(0.35f, 0.92f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun shiftHue(color: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255),
        hsv
    )
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun darken(color: Color, amount: Float): Color =
    lerp(color, Color.Black, amount.coerceIn(0f, 1f))
