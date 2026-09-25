package com.georgeappdev.atxfriends.ui.messages

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.model.Message
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/**
 * The fixed status/badge accents from AppColors.swift that Messages uses. Like iOS, these have
 * no dark variant.
 */
object PlanColors {
    val positiveGreen = Color(0.30f, 0.58f, 0.35f)
    val positiveGreenTint = Color(0.55f, 0.80f, 0.55f)
    val declinedRed = Color(0.72f, 0.33f, 0.28f)
    val cancelledText = Color(0.60f, 0.35f, 0.30f)
    val cancelledTint = Color(0.80f, 0.40f, 0.35f)
    val counterText = Color(0.40f, 0.40f, 0.70f)
    val counterTint = Color(0.50f, 0.50f, 0.85f)
    val pendingGold = Color(0.70f, 0.55f, 0.20f)
    val pendingGoldTint = Color(0.97f, 0.90f, 0.60f)
    val promptGold = Color(0.60f, 0.45f, 0.20f)
    val promptGoldAccent = Color(0.72f, 0.55f, 0.25f)
}

/**
 * Port of iOS AvatarRing: the photo in a burnt-orange ring, initials when there's no photo or
 * it fails. [needsAttention] is the bolder, fully opaque ring used for unread rows.
 */
@Composable
fun AvatarRing(
    photoURL: String?,
    displayName: String,
    size: Dp,
    modifier: Modifier = Modifier,
    needsAttention: Boolean = false,
) {
    val colors = AtxTheme.colors
    val ringColor = if (needsAttention) colors.appPrimary else colors.appPrimary.copy(alpha = 0.35f)
    val ringWidth = size * (if (needsAttention) 3f / 64f else 2f / 64f)
    val photoSize = size * (56f / 64f)
    val a11y = stringResource(R.string.messages_profile_photo, displayName)

    Box(
        modifier
            .size(size)
            .border(ringWidth, ringColor, CircleShape)
            .clearAndSetSemantics { contentDescription = a11y },
        contentAlignment = Alignment.Center,
    ) {
        val photoModifier = Modifier.size(photoSize).clip(CircleShape)
        val initials: @Composable () -> Unit = { InitialsCircle(displayName, (size.value * 20f / 56f).sp) }
        if (photoURL == null) {
            Box(photoModifier) { initials() }
        } else {
            SubcomposeAsyncImage(
                model = photoURL,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = photoModifier,
                loading = { Box(Modifier.fillMaxSize().background(colors.border.copy(alpha = 0.3f))) },
                error = { initials() },
            )
        }
    }
}

/** The initials fallback: the first letter on burnt orange at 70%. */
@Composable
fun InitialsCircle(name: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().background(AtxTheme.colors.appPrimary.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            style = atxText(fontSize, FontWeight.SemiBold),
            color = Color.White,
        )
    }
}

/**
 * Port of iOS MessageBubble: mine on the right in burnt orange, theirs on the left on a card.
 * [time] is the sent time, or "Sending…" while the server hasn't accepted it yet.
 */
@Composable
fun MessageBubble(message: Message, isMine: Boolean, time: String) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(18.dp)
    SideAlignedRow(isMine, horizontalPadding = 20.dp) {
        Column(
            Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                message.text,
                style = atxText(16.sp),
                color = if (isMine) Color.White else colors.primaryText,
                modifier = Modifier
                    .shadow(
                        elevation = 3.dp,
                        shape = shape,
                        ambientColor = if (isMine) colors.appPrimary.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.05f),
                        spotColor = if (isMine) colors.appPrimary.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.08f),
                    )
                    .background(if (isMine) colors.appPrimary else colors.cardBackground, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            Text(
                time,
                style = atxText(11.sp),
                color = colors.secondaryText,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/**
 * iOS's `HStack { Spacer(minLength: 60); content }` (or mirrored): [content] hugs the sender's
 * side, sized to fit, and never comes closer than 60dp to the other edge. Content should use
 * `Modifier.weight(1f, fill = false)` so long content wraps instead of overflowing.
 */
@Composable
fun SideAlignedRow(
    isMine: Boolean,
    horizontalPadding: Dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        if (isMine) Spacer(Modifier.width(60.dp))
        content()
        if (!isMine) Spacer(Modifier.width(60.dp))
    }
}

/** The small rounded date chip above each day's messages. */
@Composable
fun DateHeaderChip(label: String) {
    val colors = AtxTheme.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = atxText(13.sp, FontWeight.SemiBold),
            color = colors.secondaryText,
            modifier = Modifier
                .background(colors.cardBackground, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/**
 * Port of the iOS input row: the "+" propose-a-plan shortcut (shown when [onProposePlan] is
 * set) and MessageInputBar ("Message..." field, up to 5 lines, and the send button, orange when
 * there's something to send).
 */
@Composable
fun MessageInputBar(
    text: String,
    canSend: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onFocused: () -> Unit,
    onProposePlan: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AtxTheme.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onProposePlan != null) {
            val propose = stringResource(R.string.thread_propose_plan)
            CircleIcon(
                icon = R.drawable.ic_msg_calendar_plus,
                size = 36.dp,
                iconSize = 18.dp,
                background = colors.appPrimary.copy(alpha = 0.18f),
                tint = colors.appPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = propose, onClick = onProposePlan)
                    .semantics { contentDescription = propose },
            )
        }
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val placeholder = stringResource(R.string.thread_message_placeholder)
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = atxText(16.sp).copy(color = colors.primaryText),
                cursorBrush = SolidColor(colors.appPrimary),
                minLines = 1,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { if (it.isFocused) onFocused() }
                    .semantics { contentDescription = placeholder },
                decorationBox = { field ->
                    Box(
                        Modifier
                            .background(colors.cardBackground, RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        if (text.isEmpty()) Text(placeholder, style = atxText(16.sp), color = colors.secondaryText)
                        field()
                    }
                },
            )
            val send = stringResource(R.string.thread_send)
            Box(
                Modifier
                    .size(40.dp)
                    .shadow(if (canSend) 6.dp else 0.dp, CircleShape, spotColor = colors.appPrimary.copy(alpha = 0.3f))
                    .background(if (canSend) colors.appPrimary else colors.border, CircleShape)
                    .clip(CircleShape)
                    .clickable(enabled = canSend, role = Role.Button, onClickLabel = send, onClick = onSend)
                    .semantics { contentDescription = send },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_msg_arrow_up), null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * Android-only: iOS never shows its send error (spec §11.3). A thin banner above the input bar,
 * dismissed by tapping it.
 */
@Composable
fun ThreadErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    val dismiss = stringResource(R.string.thread_dismiss)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.danger.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClickLabel = dismiss, onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_error), null, tint = colors.danger, modifier = Modifier.size(18.dp))
        Text(message, style = atxText(14.sp), color = colors.primaryText, modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_close_circle), null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun CircleIcon(
    @DrawableRes icon: Int,
    size: Dp,
    iconSize: Dp,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}
