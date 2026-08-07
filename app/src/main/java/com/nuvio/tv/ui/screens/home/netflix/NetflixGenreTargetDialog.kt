package com.nuvio.tv.ui.screens.home.netflix

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.sync.SyncGenreRowTarget
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme

internal data class NetflixGenreTargetOption(
    val key: String,
    val title: String,
    val subtitle: String,
    val target: SyncGenreRowTarget
)

@Composable
internal fun NetflixGenreTargetDialog(
    chip: NetflixGenreChip,
    options: List<NetflixGenreTargetOption>,
    selectedTarget: SyncGenreRowTarget?,
    onSelect: (SyncGenreRowTarget?) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.genre_target_dialog_title, chip.label),
        subtitle = stringResource(R.string.genre_target_dialog_subtitle),
        width = 760.dp,
        usePlatformDefaultWidth = false
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
        ) {
            item(key = "automatic") {
                GenreTargetButton(
                    title = stringResource(R.string.genre_target_automatic),
                    subtitle = stringResource(R.string.genre_target_automatic_subtitle),
                    selected = selectedTarget == null,
                    onClick = { onSelect(null) }
                )
            }
            items(options, key = NetflixGenreTargetOption::key) { option ->
                GenreTargetButton(
                    title = option.title,
                    subtitle = option.subtitle,
                    selected = option.target == selectedTarget,
                    onClick = { onSelect(option.target) }
                )
            }
        }
    }
}

@Composable
private fun GenreTargetButton(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContainerColor = NuvioTheme.colors.FocusBackground,
            focusedContentColor = NuvioTheme.colors.TextPrimary
        ),
        border = ButtonDefaults.border(
            border = if (selected) {
                Border(
                    border = BorderStroke(2.dp, NuvioTheme.colors.Primary),
                    shape = RoundedCornerShape(NuvioTheme.radii.md)
                )
            } else {
                Border.None
            },
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioTheme.colors.FocusRing),
                shape = RoundedCornerShape(NuvioTheme.radii.md)
            )
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = NuvioTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Text(
                    text = stringResource(R.string.genre_target_selected),
                    style = MaterialTheme.typography.labelMedium,
                    color = NuvioTheme.colors.Primary
                )
            }
        }
    }
}
