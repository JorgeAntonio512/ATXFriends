package com.georgeappdev.atxfriends.ui.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import com.georgeappdev.atxfriends.data.model.ReportReason
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme
import java.io.File

/** Port of iOS PrivacyAndSafetyView. */
@Composable
fun PrivacySafetyScreen(onBack: () -> Unit, onNavigate: (Any) -> Unit) {
    val colors = AtxTheme.colors
    val context = LocalContext.current
    SettingsSubScreen(title = stringResource(R.string.privacy_title), onBack = onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_set_hand), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.privacy_title), style = atxText(24.sp, FontWeight.Bold), color = colors.primaryText)
                }
                Text(stringResource(R.string.privacy_header_body), style = atxText(15.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
            }
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.cardBackground.copy(alpha = 0.6f)).padding(16.dp),
            ) {
                PrivacyRow(R.drawable.ic_set_hand, R.string.privacy_row_block, R.string.privacy_row_block_sub, SettingsColors.iconWarm) { onNavigate(SettingsBlockUser) }
                RowDivider(56.dp)
                PrivacyRow(R.drawable.ic_set_person_off, R.string.privacy_row_blocked, R.string.privacy_row_blocked_sub, colors.secondaryText) { onNavigate(SettingsBlockedUsers) }
                RowDivider(56.dp)
                PrivacyRow(R.drawable.ic_set_warning, R.string.privacy_row_report, R.string.privacy_row_report_sub, colors.danger) { onNavigate(SettingsReportUser) }
                RowDivider(56.dp)
                PrivacyRow(R.drawable.ic_set_trash, R.string.privacy_row_delete, R.string.privacy_row_delete_sub, SettingsColors.dangerStrong) { onNavigate(SettingsDeleteAccount) }
                RowDivider(56.dp)
                PrivacyRow(R.drawable.ic_set_share, R.string.privacy_row_export, R.string.privacy_row_export_sub, SettingsColors.iconInfo) { onNavigate(SettingsExportData) }
                RowDivider(56.dp)
                val url = stringResource(R.string.privacy_policy_url)
                PrivacyRow(R.drawable.ic_set_shield, R.string.privacy_row_protect, R.string.privacy_row_protect_sub, colors.appPrimary) { openUrl(context, url) }
            }
        }
    }
}

/** iOS PrivacyRowLabel. */
@Composable
private fun PrivacyRow(@DrawableRes icon: Int, title: Int, subtitle: Int, iconTint: Color, onClick: () -> Unit) {
    val colors = AtxTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = atxText(16.sp, FontWeight.Medium), color = colors.textStrong)
            Text(stringResource(subtitle), style = atxText(14.sp), color = colors.secondaryText)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.textSubtle, modifier = Modifier.size(18.dp))
    }
}

/** iOS BlockUserView. */
@Composable
fun BlockUserScreen(onBack: () -> Unit, viewModel: BlockUserViewModel = viewModel(factory = BlockUserViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors
    var confirm by rememberSaveable(stateSaver = PersonRowSaver) { mutableStateOf<PersonRow?>(null) }

    LaunchedEffect(state.done) { if (state.done) onBack() }

    SettingsSubScreen(title = stringResource(R.string.block_title), onBack = onBack, backEnabled = !state.isBlocking) {
        when {
            state.isLoading -> LoadingState(stringResource(R.string.block_loading))
            state.loadFailed -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                SettingsErrorBanner(stringResource(R.string.block_load_failed), onRetry = viewModel::load)
            }
            state.isEmpty -> EmptyState(R.drawable.ic_set_person_off, stringResource(R.string.block_empty_title), stringResource(R.string.block_empty_body))
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_set_hand), contentDescription = null, tint = SettingsColors.iconWarm, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.block_header), style = atxText(16.sp, FontWeight.Medium), color = colors.secondaryText)
                }
                if (state.blockFailed) SettingsErrorBanner(stringResource(R.string.block_failed), Modifier.padding(horizontal = 20.dp))
                if (state.pending.isNotEmpty()) PeopleSection(stringResource(R.string.block_section_matches), state.pending, enabled = !state.isBlocking) { person, _ -> confirm = person }
                if (state.connections.isNotEmpty()) PeopleSection(stringResource(R.string.block_section_connections), state.connections, enabled = !state.isBlocking) { person, _ -> confirm = person }
            }
        }
        state.blockedName?.let { SuccessOverlay(stringResource(R.string.block_success_title), stringResource(R.string.block_success_body, it), Modifier.align(Alignment.Center)) }
    }

    confirm?.let { person ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.block_confirm_title, person.name.ifBlank { stringResource(R.string.block_user_fallback) })) },
            text = { Text(stringResource(R.string.block_confirm_body)) },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    viewModel.block(person)
                }) { Text(stringResource(R.string.block_confirm_button), color = colors.danger) }
            },
        )
    }
}

/** iOS BlockedUsersView. */
@Composable
fun BlockedUsersScreen(onBack: () -> Unit, viewModel: BlockedUsersViewModel = viewModel(factory = BlockedUsersViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors
    var confirm by rememberSaveable(stateSaver = PersonRowSaver) { mutableStateOf<PersonRow?>(null) }

    SettingsSubScreen(title = stringResource(R.string.blocked_title), onBack = onBack, backEnabled = !state.isUnblocking) {
        when {
            state.isLoading -> LoadingState(stringResource(R.string.blocked_loading))
            state.loadFailed -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                SettingsErrorBanner(stringResource(R.string.blocked_load_failed), onRetry = viewModel::load)
            }
            state.blocked.isEmpty() -> EmptyState(R.drawable.ic_set_person_off, stringResource(R.string.blocked_empty_title), stringResource(R.string.blocked_empty_body))
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_set_person_off), contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.blocked_title), style = atxText(20.sp, FontWeight.Bold), color = colors.textStrong)
                    Text(stringResource(R.string.blocked_header_body), style = atxText(15.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
                }
                if (state.unblockFailed) SettingsErrorBanner(stringResource(R.string.blocked_unblock_failed), Modifier.padding(horizontal = 20.dp))
                PeopleSection(title = null, people = state.blocked, enabled = !state.isUnblocking, unblockButton = true) { person, _ -> confirm = person }
            }
        }
        state.unblockedName?.let { SuccessOverlay(stringResource(R.string.blocked_success_title), stringResource(R.string.blocked_success_body, it), Modifier.align(Alignment.Center)) }
    }

    confirm?.let { person ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.blocked_confirm_title, person.name.ifBlank { stringResource(R.string.block_user_fallback) })) },
            text = { Text(stringResource(R.string.blocked_confirm_body)) },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    viewModel.unblock(person)
                }) { Text(stringResource(R.string.blocked_unblock)) }
            },
        )
    }
}

@Composable
private fun PeopleSection(
    title: String?,
    people: List<PersonRow>,
    enabled: Boolean,
    unblockButton: Boolean = false,
    @DrawableRes trailingIcon: Int = R.drawable.ic_set_hand,
    trailingTint: Color = SettingsColors.iconWarm,
    onTap: (PersonRow, Int) -> Unit,
) {
    val colors = AtxTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (title != null) Text(title, style = atxText(20.sp, FontWeight.Bold), color = colors.textStrong, modifier = Modifier.padding(horizontal = 20.dp))
        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.cardBackground.copy(alpha = 0.6f)).padding(16.dp)) {
            people.forEachIndexed { i, person ->
                if (i > 0) RowDivider(76.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (unblockButton) Modifier else Modifier.clickable(enabled = enabled, role = Role.Button) { onTap(person, i) })
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(person.photoURL)
                    Text(person.name, style = atxText(17.sp, FontWeight.Medium), color = colors.textStrong, modifier = Modifier.weight(1f))
                    if (unblockButton) {
                        Text(
                            stringResource(R.string.blocked_unblock),
                            style = atxText(15.sp, FontWeight.SemiBold),
                            color = Color.White,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.appPrimary)
                                .clickable(enabled = enabled, role = Role.Button) { onTap(person, i) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    } else {
                        Icon(painterResource(trailingIcon), contentDescription = null, tint = trailingTint, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(url: String?, size: Dp = 52.dp) {
    val colors = AtxTheme.colors
    val placeholder = @Composable {
        Box(Modifier.fillMaxSize().background(colors.border), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_person), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
    Box(Modifier.size(size).clip(CircleShape)) {
        if (url == null) placeholder()
        else SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = { Box(Modifier.fillMaxSize().background(colors.border)) },
            error = { placeholder() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SuccessOverlay(title: String, body: String, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Column(
        modifier
            .padding(24.dp)
            .shadow(20.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(colors.cardBackground.copy(alpha = 0.95f))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(R.drawable.ic_msg_check_circle), contentDescription = null, tint = SettingsColors.iconSafe, modifier = Modifier.size(60.dp))
        Text(title, style = atxText(20.sp, FontWeight.Bold), color = colors.textStrong)
        Text(body, style = atxText(16.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
    }
}

/** iOS ReportUserView: step 1 picks the person, step 2 the reason and optional comments. */
@Composable
fun ReportUserScreen(onBack: () -> Unit, viewModel: ReportUserViewModel = viewModel(factory = ReportUserViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors
    val selected = state.selected

    LaunchedEffect(state.done) { if (state.done) onBack() }
    // In step 2, back returns to the list (iOS swaps the back button for "Back").
    BackHandler(enabled = selected != null && !state.submitted) { viewModel.backToPeople() }

    SettingsSubScreen(
        title = stringResource(R.string.report_title),
        onBack = { if (selected != null) viewModel.backToPeople() else onBack() },
        backEnabled = !state.isSubmitting && !state.submitted,
    ) {
        when {
            selected != null -> ReportReasonStep(state, selected, viewModel)
            state.isLoading -> LoadingState(stringResource(R.string.report_loading))
            state.loadFailed -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                SettingsErrorBanner(stringResource(R.string.report_load_failed), onRetry = viewModel::load)
            }
            state.isEmpty -> EmptyState(R.drawable.ic_set_warning, stringResource(R.string.report_empty_title), stringResource(R.string.report_empty_body))
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_set_warning), contentDescription = null, tint = colors.danger, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.report_header), style = atxText(16.sp, FontWeight.Medium), color = colors.secondaryText)
                }
                val pick: (PersonRow, Int) -> Unit = { person, _ -> viewModel.select(person) }
                if (state.pending.isNotEmpty()) PeopleSection(stringResource(R.string.block_section_matches), state.pending, enabled = true, trailingIcon = R.drawable.ic_chevron_right, trailingTint = colors.textSubtle, onTap = pick)
                if (state.connections.isNotEmpty()) PeopleSection(stringResource(R.string.block_section_connections), state.connections, enabled = true, trailingIcon = R.drawable.ic_chevron_right, trailingTint = colors.textSubtle, onTap = pick)
                if (state.blocked.isNotEmpty()) PeopleSection(stringResource(R.string.report_section_blocked), state.blocked, enabled = true, trailingIcon = R.drawable.ic_chevron_right, trailingTint = colors.textSubtle, onTap = pick)
            }
        }
        if (state.submitted) SuccessOverlay(stringResource(R.string.report_success_title), stringResource(R.string.report_success_body), Modifier.align(Alignment.Center))
    }

    if (state.submitFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.settings_error_title)) },
            text = { Text(stringResource(R.string.report_failed)) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun ReportReasonStep(state: ReportUserUiState, person: PersonRow, viewModel: ReportUserViewModel) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp, start = 20.dp, end = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(person.photoURL, size = 80.dp)
            val name = person.name.ifBlank { stringResource(R.string.block_user_fallback) }
            Text(stringResource(R.string.report_reporting, name), style = atxText(20.sp, FontWeight.Bold), color = colors.textStrong, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.report_reason_prompt), style = atxText(16.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
        }

        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardBackground.copy(alpha = 0.6f))
                .padding(16.dp)
                .selectableGroup(),
        ) {
            ReportReason.choices.forEachIndexed { i, reason ->
                if (i > 0) RowDivider(20.dp)
                val isSelected = state.reason == reason
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(selected = isSelected, enabled = !state.isSubmitting, role = Role.RadioButton) { viewModel.setReason(reason) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        painterResource(if (isSelected) R.drawable.ic_msg_check_circle else R.drawable.ic_radio_unchecked),
                        contentDescription = null,
                        tint = if (isSelected) colors.danger else colors.textSubtle,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(stringResource(reason.label()), style = atxText(16.sp), color = colors.textStrong)
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val label = stringResource(R.string.report_comments_label)
            Text(label, style = atxText(16.sp, FontWeight.SemiBold), color = colors.textStrong)
            BasicTextField(
                value = state.comments,
                onValueChange = viewModel::setComments,
                enabled = !state.isSubmitting,
                textStyle = atxText(16.sp).copy(color = colors.textStrong),
                cursorBrush = SolidColor(colors.primaryText),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clip(shape)
                    .background(colors.cardBackground.copy(alpha = 0.6f))
                    .border(1.dp, colors.border, shape)
                    .padding(12.dp)
                    .semantics { contentDescription = label },
            )
        }

        WideButton(
            text = stringResource(if (state.isSubmitting) R.string.report_submitting else R.string.report_submit),
            icon = R.drawable.ic_send,
            background = if (state.reason != null) colors.danger else colors.textSubtle,
            busy = state.isSubmitting,
            enabled = state.canSubmit,
            onClick = viewModel::submit,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
        )
    }
}

private fun ReportReason.label(): Int = when (this) {
    ReportReason.INAPPROPRIATE_BEHAVIOR -> R.string.report_reason_inappropriate
    ReportReason.HARASSMENT -> R.string.report_reason_harassment
    ReportReason.FAKE_PROFILE -> R.string.report_reason_fake
    ReportReason.SPAM -> R.string.report_reason_spam
    ReportReason.OTHER, ReportReason.UNKNOWN -> R.string.report_reason_other
}

private val PersonRowSaver = Saver<PersonRow?, List<String?>>(
    save = { it?.let { p -> listOf(p.id, p.name, p.photoURL) } },
    restore = { PersonRow(it[0]!!, it[1]!!, it[2]) },
)

/** iOS ExportDataView. */
@Composable
fun ExportDataScreen(onBack: () -> Unit, viewModel: ExportDataViewModel = viewModel(factory = ExportDataViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors
    val context = LocalContext.current

    LaunchedEffect(state.readyText) {
        val text = state.readyText ?: return@LaunchedEffect
        if (shareExport(context, text)) viewModel.shareHandled() else viewModel.shareFailed()
    }

    SettingsSubScreen(title = stringResource(R.string.export_title), onBack = onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, start = 20.dp, end = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(painterResource(R.drawable.ic_set_share), contentDescription = null, tint = SettingsColors.iconInfo, modifier = Modifier.size(60.dp))
                Text(stringResource(R.string.export_title), style = atxText(28.sp, FontWeight.Bold), color = colors.textStrong)
                Text(stringResource(R.string.export_subtitle), style = atxText(16.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.export_included_header), style = atxText(20.sp, FontWeight.Bold), color = colors.textStrong, modifier = Modifier.padding(horizontal = 20.dp))
                IconListCard(
                    tint = SettingsColors.iconInfo,
                    rows = listOf(
                        Triple(R.drawable.ic_person, R.string.export_profile_title, R.string.export_profile_desc),
                        Triple(R.drawable.ic_set_run, R.string.export_activities_title, R.string.export_activities_desc),
                        Triple(R.drawable.ic_set_calendar, R.string.export_availability_title, R.string.export_availability_desc),
                        Triple(R.drawable.ic_set_photo, R.string.export_photos_title, R.string.export_photos_desc),
                        Triple(R.drawable.ic_people, R.string.export_matches_title, R.string.export_matches_desc),
                        Triple(R.drawable.ic_tab_messages_selected, R.string.export_messages_title, R.string.export_messages_desc),
                        Triple(R.drawable.ic_msg_calendar_check, R.string.export_plans_title, R.string.export_plans_desc),
                    ),
                )
            }

            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.cardBackground.copy(alpha = 0.6f)).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(painterResource(R.drawable.ic_set_info), contentDescription = null, tint = SettingsColors.iconInfo, modifier = Modifier.size(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.export_info_title), style = atxText(18.sp, FontWeight.Bold), color = colors.textStrong)
                    Text(stringResource(R.string.export_info_body), style = atxText(15.sp), color = colors.secondaryText)
                }
            }

            WideButton(
                text = stringResource(if (state.isExporting) R.string.export_button_busy else R.string.export_button),
                icon = R.drawable.ic_set_share,
                background = SettingsColors.iconInfo,
                busy = state.isExporting,
                enabled = !state.isExporting,
                onClick = viewModel::export,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp),
            )
        }
    }

    if (state.failed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.settings_error_title)) },
            text = { Text(stringResource(R.string.export_failed)) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

/** A card of icon + title + description rows (export contents, deletion contents). */
@Composable
internal fun IconListCard(tint: Color, rows: List<Triple<Int, Int, Int>>) {
    val colors = AtxTheme.colors
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.cardBackground.copy(alpha = 0.6f)).padding(16.dp)) {
        rows.forEachIndexed { i, (icon, title, desc) ->
            if (i > 0) RowDivider(56.dp)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(title), style = atxText(16.sp, FontWeight.Medium), color = colors.textStrong)
                    Text(stringResource(desc), style = atxText(14.sp), color = colors.secondaryText)
                }
            }
        }
    }
}

/** Full-width 12-corner action button with an icon or a spinner. */
@Composable
internal fun WideButton(
    text: String,
    @DrawableRes icon: Int,
    background: Color,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        else Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(text, style = atxText(17.sp, FontWeight.Bold), color = Color.White)
    }
}

/** Opens a web page in the browser (iOS shows it in an in-app Safari view). */
private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * The share sheet for the export (iOS UIActivityViewController). The text goes in a .txt file
 * in the app's cache, shared read-only through FileProvider, so a long history never overflows
 * an Intent extra. Returns false if the file couldn't be written or nothing can receive it.
 */
private fun shareExport(context: Context, text: String): Boolean = runCatching {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, context.getString(R.string.export_file_name)).apply { writeText(text) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_share_subject))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess
