package com.georgeappdev.atxfriends.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import com.georgeappdev.atxfriends.push.OpenThreadTracker
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.model.MessageKind
import com.georgeappdev.atxfriends.data.model.Plan
import com.georgeappdev.atxfriends.domain.messages.ConfirmedPlans
import com.georgeappdev.atxfriends.domain.messages.MessageDates
import com.georgeappdev.atxfriends.domain.messages.MessageThread
import com.georgeappdev.atxfriends.domain.messages.ProposalCardState
import com.georgeappdev.atxfriends.domain.messages.ThreadItem
import com.georgeappdev.atxfriends.domain.messages.threadItems
import com.georgeappdev.atxfriends.domain.messages.visibleMessages
import com.georgeappdev.atxfriends.session.SessionState
import com.georgeappdev.atxfriends.ui.matches.MatchDetailSheet
import com.georgeappdev.atxfriends.ui.plans.PlanComposerSheet
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Opens a conversation full screen over everything, tab bar included — the Android counterpart
 * of iOS's `fullScreenCover`. The thread's ViewModel lives only while it's open, so its live
 * listeners stop when it closes (iOS `stopListening`).
 */
@Composable
fun MessageThreadDialog(thread: MessageThread, myID: String, myPhotoURL: String?, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        MatchSystemBarsToTheme()
        val app = LocalContext.current.applicationContext as AtxFriendsApp
        // Its own store, so the thread's ViewModels (and the composer's) end when it closes.
        // Supplies the Application in the creation extras, which factories like the plan
        // composer's read (APPLICATION_KEY).
        val owner = remember(thread.id) {
            object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
                override val viewModelStore = ViewModelStore()
                override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
                    ViewModelProvider.AndroidViewModelFactory.getInstance(app)
                override val defaultViewModelCreationExtras: CreationExtras =
                    MutableCreationExtras().apply { set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, app) }
            }
        }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
        // While this conversation is on screen, pushes about it aren't shown (iOS currentlyOpenMatchID).
        DisposableEffect(thread.id) {
            OpenThreadTracker.opened(thread.id)
            onDispose { OpenThreadTracker.closed(thread.id) }
        }
        val container = app.container
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            val viewModel: MessageThreadViewModel = viewModel(
                factory = MessageThreadViewModel.factory(
                    thread,
                    myID,
                    container.messages,
                    container.plans,
                    SharedPrefsAddedToCalendarStore(LocalContext.current),
                    container.showUps,
                    ThreadProfileLoader(
                        myID = myID,
                        myProfile = (container.session.state.value as? SessionState.Ready)?.profile,
                        fetchUser = container.users::fetchUser,
                        fetchSimpatico = container.simpatico::fetchState,
                    ),
                ),
            )
            MessageThreadScreen(viewModel, myID, myPhotoURL, onBack = onClose)
        }
    }
}

/** The dialog has its own window, so its status/navigation bar icons need setting for the theme. */
@Composable
private fun MatchSystemBarsToTheme() {
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/** Port of iOS MessageThreadView. */
@Composable
fun MessageThreadScreen(viewModel: MessageThreadViewModel, myID: String, myPhotoURL: String?, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val thread = viewModel.thread
    val colors = AtxTheme.colors

    // iOS refreshes the pinned card's countdown every minute (TimelineView .periodic 60s).
    val now by produceState(Instant.now()) {
        while (true) {
            delay(60_000)
            value = Instant.now()
        }
    }
    val dates = remember(now) { MessageDates(now) }
    val confirmed = remember(state.plans, now) { ConfirmedPlans.from(state.plans, now) }
    val pinned = confirmed.upcoming.firstOrNull()
    val visible = remember(state.messages, confirmed.allIDs) { visibleMessages(state.messages, confirmed.allIDs) }
    val items = remember(visible, dates) { threadItems(visible, dates) }

    val links = rememberPlanLinks(
        calendarEvent = viewModel::calendarEvent,
        isAddedToCalendar = { it in viewModel.state.value.addedToCalendar },
        onReturnedFromCalendar = viewModel::onReturnedFromCalendar,
    )
    val actions = remember(viewModel, links) {
        PlanActions(
            acceptProposal = { viewModel.acceptProposal(it) },
            declineProposal = { viewModel.declineProposal(it) },
            acceptReschedule = { viewModel.acceptReschedule(it) },
            declineReschedule = { viewModel.declineReschedule(it) },
            confirmCancel = { viewModel.askToCancel(it, fromList = false) },
            confirmCancelFromList = { viewModel.askToCancel(it, fromList = true) },
            openReschedule = viewModel::openReschedule,
            showAll = viewModel::showAllPlans,
            directions = links.directions,
            addToCalendar = links.addToCalendar,
        )
    }

    LaunchedEffect(pinned?.id, state.messagesLoaded) {
        if (pinned != null) viewModel.onPinnedPlanShown()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.appBackground)
            // safeDrawing includes the keyboard, so the input bar rides above it.
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ThreadTopBar(thread, onBack, onAvatar = viewModel::openProfile)

        pinned?.let { plan ->
            PinnedPlanCard(
                plan = plan,
                overflowCount = confirmed.upcoming.size - 1,
                otherUserName = thread.otherUserName,
                otherUserPhotoURL = thread.otherUserPhotoURL,
                myPhotoURL = myPhotoURL,
                myID = myID,
                expanded = state.planCardExpanded,
                onExpand = viewModel::expandPlanCard,
                dates = dates,
                isBusy = plan.id in state.busyPlanIDs,
                addedToCalendar = plan.id in state.addedToCalendar,
                actions = actions,
            )
        }

        state.pendingShowUp?.let { todayPlan ->
            ShowUpPromptCard(
                otherUserName = thread.otherUserName,
                activityName = todayPlan.activity.name,
                isSaving = state.showUpSaving,
                onReport = viewModel::submitShowUp,
                onDismiss = viewModel::dismissShowUp,
            )
        }
        if (state.showUpFailed) {
            AlertDialog(
                onDismissRequest = viewModel::dismissShowUpError,
                title = { Text(stringResource(R.string.showup_error_title)) },
                text = { Text(stringResource(R.string.showup_error_message)) },
                confirmButton = { TextButton(onClick = viewModel::dismissShowUpError) { Text(stringResource(R.string.action_ok)) } },
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.messagesFailed -> ThreadLoadError(onRetry = viewModel::retry)
                !state.messagesLoaded -> ThreadLoading()
                visible.isEmpty() -> {
                    val confirmedDate = pinned?.confirmedDate
                    if (confirmedDate != null) ConfirmedPlanEmptyState(dates.confirmedEmptyTitle(confirmedDate), thread.otherUserName)
                    else NoMessagesYet(thread, onAvatar = viewModel::openProfile, onProposePlan = viewModel::openComposer)
                }
                else -> ThreadMessageList(items, state, myID, dates, actions)
            }
        }

        if (state.sendFailed) {
            ThreadErrorBanner(stringResource(R.string.thread_send_failed), onDismiss = viewModel::dismissSendError)
        }
        state.draftTooLong?.let { length ->
            ThreadErrorBanner(stringResource(R.string.thread_message_too_long, length), onDismiss = viewModel::dismissTooLong)
        }
        state.planError?.let { action -> PlanErrorAlert(action, onDismiss = viewModel::dismissPlanError) }
        PlanDialogs(state, confirmed, thread.otherUserName, myID, dates, actions, viewModel)
        state.profile?.let { profile ->
            // Always a mutual match here, so there's no Yay / Nay to decide.
            MatchDetailSheet(
                profile,
                onDismiss = viewModel::closeProfile,
                onDecide = { },
                isSaving = false,
                onProposePlan = viewModel::proposeFromProfile,
            )
        }
        state.composer?.let { request ->
            PlanComposerSheet(request, onDismiss = viewModel::closeComposer)
        }

        MessageInputBar(
            text = state.draft,
            canSend = state.canSend,
            onTextChange = viewModel::onDraftChange,
            onSend = viewModel::send,
            onFocused = viewModel::onInputFocused,
            onProposePlan = { viewModel.openComposer() },
        )
    }
}

/** The thread's plan callbacks, bundled so they can be passed down the tree in one piece. */
class PlanActions(
    val acceptProposal: (planID: String) -> Unit,
    val declineProposal: (planID: String) -> Unit,
    val acceptReschedule: (planID: String) -> Unit,
    val declineReschedule: (planID: String) -> Unit,
    /** Pinned card ⋯ → "Cancel plan" (asks first). */
    val confirmCancel: (planID: String) -> Unit,
    /** Upcoming-plans row "Cancel" (asks first). */
    val confirmCancelFromList: (planID: String) -> Unit,
    val openReschedule: (planID: String) -> Unit,
    val showAll: () -> Unit,
    val directions: (Plan) -> Unit,
    val addToCalendar: (Plan) -> Unit,
)

/** The thread's plan sheets and confirm alerts, each shown when the ViewModel says so. */
@Composable
private fun PlanDialogs(
    state: ThreadUiState,
    confirmed: ConfirmedPlans,
    otherUserName: String,
    myID: String,
    dates: MessageDates,
    actions: PlanActions,
    viewModel: MessageThreadViewModel,
) {
    state.confirmingCancelPlanID?.let { state.plansByID[it] }?.let { plan ->
        CancelPlanAlert(plan, otherUserName, state.cancelFromList, onConfirm = viewModel::confirmCancel, onKeep = viewModel::dismissCancel)
    }
    if (state.reschedulingPlanID != null) {
        val initial = remember(state.reschedulingPlanID) { viewModel.rescheduleInitialTime() }
        if (initial != null) {
            ReschedulePlanSheet(
                initialTime = initial,
                isSaving = state.rescheduleSaving,
                failed = state.rescheduleFailed,
                timePassed = state.rescheduleTimePassed,
                onSubmit = viewModel::requestReschedule,
                onCancel = viewModel::closeReschedule,
            )
        }
    }
    if (state.showingAllPlans) {
        UpcomingPlansSheet(
            plans = confirmed.upcoming.drop(1),
            otherUserName = otherUserName,
            myID = myID,
            dates = dates,
            busyPlanIDs = state.busyPlanIDs,
            actions = actions,
            onDone = viewModel::hideAllPlans,
        )
    }
}

/** iOS's "Couldn't Update Plan" alert, with a message for whichever change failed. */
@Composable
private fun PlanErrorAlert(action: PlanAction, onDismiss: () -> Unit) {
    val message = when (action) {
        PlanAction.ACCEPT -> R.string.plan_error_accept
        PlanAction.DECLINE -> R.string.plan_error_decline
        PlanAction.ACCEPT_RESCHEDULE -> R.string.plan_error_accept_reschedule
        PlanAction.DECLINE_RESCHEDULE -> R.string.plan_error_decline_reschedule
        PlanAction.CANCEL -> R.string.plan_error_cancel
        PlanAction.REQUEST_RESCHEDULE -> R.string.plan_error_reschedule
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_error_title)) },
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

@Composable
private fun ThreadTopBar(thread: MessageThread, onBack: () -> Unit, onAvatar: () -> Unit) {
    val colors = AtxTheme.colors
    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).heightIn(min = 52.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_chevron_left),
                contentDescription = stringResource(R.string.thread_back),
                tint = colors.appPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            thread.otherUserName,
            style = atxText(17.sp, FontWeight.SemiBold),
            color = colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 60.dp).semantics { heading() },
        )
        val viewProfile = stringResource(R.string.thread_view_profile, thread.otherUserName)
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp)
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = viewProfile, onClick = onAvatar)
                .semantics(mergeDescendants = true) { contentDescription = viewProfile },
            contentAlignment = Alignment.Center,
        ) {
            AvatarRing(photoURL = thread.otherUserPhotoURL, displayName = thread.otherUserName, size = 36.dp)
        }
    }
}

@Composable
private fun ThreadMessageList(items: List<ThreadItem>, state: ThreadUiState, myID: String, dates: MessageDates, actions: PlanActions) {
    val listState = rememberLazyListState()
    val hasScrolled = remember { mutableStateOf(false) }
    val plansByID = remember(state.plans) { state.plansByID }

    // Keep the newest message in view as messages arrive (iOS scrolls to the last on count change).
    LaunchedEffect(state.messages.size) {
        if (items.isEmpty()) return@LaunchedEffect
        if (hasScrolled.value) listState.animateScrollToItem(items.lastIndex)
        else listState.scrollToItem(items.lastIndex)
        hasScrolled.value = true
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items, key = { it.key }) { item ->
            when (item) {
                is ThreadItem.DateHeader -> DateHeaderChip(item.label)
                is ThreadItem.Bubble -> {
                    val message = item.message
                    val isMine = message.senderID == myID
                    // Any kind other than a plan proposal renders as text, as on iOS.
                    if (message.kind == MessageKind.PLAN_PROPOSAL) {
                        PlanProposalCard(
                            message = message,
                            isMine = isMine,
                            state = ProposalCardState.of(message, plansByID, state.plansLoaded),
                            myID = myID,
                            dates = dates,
                            isBusy = message.planID in state.busyPlanIDs,
                            onAccept = actions.acceptProposal,
                            onDecline = actions.declineProposal,
                        )
                    } else {
                        val time = if (message.id in state.pendingMessageIDs) stringResource(R.string.thread_sending)
                        else dates.shortTime(message.sentAt)
                        MessageBubble(message, isMine, time)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadLoading() {
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
private fun ThreadLoadError(onRetry: () -> Unit) {
    val colors = AtxTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_error), null, tint = colors.danger, modifier = Modifier.size(32.dp))
        Text(
            stringResource(R.string.thread_load_error),
            style = atxText(15.sp),
            color = colors.secondaryText,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.action_try_again), style = atxText(15.sp, FontWeight.SemiBold), color = colors.appPrimary)
        }
    }
}

/** A confirmed plan but no messages yet: "You're on for tonight." */
@Composable
private fun ConfirmedPlanEmptyState(title: String, otherUserName: String) {
    val colors = AtxTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircleIcon(
            icon = R.drawable.ic_msg_chat,
            size = 80.dp,
            iconSize = 36.dp,
            background = colors.appPrimary.copy(alpha = 0.2f),
            tint = colors.appPrimary,
            modifier = Modifier.padding(top = 40.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = atxText(20.sp, FontWeight.Bold), color = colors.primaryText, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.thread_say_hi_before, otherUserName),
                style = atxText(15.sp),
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The default empty state: their photo and name, shared interests, and "Propose a plan". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoMessagesYet(thread: MessageThread, onAvatar: () -> Unit, onProposePlan: (activityName: String?) -> Unit) {
    val colors = AtxTheme.colors
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .padding(horizontal = 40.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            val viewProfile = stringResource(R.string.thread_view_profile, thread.otherUserName)
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = viewProfile, onClick = onAvatar)
                    .semantics(mergeDescendants = true) { contentDescription = viewProfile },
            ) {
                AvatarRing(thread.otherUserPhotoURL, thread.otherUserName, size = 112.dp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(thread.otherUserName, style = atxText(22.sp, FontWeight.Bold), color = colors.primaryText)
                Text(
                    stringResource(R.string.thread_matched_subtitle),
                    style = atxText(15.sp),
                    color = colors.secondaryText,
                    textAlign = TextAlign.Center,
                )
            }

            val shared = thread.match.overlappingActivityNames
            if (shared.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.thread_you_both_like), style = atxText(13.sp, FontWeight.SemiBold), color = colors.secondaryText)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        shared.forEach { name ->
                            Text(
                                name,
                                style = atxText(14.sp, FontWeight.Medium),
                                color = colors.appPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.appPrimary.copy(alpha = 0.15f))
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = stringResource(R.string.thread_propose_plan_for, name),
                                        onClick = { onProposePlan(name) },
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            Row(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.appPrimary)
                    .clickable(role = Role.Button, onClick = { onProposePlan(null) })
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_msg_calendar_plus), null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.thread_propose_plan), style = atxText(16.sp, FontWeight.SemiBold), color = Color.White)
            }
        }
    }
}
