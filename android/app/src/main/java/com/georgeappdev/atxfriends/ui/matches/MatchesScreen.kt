package com.georgeappdev.atxfriends.ui.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/** Port of iOS MatchesView. Read-only this round. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(viewModel: MatchesViewModel = viewModel(factory = MatchesViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors

    // Runs each time the tab appears (iOS .onAppear).
    LaunchedEffect(Unit) { viewModel.onAppear() }

    Column(Modifier.fillMaxSize().background(colors.appBackground)) {
        Text(
            stringResource(R.string.matches_title),
            style = atxText(17.sp, FontWeight.SemiBold),
            color = colors.primaryText,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp),
        )
        if (state.connected.isNotEmpty()) {
            ConnectedAvatarStrip(state.connected, onTap = viewModel::select)
        }

        if (state.isLoading) {
            MatchesLoading()
        } else {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.isEmpty) {
                    // Scrollable so pull-to-refresh works on the empty and error states too.
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.verticalScroll(rememberScrollState()).fillMaxWidth().heightIn(min = maxHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.loadFailed) LoadErrorBanner(Modifier.padding(20.dp))
                            else EmptyMatches(Modifier.padding(vertical = 24.dp))
                        }
                    }
                } else {
                    MatchesList(state, onSelect = viewModel::select, onDecide = viewModel::decide)
                }
            }
        }
    }

    state.selected?.let { match ->
        MatchDetailSheet(
            match = match,
            onDismiss = viewModel::dismissDetail,
            onDecide = { yay -> viewModel.decide(match.matchID, yay) },
            isSaving = state.isDeciding,
        )
    }

    state.celebration?.let { match ->
        CelebrationOverlay(
            name = match.name,
            onOpen = viewModel::openCelebration,
            onLater = viewModel::dismissCelebration,
        )
    }

    if (state.decisionFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDecisionError,
            text = { Text(stringResource(R.string.matches_decision_error)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDecisionError) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@Composable
private fun MatchesLoading() {
    val colors = AtxTheme.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CircularProgressIndicator(color = colors.appPrimary, modifier = Modifier.size(32.dp))
            Text(stringResource(R.string.matches_loading), style = atxText(16.sp, FontWeight.Medium), color = colors.secondaryText)
        }
    }
}

@Composable
private fun MatchesList(state: MatchesUiState, onSelect: (String) -> Unit, onDecide: (String, Boolean) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (state.loadFailed) {
            item { LoadErrorBanner(Modifier.padding(horizontal = 20.dp)) }
        }
        if (state.pending.isNotEmpty()) {
            item(key = "pending") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SectionHeader(
                        stringResource(R.string.matches_pending_title),
                        stringResource(R.string.matches_pending_subtitle),
                        state.pending.size,
                        Modifier.padding(horizontal = 20.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(state.pending, key = { it.matchID }) { match ->
                            PendingMatchCard(
                                match = match,
                                onTap = { onSelect(match.matchID) },
                                onDecide = { yay -> onDecide(match.matchID, yay) },
                                savingDecision = state.isDeciding,
                                isThisCardSaving = state.decidingMatchID == match.matchID,
                            )
                        }
                    }
                }
            }
        }
        if (state.connected.isNotEmpty()) {
            item(key = "connected-header") {
                SectionHeader(
                    stringResource(R.string.matches_connected_title),
                    stringResource(R.string.matches_connected_subtitle),
                    state.connected.size,
                    Modifier.padding(horizontal = 20.dp),
                )
            }
            items(state.connected, key = { "c-" + it.matchID }) { match ->
                ConnectedMatchRow(match, onTap = { onSelect(match.matchID) }, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

/** Android-only: iOS sets errorMessage on failure but never shows it. */
@Composable
private fun LoadErrorBanner(modifier: Modifier = Modifier) {
    val danger = AtxTheme.colors.danger
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(danger.copy(alpha = 0.1f))
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = danger, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.matches_load_error), style = atxText(14.sp, FontWeight.Medium), color = danger)
    }
}
