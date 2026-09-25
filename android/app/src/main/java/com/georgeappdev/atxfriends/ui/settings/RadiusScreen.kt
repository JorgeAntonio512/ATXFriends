package com.georgeappdev.atxfriends.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
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
import com.georgeappdev.atxfriends.data.repository.UserWrites
import com.georgeappdev.atxfriends.domain.profile.RadiusRules
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class RadiusUiState(
    val miles: Int = 10,
    val savedMiles: Double = 10.0,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val done: Boolean = false,
) {
    val hasChanges: Boolean get() = miles.toDouble() != savedMiles
}

/** iOS SearchRadiusSettingsView: a 5–25 mile slider saved with the toolbar Save. */
class RadiusViewModel(private val edits: ProfileEdits) : ViewModel() {
    private val _state = MutableStateFlow(
        (edits.profile?.radiusMiles ?: 10.0).let { RadiusUiState(miles = RadiusRules.clampToSlider(it), savedMiles = it) },
    )
    val state: StateFlow<RadiusUiState> = _state.asStateFlow()

    fun onChange(miles: Int) = _state.update { it.copy(miles = miles.coerceIn(RadiusRules.MIN, RadiusRules.MAX)) }

    fun save() {
        val current = _state.value
        if (!current.hasChanges || current.isSaving) return
        val miles = current.miles
        _state.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                edits.save({ now -> UserWrites.radius(miles, now) }) { p, now -> p.copy(radiusMiles = miles.toDouble(), updatedAt = now) }
                _state.update { it.copy(isSaving = false, savedMiles = miles.toDouble(), done = true) }
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
                RadiusViewModel(ProfileEdits(c.session, c.profiles))
            }
        }
    }
}

@Composable
fun RadiusScreen(onBack: () -> Unit, viewModel: RadiusViewModel = viewModel(factory = RadiusViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors

    LaunchedEffect(state.done) { if (state.done) onBack() }

    SettingsSubScreen(
        title = stringResource(R.string.radius_title),
        onBack = onBack,
        backEnabled = !state.isSaving,
        action = { SaveAction(enabled = state.hasChanges, isSaving = state.isSaving, onClick = viewModel::save) },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(
                Modifier.padding(top = 20.dp, start = 40.dp, end = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeaderIconCircle(R.drawable.ic_location)
                Text(stringResource(R.string.radius_title), style = atxText(28.sp, FontWeight.Bold), color = colors.primaryText)
                Text(stringResource(R.string.radius_subtitle), style = atxText(16.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
            }

            val unit = stringResource(if (state.miles == 1) R.string.radius_mile else R.string.radius_miles)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${state.miles}", style = atxText(72.sp, FontWeight.Bold), color = colors.appPrimary)
                Text(unit, style = atxText(20.sp, FontWeight.Medium), color = colors.secondaryText)
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Slider(
                    value = state.miles.toFloat(),
                    onValueChange = { viewModel.onChange(it.roundToInt()) },
                    valueRange = RadiusRules.MIN.toFloat()..RadiusRules.MAX.toFloat(),
                    steps = RadiusRules.MAX - RadiusRules.MIN - 1,
                    enabled = !state.isSaving,
                    colors = SliderDefaults.colors(thumbColor = colors.appPrimary, activeTrackColor = colors.appPrimary),
                    modifier = Modifier.semantics { stateDescription = "${state.miles} $unit" },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.radius_min_label), style = atxText(13.sp, FontWeight.Medium), color = colors.textMuted)
                    Text(stringResource(R.string.radius_max_label), style = atxText(13.sp, FontWeight.Medium), color = colors.textMuted)
                }
            }

            DistanceGuide(Modifier.padding(horizontal = 32.dp))

            InfoBox(
                R.drawable.ic_set_info,
                stringResource(R.string.radius_how_title),
                listOf(
                    stringResource(R.string.radius_how_1),
                    stringResource(R.string.radius_how_2),
                    stringResource(R.string.radius_how_3),
                    stringResource(R.string.radius_how_4),
                ),
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

/** The "Distance Guide" card (static marketing copy on iOS, not the real distance buckets). */
@Composable
private fun DistanceGuide(modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.appPrimary.copy(alpha = 0.1f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_set_map), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.radius_guide_title), style = atxText(15.sp, FontWeight.SemiBold), color = colors.textBody)
        }
        listOf(
            R.string.radius_guide_1_distance to R.string.radius_guide_1_desc,
            R.string.radius_guide_2_distance to R.string.radius_guide_2_desc,
            R.string.radius_guide_3_distance to R.string.radius_guide_3_desc,
        ).forEach { (distance, desc) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_set_arrow_circle), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(distance), style = atxText(14.sp, FontWeight.SemiBold), color = colors.textStrong)
                    Text(stringResource(desc), style = atxText(12.sp), color = colors.secondaryText)
                }
            }
        }
    }
}
