package com.georgeappdev.atxfriends.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.georgeappdev.atxfriends.data.repository.UserWrites
import com.georgeappdev.atxfriends.domain.profile.NameRules
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditNameUiState(
    val name: String = "",
    val savedName: String = "",
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val done: Boolean = false,
) {
    val isValid: Boolean get() = NameRules.isValid(name)
    val hasChanges: Boolean get() = NameRules.trimmed(name) != savedName
    val canSave: Boolean get() = isValid && hasChanges && !isSaving

    /** iOS counts the untrimmed text. */
    val count: Int get() = NameRules.characterCount(name)

    /** Shown once the user has edited the name into something invalid (iOS never shows it). */
    val showValidationError: Boolean get() = hasChanges && !isValid
}

/** iOS EditProfileView: the display name, 2–30 characters, saved with the toolbar Save. */
class EditNameViewModel(private val edits: ProfileEdits) : ViewModel() {
    private val _state = MutableStateFlow(
        edits.profile?.displayName.orEmpty().let { EditNameUiState(name = it, savedName = it) },
    )
    val state: StateFlow<EditNameUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        val name = NameRules.trimmed(current.name)
        _state.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                edits.save({ now -> UserWrites.displayName(name, now) }) { p, now -> p.copy(displayName = name, updatedAt = now) }
                _state.update { it.copy(isSaving = false, savedName = name, done = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(saveFailed = false) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as AtxFriendsApp).container
                EditNameViewModel(ProfileEdits(c.session, c.profiles))
            }
        }
    }
}

@Composable
fun EditNameScreen(onBack: () -> Unit, viewModel: EditNameViewModel = viewModel(factory = EditNameViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors
    val focus = LocalFocusManager.current

    LaunchedEffect(state.done) { if (state.done) onBack() }

    SettingsSubScreen(
        title = stringResource(R.string.settings_row_display_name),
        onBack = onBack,
        backEnabled = !state.isSaving,
        action = {
            SaveAction(enabled = state.canSave, isSaving = state.isSaving) {
                focus.clearFocus()
                viewModel.save()
            }
        },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            HeaderIconCircle(R.drawable.ic_person_circle, Modifier.padding(top = 20.dp))

            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val label = stringResource(R.string.name_field_label)
                Text(label, style = atxText(14.sp, FontWeight.SemiBold), color = colors.textBody, modifier = Modifier.padding(start = 4.dp))
                val textStyle = atxText(18.sp, FontWeight.Medium).copy(color = colors.textStrong)
                BasicTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    enabled = !state.isSaving,
                    singleLine = true,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(colors.primaryText),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focus.clearFocus()
                        viewModel.save()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.cardBackground.copy(alpha = 0.9f))
                        .padding(16.dp)
                        .semantics { contentDescription = label },
                    decorationBox = { inner ->
                        Box {
                            if (state.name.isEmpty()) Text(stringResource(R.string.name_field_placeholder), style = textStyle.copy(color = colors.textSubtle))
                            inner()
                        }
                    },
                )
                if (state.showValidationError) {
                    Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val red = Color.Red.copy(alpha = 0.8f)
                        Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = red, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.name_error), style = atxText(13.sp, FontWeight.Medium), color = red)
                    }
                }
                Text(
                    stringResource(R.string.name_counter, state.count),
                    style = atxText(12.sp),
                    color = colors.textMuted,
                    modifier = Modifier.align(Alignment.End).padding(end = 4.dp),
                )
            }

            InfoBox(
                R.drawable.ic_set_info,
                stringResource(R.string.settings_tips),
                listOf(stringResource(R.string.name_tip_1), stringResource(R.string.name_tip_2), stringResource(R.string.name_tip_3)),
                Modifier.padding(horizontal = 32.dp),
            )
        }
    }

    if (state.saveFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.settings_error_title)) },
            text = { Text(stringResource(R.string.settings_save_failed)) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}
