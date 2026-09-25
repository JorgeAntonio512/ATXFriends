package com.georgeappdev.atxfriends.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.georgeappdev.atxfriends.navigation.AppTab
import com.georgeappdev.atxfriends.navigation.LocalTabNavigator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.domain.messages.MessageDates
import com.georgeappdev.atxfriends.domain.messages.MessageThread
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme
import java.time.Instant

/** Port of iOS MessagesListView. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(viewModel: MessagesViewModel = viewModel(factory = MessagesViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors

    // Runs each time the tab appears, like iOS rebuilding the list on every tab switch.
    LaunchedEffect(Unit) { viewModel.onAppear() }

    // A tapped new-match push whose thread didn't load falls back to Matches (iOS).
    val tabs = LocalTabNavigator.current
    LaunchedEffect(state.showMatchesTab) {
        if (state.showMatchesTab) {
            viewModel.matchesTabShown()
            tabs.open(AppTab.MATCHES)
        }
    }

    Column(Modifier.fillMaxSize().background(colors.appBackground)) {
        // iOS large navigation title.
        Text(
            stringResource(R.string.messages_title),
            style = atxText(34.sp, FontWeight.Bold),
            color = colors.primaryText,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
        )

        when {
            state.isLoading -> MessagesLoading()
            state.threads.isEmpty() && state.loadFailed -> MessagesLoadError(onRetry = viewModel::onAppear)
            state.threads.isEmpty() -> EmptyMessages()
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                ThreadList(state, onOpen = viewModel::open)
            }
        }
    }

    state.openThread?.let { thread ->
        MessageThreadDialog(thread, state.myID, state.myPhotoURL, onClose = viewModel::closeThread)
    }
}

@Composable
private fun ThreadList(state: MessagesUiState, onOpen: (String) -> Unit) {
    // Row times and plan pills are relative to when the list was last composed, as on iOS.
    val dates = remember(state.threads) { MessageDates(Instant.now()) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.threads, key = { it.id }) { thread ->
            MessageThreadRow(
                thread = thread,
                isUnread = thread.id in state.unreadMatchIDs,
                dates = dates,
                onTap = { onOpen(thread.id) },
            )
        }
    }
}

/** Port of iOS MessageThreadRow. */
@Composable
private fun MessageThreadRow(thread: MessageThread, isUnread: Boolean, dates: MessageDates, onTap: () -> Unit) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val timeText = thread.lastMessage?.let { dates.rowTime(it.sentAt) }.orEmpty()
    val pill = thread.upcomingPlan?.let(dates::planPill)
    val a11y = rowAccessibilityLabel(thread, isUnread, pill)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.cardBackground.copy(alpha = if (isUnread) 1f else 0.7f))
            .clickable(onClick = onTap)
            .padding(16.dp)
            .clearAndSetSemantics {
                contentDescription = a11y
                role = Role.Button
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AvatarRing(thread.otherUserPhotoURL, thread.otherUserName, size = 64.dp, needsAttention = isUnread)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    thread.otherUserName,
                    style = atxText(17.sp, if (isUnread) FontWeight.Bold else FontWeight.SemiBold),
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (timeText.isNotEmpty()) {
                    Text(
                        timeText,
                        style = atxText(13.sp),
                        color = if (isUnread) colors.appPrimary else colors.secondaryText,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            val lastMessage = thread.lastMessage
            if (lastMessage != null) {
                Text(
                    lastMessage.text,
                    style = atxText(15.sp, if (isUnread) FontWeight.Medium else FontWeight.Normal),
                    color = if (isUnread) colors.primaryText else colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(stringResource(R.string.messages_say_hi), style = atxText(15.sp), color = colors.secondaryText)
            }

            pill?.let { PlanPill(it) }
        }
    }
}

@Composable
private fun rowAccessibilityLabel(thread: MessageThread, isUnread: Boolean, pill: String?): String {
    val parts = mutableListOf(thread.otherUserName)
    if (isUnread) parts += stringResource(R.string.messages_row_unread)
    parts += thread.lastMessage?.let { stringResource(R.string.messages_row_last_message, it.text) }
        ?: stringResource(R.string.messages_row_say_hi)
    pill?.let { parts += stringResource(R.string.messages_row_plan, it) }
    return parts.joinToString(". ")
}

@Composable
private fun PlanPill(text: String) {
    val primary = AtxTheme.colors.appPrimary
    Row(
        Modifier.background(primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(R.drawable.ic_msg_calendar_check), null, tint = primary, modifier = Modifier.size(13.dp))
        Text(text, style = atxText(13.sp, FontWeight.Medium), color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MessagesLoading() {
    val colors = AtxTheme.colors
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = colors.appPrimary, modifier = Modifier.size(32.dp))
        Text(stringResource(R.string.messages_loading), style = atxText(16.sp, FontWeight.Medium), color = colors.secondaryText)
    }
}

@Composable
private fun EmptyMessages() {
    val colors = AtxTheme.colors
    Box(Modifier.fillMaxSize().padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.messages_empty_title), style = atxText(20.sp, FontWeight.Bold), color = colors.primaryText)
            Text(
                stringResource(R.string.messages_empty_body),
                style = atxText(15.sp),
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MessagesLoadError(onRetry: () -> Unit) {
    val colors = AtxTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_error), null, tint = colors.danger, modifier = Modifier.size(32.dp))
        Text(
            stringResource(R.string.messages_load_error),
            style = atxText(15.sp),
            color = colors.secondaryText,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.action_try_again), style = atxText(15.sp, FontWeight.SemiBold), color = colors.appPrimary)
        }
    }
}
