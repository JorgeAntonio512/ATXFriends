package com.georgeappdev.atxfriends.ui.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/**
 * Port of iOS MatchDetailView. Say Yay / Say Nay each ask for confirmation with the iOS alert
 * text. [onProposePlan] backs "Propose a Plan"; Send Message is shown but disabled this round.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailSheet(
    match: MatchUi,
    onDismiss: () -> Unit,
    onDecide: (yay: Boolean) -> Unit,
    isSaving: Boolean,
    onProposePlan: (() -> Unit)? = null,
) {
    val colors = AtxTheme.colors
    var fullScreenIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    /** true = confirming Yay, false = confirming Nay. */
    var confirming by rememberSaveable { mutableStateOf<Boolean?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.appBackground,
        dragHandle = null,
    ) {
        Box {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 60.dp, bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                PhotoGallery(match, onPhotoTap = { fullScreenIndex = it }, modifier = Modifier.padding(horizontal = 20.dp))

                Text(match.name, style = atxText(32.sp, FontWeight.Bold), color = colors.primaryText, textAlign = TextAlign.Center)

                match.distanceText?.let {
                    IconLabel(R.drawable.ic_location, it, 16.dp, colors.appPrimary, 15.sp, FontWeight.SemiBold, colors.textStrong, Modifier.padding(horizontal = 20.dp))
                }

                if (match.sharedActivities.isNotEmpty()) {
                    DetailCard(R.drawable.ic_logo_heart, stringResource(R.string.matches_shared_interests), match.sharedActivities)
                }
                match.sharedCategory?.let {
                    IconLabel(R.drawable.ic_sparkles, stringResource(R.string.matches_category_label, it), 16.dp, colors.appPrimary, 14.sp, FontWeight.Medium, colors.textBody, Modifier.padding(horizontal = 20.dp), spacing = 8.dp)
                }
                if (match.sharedTimes.isNotEmpty()) {
                    DetailCard(R.drawable.ic_tab_upcoming, stringResource(R.string.match_detail_free_same_time), match.sharedTimes)
                }

                if (match.isPending) {
                    Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailButton(
                            R.drawable.ic_thumb_up,
                            stringResource(R.string.match_detail_say_yay),
                            filled = true,
                            enabled = !isSaving,
                            showSpinner = isSaving,
                            onClick = { confirming = true },
                        )
                        DetailButton(
                            R.drawable.ic_thumb_down,
                            stringResource(R.string.match_detail_say_nay),
                            filled = false,
                            enabled = !isSaving,
                            onClick = { confirming = false },
                        )
                    }
                }

                if (match.isMutual) ConnectedBlock(onProposePlan)
            }

            // Close button, top right (iOS xmark.circle.fill). The 32dp glyph sits where it
            // always has (16dp from the top, 20dp from the end) inside a 48dp touch target.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 12.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_close_circle),
                    contentDescription = stringResource(R.string.action_close),
                    tint = colors.textMuted,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }

    confirming?.let { yay ->
        DecisionConfirmDialog(
            yay = yay,
            name = match.name,
            onConfirm = {
                confirming = null
                onDecide(yay)
            },
            onCancel = { confirming = null },
        )
    }

    fullScreenIndex?.let { start ->
        FullScreenPhotos(match, startIndex = start, onDismiss = { fullScreenIndex = null })
    }
}

/** Swipeable 400dp gallery with page dots; each photo loads on its own with the iOS placeholder. */
@Composable
private fun PhotoGallery(match: MatchUi, onPhotoTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    if (match.photoURLs.isEmpty()) {
        Box(modifier.fillMaxWidth().height(400.dp).clip(shape)) { PhotoPlaceholder(100.dp, showSpinner = false) }
        return
    }
    val pager = rememberPagerState { match.photoURLs.size }
    Box(modifier.fillMaxWidth().height(400.dp).clip(shape)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            RemotePhoto(
                url = match.photoURLs[page],
                contentDescription = stringResource(R.string.photo_description, page + 1, match.photoURLs.size),
                modifier = Modifier.fillMaxSize().clickable { onPhotoTap(page) },
                loading = { PhotoPlaceholder(100.dp, showSpinner = true) },
                fallback = { PhotoPlaceholder(100.dp, showSpinner = false) },
            )
        }
        PageDots(pager, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
    }
}

@Composable
private fun PageDots(pager: PagerState, modifier: Modifier = Modifier) {
    if (pager.pageCount < 2) return
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.25f)).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(pager.pageCount) { i ->
            Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == pager.currentPage) 1f else 0.4f)))
        }
    }
}

/**
 * Full-screen photos on black, fit to screen. Swipes between photos (iOS shows only the
 * tapped photo); a photo that fails to load just leaves the black background.
 */
@Composable
private fun FullScreenPhotos(match: MatchUi, startIndex: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = startIndex) { match.photoURLs.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                RemotePhoto(
                    url = match.photoURLs[page],
                    contentDescription = stringResource(R.string.photo_description, page + 1, match.photoURLs.size),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    loading = { Box(Modifier.fillMaxSize()) },
                )
            }
            PageDots(pager, Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
            // The 36dp glyph stays 16dp from the top and 20dp from the end, inside a 48dp touch target.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 10.dp, end = 14.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_close_circle),
                    contentDescription = stringResource(R.string.action_close),
                    tint = Color.White,
                    modifier = Modifier.size(36.dp).shadow(4.dp, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun DetailCard(icon: Int, title: String, chips: List<String>) {
    val colors = AtxTheme.colors
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBackground.copy(alpha = 0.7f))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconLabel(icon, title, 18.dp, colors.appPrimary, 18.sp, FontWeight.Bold, colors.textStrong, spacing = 8.dp)
        ChipFlow(chips, 15.sp, FontWeight.SemiBold, 16.dp, 10.dp, 16.dp, 10.dp)
    }
}

/** The mutual-match block: "You're Connected!" with Send Message (disabled) / Propose a Plan. */
@Composable
private fun ConnectedBlock(onProposePlan: (() -> Unit)?) {
    val colors = AtxTheme.colors
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.appPrimary.copy(alpha = 0.1f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconLabel(R.drawable.ic_verified, stringResource(R.string.match_detail_connected_title), 20.dp, colors.appPrimary, 18.sp, FontWeight.Bold, colors.primaryText, spacing = 8.dp)
        Text(
            stringResource(R.string.match_detail_connected_body),
            style = atxText(15.sp),
            color = colors.secondaryText,
            textAlign = TextAlign.Center,
        )
        DetailButton(R.drawable.ic_tab_messages_selected, stringResource(R.string.match_detail_send_message), filled = true)
        DetailButton(R.drawable.ic_calendar_add, stringResource(R.string.match_detail_propose_plan), filled = false, outlined = true, onClick = onProposePlan)
    }
}

/** The iOS "Say Yay?" / "Say Nay?" alerts. Nay's confirm button is destructive (red), as on iOS. */
@Composable
private fun DecisionConfirmDialog(yay: Boolean, name: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(if (yay) R.string.match_detail_yay_title else R.string.match_detail_nay_title)) },
        text = { Text(stringResource(if (yay) R.string.match_detail_yay_message else R.string.match_detail_nay_message, name)) },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(if (yay) R.string.match_detail_yay_confirm else R.string.match_detail_nay_confirm),
                    color = if (yay) AtxTheme.colors.appPrimary else AtxTheme.colors.danger,
                )
            }
        },
    )
}

/**
 * The iOS 56pt action buttons. Filled = navy (Say Yay, Send Message); otherwise card-colored.
 * Without [onClick] the button is inert (Send Message / Propose a Plan this round).
 */
@Composable
private fun DetailButton(
    icon: Int,
    label: String,
    filled: Boolean,
    outlined: Boolean = false,
    enabled: Boolean = true,
    showSpinner: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val content = when {
        filled -> Color.White
        outlined -> colors.appPrimary
        else -> colors.textMuted
    }
    var modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    if (filled) {
        modifier = modifier.shadow(12.dp, shape, ambientColor = colors.appNavy.copy(alpha = 0.3f), spotColor = colors.appNavy.copy(alpha = 0.3f))
    }
    modifier = modifier.clip(shape).background(if (filled) colors.appNavy else colors.cardBackground.copy(alpha = 0.9f))
    if (outlined) modifier = modifier.border(1.5.dp, colors.appPrimary.copy(alpha = 0.4f), shape)
    if (onClick != null) modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (showSpinner) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(label, style = atxText(18.sp, FontWeight.SemiBold), color = content)
        }
    }
}
