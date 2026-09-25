package com.georgeappdev.atxfriends.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.repository.AccountDeleter
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.messages.AddedToCalendarStore
import com.georgeappdev.atxfriends.ui.messages.SharedPrefsAddedToCalendarStore
import com.georgeappdev.atxfriends.ui.simpatico.SharedPrefsSimpaticoPositionStore
import com.georgeappdev.atxfriends.ui.simpatico.SimpaticoPositionStore
import com.georgeappdev.atxfriends.ui.theme.AtxTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeleteAccountUiState(
    val confirmation: String = "",
    val isDeleting: Boolean = false,
    val failed: Boolean = false,
) {
    /** iOS: the typed text, trimmed, must be exactly "DELETE". */
    val isConfirmed: Boolean get() = confirmation.trim() == CONFIRM_WORD
    val canDelete: Boolean get() = isConfirmed && !isDeleting

    companion object {
        const val CONFIRM_WORD = "DELETE"
    }
}

/**
 * iOS DeleteAccountView + PrivacyAndSafetyViewModel.deleteAccount. The deletion itself is the
 * existing `deleteMyAccount` Cloud Function — nothing is deleted from the device side. Only
 * after it succeeds: clear this user's on-device data and sign out, which returns to the
 * welcome screen. On failure nothing is signed out or cleared, and retrying is safe.
 *
 * Like iOS, it also forgets the "Added to Calendar" flags for every plan the function deleted.
 * (iOS first revokes Sign in with Apple for Apple accounts and drops its FCM token; Android
 * has neither yet.)
 */
class DeleteAccountViewModel(
    private val currentUid: () -> String?,
    private val deleter: AccountDeleter,
    private val positions: SimpaticoPositionStore,
    private val calendarFlags: AddedToCalendarStore,
    private val signOut: () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(DeleteAccountUiState())
    val state: StateFlow<DeleteAccountUiState> = _state.asStateFlow()

    fun onConfirmationChange(text: String) = _state.update { it.copy(confirmation = text) }

    fun delete() {
        if (!_state.value.canDelete) return
        val uid = currentUid()
        if (uid == null) {
            _state.update { it.copy(failed = true) }
            return
        }
        _state.update { it.copy(isDeleting = true, failed = false) }
        viewModelScope.launch {
            val planIDs = try {
                deleter.deleteMyAccount()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isDeleting = false, failed = true) }
                return@launch
            }
            positions.clear(uid)
            calendarFlags.clear(planIDs)
            signOut()
        }
    }

    fun dismissError() = _state.update { it.copy(failed = false) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AtxFriendsApp
                val c = app.container
                DeleteAccountViewModel(
                    currentUid = { c.session.currentProfile?.id },
                    deleter = c.account,
                    positions = SharedPrefsSimpaticoPositionStore(app),
                    calendarFlags = SharedPrefsAddedToCalendarStore(app),
                    signOut = c.session::signOut,
                )
            }
        }
    }
}

/** Port of iOS DeleteAccountView. */
@Composable
fun DeleteAccountScreen(onBack: () -> Unit, viewModel: DeleteAccountViewModel = viewModel(factory = DeleteAccountViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors
    val danger = SettingsColors.dangerStrong

    SettingsSubScreen(title = stringResource(R.string.delete_title), onBack = onBack, backEnabled = !state.isDeleting) {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(painterResource(R.drawable.ic_set_warning), contentDescription = null, tint = danger, modifier = Modifier.size(60.dp))
                Text(stringResource(R.string.delete_title), style = atxText(28.sp, FontWeight.Bold), color = colors.textStrong)
                Text(stringResource(R.string.delete_warning), style = atxText(16.sp, FontWeight.Medium), color = danger, textAlign = TextAlign.Center)
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.delete_what_header), style = atxText(20.sp, FontWeight.Bold), color = colors.textStrong, modifier = Modifier.padding(horizontal = 20.dp))
                IconListCard(
                    tint = danger,
                    rows = listOf(
                        Triple(R.drawable.ic_person, R.string.delete_profile_title, R.string.delete_profile_desc),
                        Triple(R.drawable.ic_people, R.string.delete_matches_title, R.string.delete_matches_desc),
                        Triple(R.drawable.ic_tab_messages_selected, R.string.delete_messages_title, R.string.delete_messages_desc),
                        Triple(R.drawable.ic_set_calendar, R.string.delete_plans_title, R.string.delete_plans_desc),
                    ),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.delete_confirm_header), style = atxText(20.sp, FontWeight.Bold), color = colors.textStrong, modifier = Modifier.padding(horizontal = 20.dp))
                Column(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.cardBackground.copy(alpha = 0.6f)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.delete_confirm_body), style = atxText(15.sp), color = colors.secondaryText)
                    val placeholder = stringResource(R.string.delete_confirm_placeholder)
                    val style = atxText(17.sp, FontWeight.Medium).copy(color = colors.textStrong)
                    val shape = RoundedCornerShape(12.dp)
                    BasicTextField(
                        value = state.confirmation,
                        onValueChange = viewModel::onConfirmationChange,
                        enabled = !state.isDeleting,
                        singleLine = true,
                        textStyle = style,
                        cursorBrush = SolidColor(colors.primaryText),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(colors.cardBackground.copy(alpha = 0.8f))
                            .border(if (state.isConfirmed) 2.dp else 1.dp, if (state.isConfirmed) danger else colors.border, shape)
                            .padding(16.dp)
                            .semantics { contentDescription = placeholder },
                        decorationBox = { inner ->
                            Box {
                                if (state.confirmation.isEmpty()) Text(placeholder, style = style.copy(color = colors.textSubtle))
                                inner()
                            }
                        },
                    )
                }
            }

            WideButton(
                text = stringResource(if (state.isDeleting) R.string.delete_button_busy else R.string.delete_button),
                icon = R.drawable.ic_set_trash,
                background = if (state.isConfirmed) danger else colors.textSubtle,
                busy = state.isDeleting,
                enabled = state.canDelete,
                onClick = viewModel::delete,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp),
            )
        }
    }

    if (state.failed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.delete_error_title)) },
            text = { Text(stringResource(R.string.delete_error_body)) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}
