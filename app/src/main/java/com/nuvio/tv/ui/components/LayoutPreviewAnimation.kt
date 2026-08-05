package com.nuvio.tv.ui.components

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

// Each preview scrolls by a whole number of card periods per cycle so the RepeatMode.Restart loop
// lands on a pixel-identical frame and the snap back is invisible.

/** Animated preview of the classic horizontal row layout: 3 rows, the middle one scrolling. */
@Composable
fun ClassicLayoutPreview(
    modifier: Modifier = Modifier,
    accentColor: Color = NuvioTheme.colors.Primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "classicPreview")
    val scrollOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "classicScroll"
    )

    val bgColor = NuvioTheme.colors.Background
    val cardColor = accentColor.copy(alpha = 0.6f)
    val cardColorDim = accentColor.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(NuvioTheme.radii.sm))
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val rowCount = 3
            val rowSpacing = h * 0.04f
            val rowHeight = (h - rowSpacing * (rowCount + 1)) / rowCount
            val cardWidth = w / 5.5f
            val cardHeight = rowHeight * 0.85f
            val gap = w / 40f
            val step = cardWidth + gap
            val cornerRadius = CornerRadius(h * 0.02f)
            val shift = scrollOffset * step * 2f
            val cardsToFill = (w / step).toInt() + 4

            for (rowIndex in 0 until rowCount) {
                val rowY = rowSpacing + rowIndex * (rowHeight + rowSpacing)
                val cardTop = rowY + (rowHeight - cardHeight) / 2f

                if (rowIndex == 1) {
                    for (i in 0..cardsToFill) {
                        val cardX = gap * 2 + i * step - shift
                        if (cardX + cardWidth > 0f && cardX < w) {
                            drawRoundRect(
                                color = cardColor,
                                topLeft = Offset(cardX, cardTop),
                                size = Size(cardWidth, cardHeight),
                                cornerRadius = cornerRadius
                            )
                        }
                    }
                } else {
                    for (i in 0 until 7) {
                        val cardX = gap * 2 + i * step
                        if (cardX < w) {
                            drawRoundRect(
                                color = cardColorDim,
                                topLeft = Offset(cardX, cardTop),
                                size = Size(cardWidth, cardHeight),
                                cornerRadius = cornerRadius
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Animated preview of the grid layout: a 5-column grid scrolling upward. */
@Composable
fun GridLayoutPreview(
    modifier: Modifier = Modifier,
    accentColor: Color = NuvioTheme.colors.Primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gridPreview")
    val scrollOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // 3-row cycle, so the duration is ~3x to keep the original per-pixel speed.
            animation = tween(8800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gridScroll"
    )

    val bgColor = NuvioTheme.colors.Background
    val cardColor = accentColor.copy(alpha = 0.5f)
    val cardColorAlt = accentColor.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(NuvioTheme.radii.sm))
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val cols = 5
            val cardGap = w * 0.025f
            val cardW = (w - cardGap * (cols + 1)) / cols
            val cardH = cardW * 1.4f
            val rowStep = cardH + cardGap
            val cornerRadius = CornerRadius(h * 0.015f)
            val scrollY = scrollOffset * rowStep * 3f
            val rowsToFill = (h / rowStep).toInt() + 5

            for (row in 0..rowsToFill) {
                val cardY = cardGap + row * rowStep - scrollY
                if (cardY + cardH > 0f && cardY < h) {
                    val color = if (row % 3 < 2) cardColor else cardColorAlt
                    for (col in 0 until cols) {
                        val cardX = cardGap + col * (cardW + cardGap)
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(cardX, cardY),
                            size = Size(cardW, cardH),
                            cornerRadius = cornerRadius
                        )
                    }
                }
            }
        }
    }
}

/** Animated preview of the modern layout: a static hero with a scrolling card row beneath it. */
@Composable
fun ModernLayoutPreview(
    modifier: Modifier = Modifier,
    accentColor: Color = NuvioTheme.colors.Primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "modernPreview")
    val scrollOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // 3-card cycle, so the duration keeps the original per-pixel speed.
            animation = tween(5700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "modernScroll"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(NuvioTheme.radii.sm))
            .background(NuvioTheme.colors.Background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val horizontalPadding = w * 0.05f
            val topPadding = h * 0.06f
            val heroHeight = h * 0.62f
            val rowTop = topPadding + heroHeight + (h * 0.05f)
            val cardHeight = h * 0.24f
            val cardWidth = cardHeight * 1.45f
            val gap = w * 0.03f

            drawRoundRect(
                color = accentColor.copy(alpha = 0.38f),
                topLeft = Offset(horizontalPadding, topPadding),
                size = Size(w - (horizontalPadding * 2f), heroHeight),
                cornerRadius = CornerRadius(h * 0.05f)
            )

            val step = cardWidth + gap
            val cornerRadius = CornerRadius(h * 0.03f)
            val shift = scrollOffset * step * 3f
            val cardsToFill = (w / step).toInt() + 6

            for (i in 0..cardsToFill) {
                val x = horizontalPadding + (i * step) - shift
                if (x + cardWidth > 0f && x < w) {
                    drawRoundRect(
                        color = if (i % 3 == 1) {
                            accentColor.copy(alpha = 0.46f)
                        } else {
                            accentColor.copy(alpha = 0.28f)
                        },
                        topLeft = Offset(x, rowTop),
                        size = Size(cardWidth, cardHeight),
                        cornerRadius = cornerRadius
                    )
                }
            }
        }
    }
}

/** Animated preview of Netflix-style home: top nav, large hero, then scrolling poster rails. */
@Composable
fun NetflixLayoutPreview(
    modifier: Modifier = Modifier,
    accentColor: Color = NuvioTheme.colors.Primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "netflixPreview")
    val scrollOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "netflixScroll"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(NuvioTheme.radii.sm))
            .background(NuvioTheme.colors.Background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pad = w * 0.04f
            val navH = h * 0.10f
            val heroH = h * 0.48f
            val gap = w * 0.02f
            val corner = CornerRadius(h * 0.025f)

            drawRoundRect(
                color = accentColor.copy(alpha = 0.18f),
                topLeft = Offset(pad, h * 0.03f),
                size = Size(w - pad * 2f, navH),
                cornerRadius = CornerRadius(h * 0.04f)
            )
            val pillW = w * 0.10f
            val pillH = navH * 0.42f
            val pillY = h * 0.03f + (navH - pillH) / 2f
            for (i in 0 until 4) {
                drawRoundRect(
                    color = accentColor.copy(alpha = if (i == 0) 0.55f else 0.28f),
                    topLeft = Offset(w * 0.28f + i * (pillW + gap), pillY),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(pillH / 2f)
                )
            }

            val heroTop = h * 0.03f + navH + h * 0.04f
            drawRoundRect(
                color = accentColor.copy(alpha = 0.42f),
                topLeft = Offset(pad, heroTop),
                size = Size(w - pad * 2f, heroH),
                cornerRadius = CornerRadius(h * 0.04f)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.55f),
                topLeft = Offset(pad + w * 0.05f, heroTop + heroH * 0.72f),
                size = Size(w * 0.16f, heroH * 0.14f),
                cornerRadius = CornerRadius(heroH * 0.05f)
            )

            val rowTop = heroTop + heroH + h * 0.05f
            val cardH = h - rowTop - h * 0.04f
            val cardW = cardH * 0.68f
            val step = cardW + gap
            val shift = scrollOffset * step * 3f
            val cardsToFill = (w / step).toInt() + 5
            for (i in 0..cardsToFill) {
                val x = pad + i * step - shift
                if (x + cardW > 0f && x < w) {
                    drawRoundRect(
                        color = accentColor.copy(alpha = if (i % 3 == 0) 0.50f else 0.30f),
                        topLeft = Offset(x, rowTop),
                        size = Size(cardW, cardH),
                        cornerRadius = corner
                    )
                }
            }
        }
    }
}
