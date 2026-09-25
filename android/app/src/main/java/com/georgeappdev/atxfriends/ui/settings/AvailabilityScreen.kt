package com.georgeappdev.atxfriends.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.georgeappdev.atxfriends.data.model.DayOfWeek
import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.TimeSlot
import com.georgeappdev.atxfriends.data.repository.UserWrites
import com.georgeappdev.atxfriends.domain.profile.AvailabilityRules
import com.georgeappdev.atxfriends.domain.profile.displayName
import com.georgeappdev.atxfriends.domain.profile.icon
import com.georgeappdev.atxfriends.domain.profile.timeRange
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AvailabilityUiState(
    val combos: List<DaySlotCombo> = emptyList(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    /** Only combos Android understands are shown; any others are kept untouched when saving. */
    val shown: List<DaySlotCombo> get() = combos.filter { it.isKnown }
    val count: Int get() = AvailabilityRules.count(combos)
    val isValid: Boolean get() = AvailabilityRules.isValid(combos)
    val canLeave: Boolean get() = isValid && !isSaving

    fun isSelected(day: DayOfWeek, slot: TimeSlot) = AvailabilityRules.isSelected(combos, day, slot)
}

/**
 * iOS AvailabilitySettingsView: every tap saves. iOS saves through the full-profile path, which
 * refuses fewer than 3 combos; Android likewise only writes a selection of 3 or more.
 */
class AvailabilityViewModel(private val edits: ProfileEdits) : ViewModel() {
    private val _state = MutableStateFlow(AvailabilityUiState(combos = edits.profile?.daySlotCombos.orEmpty()))
    val state: StateFlow<AvailabilityUiState> = _state.asStateFlow()

    private val saver = AutoSaver<List<DaySlotCombo>>(
        scope = viewModelScope,
        write = { list ->
            edits.save({ now -> UserWrites.daySlotCombos(list, now) }) { p, now -> p.copy(daySlotCombos = list, updatedAt = now) }
        },
        onStatus = { saving, failed -> _state.update { it.copy(isSaving = saving, saveFailed = failed) } },
    )

    fun toggle(day: DayOfWeek, slot: TimeSlot) = change(AvailabilityRules.toggle(_state.value.combos, day, slot))

    fun remove(combo: DaySlotCombo) = change(_state.value.combos - combo)

    fun retrySave() = saver.retry()

    private fun change(after: List<DaySlotCombo>) {
        _state.update { it.copy(combos = after, saveFailed = false) }
        if (AvailabilityRules.isValid(after)) saver.submit(after)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as AtxFriendsApp).container
                AvailabilityViewModel(ProfileEdits(c.session, c.profiles))
            }
        }
    }
}

/** Port of iOS AvailabilitySettingsView (selected chips + 7×5 TimeSlotGrid). */
@Composable
fun AvailabilityScreen(onBack: () -> Unit, viewModel: AvailabilityViewModel = viewModel(factory = AvailabilityViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors

    SettingsSubScreen(
        title = stringResource(R.string.settings_row_availability),
        onBack = onBack,
        backEnabled = state.canLeave,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (!state.isValid) {
                SelectionWarningBanner(
                    stringResource(R.string.availability_warning_title),
                    stringResource(R.string.availability_warning_count, state.count),
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }
            ScreenHeader(
                stringResource(R.string.availability_title),
                stringResource(R.string.availability_subtitle),
                Modifier.padding(top = 20.dp, start = 40.dp, end = 40.dp),
            )
            if (state.saveFailed) {
                SettingsErrorBanner(stringResource(R.string.settings_autosave_failed), Modifier.padding(horizontal = 16.dp), onRetry = viewModel::retrySave)
            }

            if (state.shown.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 32.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.availability_selected_count, state.count), style = atxText(14.sp, FontWeight.SemiBold), color = colors.textBody)
                    state.shown.forEach { combo -> SelectedSlotChip(combo, onRemove = { viewModel.remove(combo) }) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(R.string.availability_grid_header),
                    style = atxText(14.sp, FontWeight.SemiBold),
                    color = colors.textBody,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                TimeSlotGrid(state, onToggle = viewModel::toggle)
            }
        }
        if (state.isSaving) {
            SavingIndicator(stringResource(R.string.settings_saving), Modifier.align(Alignment.TopCenter).padding(top = 80.dp))
        }
    }
}

@Composable
private fun SelectedSlotChip(combo: DaySlotCombo, onRemove: () -> Unit) {
    val colors = AtxTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.appPrimary)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${combo.slot.icon} ${combo.displayName}", style = atxText(15.sp, FontWeight.SemiBold), color = Color.White, modifier = Modifier.weight(1f))
        val label = stringResource(R.string.availability_remove, combo.displayName)
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_close_circle), contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
        }
    }
}

/** iOS TimeSlotGrid: slot headers across the top, one row per day. */
@Composable
private fun TimeSlotGrid(state: AvailabilityUiState, onToggle: (DayOfWeek, TimeSlot) -> Unit) {
    val colors = AtxTheme.colors
    val dayWidth = 80.dp
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Spacer(Modifier.width(dayWidth))
            AvailabilityRules.slots.forEach { slot ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(slot.icon, style = atxText(16.sp))
                    Text(slot.raw.orEmpty(), style = atxText(10.sp, FontWeight.SemiBold), color = colors.textBody, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(slot.timeRange, style = atxText(9.sp), color = colors.textMuted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        AvailabilityRules.days.forEach { day ->
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(day.raw.orEmpty().take(3), style = atxText(12.sp, FontWeight.SemiBold), color = colors.textBody, modifier = Modifier.width(dayWidth - 4.dp))
                AvailabilityRules.slots.forEach { slot ->
                    val selected = state.isSelected(day, slot)
                    val shape = RoundedCornerShape(6.dp)
                    val label = "${day.raw} ${slot.raw}"
                    Box(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(shape)
                            .background(if (selected) colors.appPrimary else colors.cardBackground.copy(alpha = 0.6f))
                            .border(1.dp, if (selected) Color.Transparent else colors.appPrimary.copy(alpha = 0.3f), shape)
                            .clickable(role = Role.Checkbox) { onToggle(day, slot) }
                            .semantics {
                                contentDescription = label
                                toggleableState = ToggleableState(selected)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
