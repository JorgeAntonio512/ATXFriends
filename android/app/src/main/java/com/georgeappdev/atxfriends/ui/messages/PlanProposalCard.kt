package com.georgeappdev.atxfriends.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.model.Message
import com.georgeappdev.atxfriends.data.model.PlanStatus
import com.georgeappdev.atxfriends.domain.messages.MessageDates
import com.georgeappdev.atxfriends.domain.messages.ProposalCardState
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/**
 * Port of iOS PlanProposalCard: a plan-proposal message shown as a card, on the sender's side.
 * The plan comes from the thread's live plans listener instead of a one-time fetch per card,
 * so the card updates itself after Accept / Decline (receiver only, pending plans).
 */
@Composable
fun PlanProposalCard(
    message: Message,
    isMine: Boolean,
    state: ProposalCardState,
    myID: String,
    dates: MessageDates,
    isBusy: Boolean,
    onAccept: (planID: String) -> Unit,
    onDecline: (planID: String) -> Unit,
) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(16.dp)
    SideAlignedRow(isMine, horizontalPadding = 16.dp) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .widthIn(max = 300.dp)
                .shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.07f), spotColor = Color.Black.copy(alpha = 0.1f))
                .background(colors.cardBackground.copy(alpha = 0.93f), shape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_msg_calendar_plus),
                    contentDescription = null,
                    tint = colors.appPrimary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.size(6.dp))
                // Weight on the title (not a spacer) so at large font sizes it wraps instead of
                // pushing the status pill out of the card.
                Text(
                    stringResource(R.string.plan_proposal),
                    style = atxText(13.sp, FontWeight.SemiBold),
                    color = colors.appPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (state is ProposalCardState.Loaded) StatusPill(state.plan.status)
            }

            HorizontalDivider(color = colors.appPrimary.copy(alpha = 0.25f))

            when (state) {
                ProposalCardState.Loading -> {
                    SummaryText(message.text, maxLines = 2)
                    CircularProgressIndicator(color = colors.appPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                }
                ProposalCardState.Failed -> SummaryText(message.text, maxLines = Int.MAX_VALUE)
                is ProposalCardState.Loaded -> {
                    val plan = state.plan
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_msg_walk),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp).background(colors.appPrimary, RoundedCornerShape(50)).padding(3.dp),
                        )
                        Text(plan.activity.name, style = atxText(17.sp, FontWeight.SemiBold), color = colors.textHeavy)
                    }
                    // The confirmed date once accepted, otherwise the proposed one.
                    (plan.confirmedDate ?: plan.proposedDates.firstOrNull())?.let { date ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_msg_clock),
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(dates.proposalDate(date), style = atxText(15.sp), color = colors.textMedium)
                        }
                    }
                    if (plan.status == PlanStatus.PENDING && plan.receiverID == myID) {
                        AcceptDeclineRow(
                            isBusy = isBusy,
                            onAccept = { onAccept(plan.id) },
                            onDecline = { onDecline(plan.id) },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryText(text: String, maxLines: Int) {
    Text(text, style = atxText(14.sp), color = AtxTheme.colors.secondaryText, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/**
 * iOS's Decline / Accept pair — used by the proposal card and the reschedule request. While
 * [isBusy] (a write is saving) both are disabled under a spinner, so a double tap can't send
 * two writes.
 */
@Composable
fun AcceptDeclineRow(
    isBusy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    acceptHint: String? = null,
    declineHint: String? = null,
) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(Modifier.alpha(if (isBusy) 0.5f else 1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.plan_decline),
                style = atxText(15.sp, FontWeight.SemiBold),
                color = PlanColors.declinedRed,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(PlanColors.declinedRed.copy(alpha = 0.10f))
                    .clickable(enabled = !isBusy, role = Role.Button, onClickLabel = declineHint, onClick = onDecline)
                    .padding(vertical = 9.dp),
            )
            Text(
                stringResource(R.string.plan_accept),
                style = atxText(15.sp, FontWeight.SemiBold),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(colors.appPrimary)
                    .clickable(enabled = !isBusy, role = Role.Button, onClickLabel = acceptHint, onClick = onAccept)
                    .padding(vertical = 9.dp),
            )
        }
        if (isBusy) {
            CircularProgressIndicator(color = colors.appPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
    }
}

/** The per-status pill, with iOS's hardcoded text and colors. */
@Composable
private fun StatusPill(status: PlanStatus) {
    val (text, fg, bg) = when (status) {
        PlanStatus.PENDING -> Triple(R.string.plan_status_pending, PlanColors.pendingGold, PlanColors.pendingGoldTint.copy(alpha = 0.6f))
        PlanStatus.CONFIRMED -> Triple(R.string.plan_status_confirmed, PlanColors.positiveGreen, PlanColors.positiveGreenTint.copy(alpha = 0.18f))
        PlanStatus.DECLINED -> Triple(R.string.plan_status_declined, PlanColors.cancelledText, PlanColors.cancelledTint.copy(alpha = 0.12f))
        PlanStatus.CANCELLED -> Triple(R.string.plan_status_cancelled, PlanColors.cancelledText, PlanColors.cancelledTint.copy(alpha = 0.12f))
        PlanStatus.COUNTER_PROPOSED -> Triple(R.string.plan_status_counter, PlanColors.counterText, PlanColors.counterTint.copy(alpha = 0.12f))
        // Never reached: the repository drops plans with an unrecognized status, as iOS does.
        PlanStatus.UNKNOWN -> return
    }
    Row(
        Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (status == PlanStatus.CONFIRMED) {
            Icon(painterResource(R.drawable.ic_msg_check_circle), contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        }
        Text(stringResource(text), style = atxText(11.sp, FontWeight.SemiBold), color = fg)
    }
}
