package com.nuvio.tv.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun PendingViewPackDialog(
    packName: String,
    onAccept: () -> Unit,
    onLater: () -> Unit
) {
    val acceptFocus = remember { FocusRequester() }
    BackHandler(onBack = onLater)
    LaunchedEffect(Unit) {
        acceptFocus.requestFocus()
    }
    NuvioDialog(
        onDismiss = onLater,
        title = stringResource(R.string.view_pack_offer_title),
        subtitle = stringResource(R.string.view_pack_offer_subtitle, packName),
        width = 560.dp,
        usePlatformDefaultWidth = false
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NuvioTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
        ) {
            Button(
                onClick = onLater,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary,
                    focusedContainerColor = NuvioTheme.colors.FocusBackground,
                    focusedContentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Text(
                    text = stringResource(R.string.view_pack_offer_later),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Button(
                onClick = onAccept,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(acceptFocus),
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.Primary,
                    contentColor = NuvioTheme.colors.OnPrimary,
                    focusedContainerColor = NuvioTheme.colors.FocusBackground,
                    focusedContentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Text(
                    text = stringResource(R.string.view_pack_offer_accept),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
