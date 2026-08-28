package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.tv.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
internal fun RateAfterWatchingOverlay(
    mode: PostPlayMode.RatePrompt,
    onSelect: (Int) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scoreFocusRequesters = remember { List(10) { FocusRequester() } }
    val submitFocusRequester = remember { FocusRequester() }
    var placedFocused by remember { mutableStateOf(false) }
    val selected = mode.selectedRating.coerceIn(1, 10)

    Box(modifier = modifier.fillMaxSize()) {
        if (!mode.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = mode.artworkUrl,
                contentDescription = mode.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.45f to Color.Black.copy(alpha = 0.62f),
                        1f to Color.Black.copy(alpha = 0.92f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.72f)
                .padding(start = 56.dp, end = 56.dp, bottom = 56.dp)
                .widthIn(max = 920.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = stringResource(R.string.rate_after_watching_title),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = mode.title,
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!mode.subtitle.isNullOrBlank()) {
                Text(
                    text = mode.subtitle,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                (1..10).forEach { score ->
                    val index = score - 1
                    val selectedScore = score == selected
                    Card(
                        onClick = { onSelect(score) },
                        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        colors = CardDefaults.colors(
                            containerColor = if (selectedScore) {
                                Color.White.copy(alpha = 0.24f)
                            } else {
                                Color.White.copy(alpha = 0.08f)
                            },
                            focusedContainerColor = Color.White.copy(alpha = 0.32f)
                        ),
                        border = CardDefaults.border(
                            border = Border(
                                border = BorderStroke(
                                    NuvioTheme.spacing.hairline,
                                    if (selectedScore) {
                                        Color.White.copy(alpha = 0.7f)
                                    } else {
                                        Color.White.copy(alpha = 0.14f)
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                                shape = RoundedCornerShape(12.dp)
                            )
                        ),
                        scale = CardDefaults.scale(focusedScale = 1.08f),
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .focusRequester(scoreFocusRequesters[index])
                            .onPlaced {
                                if (selectedScore && !placedFocused) {
                                    placedFocused = true
                                    runCatching { scoreFocusRequesters[index].requestFocus() }
                                }
                            }
                            .focusProperties {
                                down = submitFocusRequester
                                if (index > 0) left = scoreFocusRequesters[index - 1]
                                if (index < 9) right = scoreFocusRequesters[index + 1]
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = score.toString(),
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = if (selectedScore) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    onClick = onSubmit,
                    shape = CardDefaults.shape(shape = RoundedCornerShape(28.dp)),
                    colors = CardDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        focusedContainerColor = Color.White.copy(alpha = 0.28f)
                    ),
                    border = CardDefaults.border(
                        border = Border(
                            border = BorderStroke(
                                NuvioTheme.spacing.hairline,
                                Color.White.copy(alpha = 0.28f)
                            ),
                            shape = RoundedCornerShape(28.dp)
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.FocusRing),
                            shape = RoundedCornerShape(28.dp)
                        )
                    ),
                    scale = CardDefaults.scale(focusedScale = 1.04f),
                    modifier = Modifier
                        .focusRequester(submitFocusRequester)
                        .focusProperties {
                            up = scoreFocusRequesters[selected - 1]
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.rate_after_watching_submit, selected),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.rate_after_watching_hint),
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 16.sp
                )
            }
        }
    }
}
