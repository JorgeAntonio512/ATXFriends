package com.georgeappdev.atxfriends.ui.signup

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import com.georgeappdev.atxfriends.domain.profile.AvailabilityRules
import com.georgeappdev.atxfriends.domain.profile.NameRules
import com.georgeappdev.atxfriends.domain.profile.PhotoRules
import com.georgeappdev.atxfriends.domain.profile.SetupStep
import com.georgeappdev.atxfriends.domain.profile.icon
import com.georgeappdev.atxfriends.domain.profile.timeRange
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.settings.CategoryMenu
import com.georgeappdev.atxfriends.ui.settings.PhotoEncoder
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/** Port of iOS ProfileSetupFlowView: progress bar + the current step. */
@Composable
fun ProfileSetupFlow(viewModel: ProfileSetupViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors

    BackHandler(enabled = state.step != SetupStep.NAME && !state.isSaving) { viewModel.back() }

    Column(Modifier.fillMaxSize().background(colors.appBackground).statusBarsPadding()) {
        if (state.step != SetupStep.COMPLETE) {
            ProgressBar(state.step, Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp))
        }
        Box(Modifier.weight(1f)) {
            when (state.step) {
                SetupStep.NAME -> NameStep(state, viewModel)
                SetupStep.PHOTOS -> PhotosStep(state, viewModel)
                SetupStep.ACTIVITIES -> ActivitiesStep(state, viewModel)
                SetupStep.TIME_SLOTS -> TimeSlotsStep(state, viewModel)
                SetupStep.COMPLETE -> CompleteStep(state, onEnter = viewModel::save)
            }
        }
    }

    state.saveError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSaveError,
            title = { Text(stringResource(R.string.setup_save_error_title)) },
            text = { Text(stringResource(error)) },
            confirmButton = { TextButton(onClick = viewModel::dismissSaveError) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

/** ProfileSetupProgressBar: 8 pt track, burnt-orange fill, "Step n of 4". */
@Composable
private fun ProgressBar(step: SetupStep, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    val progress by animateFloatAsState(step.number.toFloat() / SetupStep.PROGRESS_STEPS, spring(dampingRatio = 0.8f), label = "progress")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.cardBackground.copy(alpha = 0.3f))) {
            Box(Modifier.fillMaxWidth(progress).height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.appPrimary))
        }
        Text(
            stringResource(R.string.setup_step_counter, step.number, SetupStep.PROGRESS_STEPS),
            style = atxText(13.sp, FontWeight.Medium),
            color = colors.secondaryText,
        )
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    val colors = AtxTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp, start = 40.dp, end = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = atxText(32.sp, FontWeight.Bold), color = colors.primaryText, textAlign = TextAlign.Center)
        Text(subtitle, style = atxText(17.sp).copy(lineHeight = 23.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
    }
}

/** Continue + Back pinned under each step. */
private fun StepButtons(enabled: Boolean, onNext: () -> Unit, onBack: (() -> Unit)?, disabledAlpha: Float = 0.3f): @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
    SetupContinueButton(stringResource(R.string.action_continue), enabled = enabled, onClick = onNext, disabledAlpha = disabledAlpha)
    if (onBack != null) TextLinkButton(stringResource(R.string.action_back), onClick = onBack)
}

@Composable
private fun TipsCard(title: String, lines: List<String>, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.appPrimary.copy(alpha = 0.1f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_set_lightbulb), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(18.dp))
            Text(title, style = atxText(15.sp, FontWeight.SemiBold), color = colors.textBody)
        }
        lines.forEach { Text(it, style = atxText(14.sp), color = colors.secondaryText) }
    }
}

// Step 1 — NameInputView

@Composable
private fun NameStep(state: ProfileSetupUiState, viewModel: ProfileSetupViewModel) {
    val colors = AtxTheme.colors
    val focus = LocalFocusManager.current
    val nameValid = NameRules.isValid(state.displayName)
    PinnedBottomLayout(
        content = { modifier ->
            Column(modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                StepHeader(stringResource(R.string.setup_name_title), stringResource(R.string.setup_name_subtitle))
                Box(Modifier.size(140.dp).clip(CircleShape).background(colors.appPrimary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_person_circle), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(80.dp))
                }

                Column(Modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(stringResource(R.string.setup_name_label))
                    SetupTextField(
                        value = state.displayName,
                        onValueChange = viewModel::onNameChange,
                        placeholder = stringResource(R.string.setup_name_placeholder),
                        label = stringResource(R.string.setup_name_label),
                        textSize = 18,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = {
                            if (nameValid) focus.moveFocus(FocusDirection.Down) else viewModel.next()
                        }),
                    )
                    if (state.showNameError && !nameValid) {
                        Row(
                            Modifier.padding(start = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                            Text(stringResource(R.string.setup_name_error), style = atxText(13.sp, FontWeight.Medium), color = Color.Red.copy(alpha = 0.8f))
                        }
                    }
                    Counter(stringResource(R.string.setup_name_count, NameRules.characterCount(state.displayName)))
                }

                Column(Modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(stringResource(R.string.setup_bio_label))
                    SetupTextField(
                        value = state.bio,
                        onValueChange = viewModel::onBioChange,
                        placeholder = stringResource(R.string.setup_bio_placeholder),
                        label = stringResource(R.string.setup_bio_label),
                        textSize = 16,
                        singleLine = false,
                        minHeight = 100,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    Counter(stringResource(R.string.setup_bio_count, NameRules.characterCount(state.bio)))
                }

                TipsCard(
                    stringResource(R.string.setup_tips_title),
                    listOf(stringResource(R.string.setup_name_tip_1), stringResource(R.string.setup_name_tip_2), stringResource(R.string.setup_name_tip_3)),
                    Modifier.padding(horizontal = 32.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
        },
        bottomBar = StepButtons(enabled = nameValid, onNext = viewModel::next, onBack = null, disabledAlpha = 0.5f),
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = atxText(14.sp, FontWeight.SemiBold), color = AtxTheme.colors.textBody, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun Counter(text: String) {
    Box(Modifier.fillMaxWidth().padding(end = 4.dp), contentAlignment = Alignment.CenterEnd) {
        Text(text, style = atxText(12.sp), color = AtxTheme.colors.textMuted)
    }
}

@Composable
private fun SetupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    textSize: Int,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minHeight: Int = 0,
) {
    val colors = AtxTheme.colors
    val style = atxText(textSize.sp, if (singleLine) FontWeight.Medium else FontWeight.Normal).copy(color = colors.textStrong)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = style,
        cursorBrush = SolidColor(colors.primaryText),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (minHeight > 0) Modifier.height(minHeight.dp) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardBackground.copy(alpha = 0.9f))
            .padding(16.dp)
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = colors.textMuted, fontWeight = FontWeight.Normal))
                inner()
            }
        },
    )
}

// Step 2 — PhotoPickerView

@Composable
private fun PhotosStep(state: ProfileSetupUiState, viewModel: ProfileSetupViewModel) {
    val colors = AtxTheme.colors
    val context = LocalContext.current
    val encoder = remember { PhotoEncoder(context.contentResolver) }
    var preview by remember { mutableStateOf<ByteArray?>(null) }

    val onPicked = { uris: List<Uri> ->
        val picks = uris.take(viewModel.photosNeeded())
        viewModel.addPhotos(picks.size) { i -> encoder.encode(picks[i]) }
    }
    // iOS `maxSelectionCount: 3 - selected.count`; Android's multi-picker needs at least 2.
    val pickThree = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { onPicked(it) }
    val pickTwo = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(2)) { onPicked(it) }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> onPicked(listOfNotNull(uri)) }
    val openPicker = {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        when (viewModel.photosNeeded()) {
            3 -> pickThree.launch(request)
            2 -> pickTwo.launch(request)
            1 -> pickOne.launch(request)
            else -> Unit
        }
    }

    Box {
        PinnedBottomLayout(
            content = { modifier ->
                Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    StepHeader(stringResource(R.string.setup_photos_title), stringResource(R.string.setup_photos_subtitle))
                    Column(Modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(PhotoRules.SLOT_COUNT) { index ->
                            val photo = state.photos.getOrNull(index)
                            if (photo != null) {
                                FilledPhotoSlot(index, photo, onTap = { preview = photo }, onRemove = { viewModel.removePhoto(index) })
                            } else {
                                EmptyPhotoSlot(index, enabled = !state.photosLoading, onAdd = openPicker)
                            }
                        }
                        state.photoError?.let {
                            Text(
                                stringResource(it),
                                style = atxText(13.sp, FontWeight.Medium),
                                color = colors.danger,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }
                    TipsCard(
                        stringResource(R.string.photos_tips_title),
                        listOf(stringResource(R.string.photos_tip_1), stringResource(R.string.photos_tip_2), stringResource(R.string.photos_tip_3)),
                        Modifier.padding(horizontal = 32.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                }
            },
            bottomBar = StepButtons(enabled = state.canContinue, onNext = viewModel::next, onBack = viewModel::back),
        )
        if (state.photosLoading) LoadingOverlay(stringResource(R.string.setup_photos_loading))
    }

    preview?.let { FullScreenPhoto(it, onDismiss = { preview = null }) }
}

@Composable
private fun FilledPhotoSlot(index: Int, photo: ByteArray, onTap: () -> Unit, onRemove: () -> Unit) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val viewLabel = stringResource(R.string.setup_photo_view, index + 1)
    val removeLabel = stringResource(R.string.setup_photo_remove, index + 1)
    Box(Modifier.fillMaxWidth().height(180.dp)) {
        AsyncImage(
            model = photo,
            contentDescription = viewLabel,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(colors.cardBackground)
                .border(2.dp, colors.appPrimary, shape)
                .clickable(role = Role.Button, onClick = onTap),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(32.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(colors.danger)
                .clickable(role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = removeLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_set_close), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun EmptyPhotoSlot(index: Int, enabled: Boolean, onAdd: () -> Unit) {
    val colors = AtxTheme.colors
    val primary = colors.appPrimary
    Column(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBackground.copy(alpha = 0.5f))
            .drawBehind {
                drawRoundRect(
                    color = primary.copy(alpha = 0.4f),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onAdd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(R.drawable.ic_set_photo), contentDescription = null, tint = primary, modifier = Modifier.size(40.dp))
        Text(stringResource(R.string.setup_photo_slot, index + 1), style = atxText(16.sp, FontWeight.SemiBold), color = colors.secondaryText)
    }
}

/** FullScreenPhotoView: black, pinch to zoom, double-tap to reset. */
@Composable
private fun FullScreenPhoto(photo: ByteArray, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    val shown by animateFloatAsState(scale, label = "zoom")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(0.5f, 5f) }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { scale = 1f }, onTap = { if (scale <= 1f) onDismiss() })
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val s = if (scale < 1f) 1f else shown
                    scaleX = s
                    scaleY = s
                },
            )
        }
    }
}

@Composable
private fun LoadingOverlay(text: String) {
    val colors = AtxTheme.colors
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
        Column(
            Modifier.shadow(20.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(colors.cardBackground).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = colors.appPrimary)
            Text(text, style = atxText(16.sp, FontWeight.Medium), color = colors.primaryText)
        }
    }
}

// Step 3 — ActivityPickerView

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivitiesStep(state: ProfileSetupUiState, viewModel: ProfileSetupViewModel) {
    val colors = AtxTheme.colors
    val focus = LocalFocusManager.current
    Box {
        PinnedBottomLayout(
            content = { modifier ->
                Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    StepHeader(stringResource(R.string.setup_activities_title), stringResource(R.string.setup_activities_subtitle))

                    if (state.activities.isNotEmpty()) {
                        Column(Modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ChipGroup(stringResource(R.string.setup_activities_main, state.mainActivities.size), state.mainActivities, viewModel)
                            ChipGroup(stringResource(R.string.setup_activities_extras, state.extraActivities.size), state.extraActivities, viewModel)
                        }
                    }

                    Column(Modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivitySearchField(state.search, viewModel::onSearchChange)
                        if (state.showAddOption) {
                            AddActivityOption(state.search.trim(), enabled = !state.isAddingActivity) { category ->
                                focus.clearFocus()
                                viewModel.addCustomActivity(category)
                            }
                        }
                        if (state.addActivityFailed) InlineError(stringResource(R.string.activities_add_failed))
                        if (state.activityLimitHit) InlineError(stringResource(R.string.setup_activities_max))
                    }

                    when {
                        state.catalogLoading || state.isAddingActivity -> Column(
                            Modifier.fillMaxWidth().padding(vertical = 60.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CircularProgressIndicator(color = colors.appPrimary)
                            Text(stringResource(R.string.setup_activities_loading), style = atxText(18.sp, FontWeight.SemiBold), color = colors.textStrong)
                        }
                        state.search.isEmpty() -> SearchPlaceholder(stringResource(R.string.setup_activities_start_title), stringResource(R.string.setup_activities_start_body))
                        state.searchResults.isEmpty() && !state.showAddOption ->
                            SearchPlaceholder(stringResource(R.string.setup_activities_none_title), stringResource(R.string.setup_activities_none_body))
                        state.searchResults.isNotEmpty() -> Column(Modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.searchResults.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { activity ->
                                        ActivityCard(activity.name, state.isSelected(activity.id), Modifier.weight(1f)) { viewModel.toggleActivity(activity) }
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            },
            bottomBar = StepButtons(enabled = state.canContinue, onNext = viewModel::next, onBack = viewModel::back),
        )
    }

    if (state.catalogFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCatalogError,
            title = { Text(stringResource(R.string.setup_activities_load_error_title)) },
            text = { Text(stringResource(R.string.setup_activities_load_error_body)) },
            confirmButton = { TextButton(onClick = viewModel::loadCatalog) { Text(stringResource(R.string.action_retry)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissCatalogError) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(title: String, activities: List<ProfileActivity>, viewModel: ProfileSetupViewModel) {
    if (activities.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = atxText(14.sp, FontWeight.SemiBold), color = AtxTheme.colors.secondaryText, modifier = Modifier.padding(horizontal = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            activities.forEach { activity ->
                ActivityChip(activity, onToggleMain = { viewModel.makeMain(activity.id) }, onRemove = { viewModel.removeActivity(activity.id) })
            }
        }
    }
}

/** iOS SelectedActivityChip: star (Main/Extra), name, remove ✕ on a burnt-orange pill. */
@Composable
private fun ActivityChip(activity: ProfileActivity, onToggleMain: () -> Unit, onRemove: () -> Unit) {
    val starLabel = if (activity.isPrimary) stringResource(R.string.activities_is_main, activity.name)
    else stringResource(R.string.activities_make_main, activity.name)
    val removeLabel = stringResource(R.string.activities_remove, activity.name)
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(AtxTheme.colors.appPrimary).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).clickable(enabled = !activity.isPrimary, role = Role.Button, onClick = onToggleMain)
                .semantics { contentDescription = starLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (activity.isPrimary) R.drawable.ic_set_star else R.drawable.ic_set_star_outline),
                contentDescription = null,
                tint = Color.White.copy(alpha = if (activity.isPrimary) 1f else 0.7f),
                modifier = Modifier.size(14.dp),
            )
        }
        Text(activity.name, style = atxText(14.sp, FontWeight.SemiBold), color = Color.White)
        Box(
            Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onRemove).semantics { contentDescription = removeLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_close_circle), contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ActivitySearchField(value: String, onChange: (String) -> Unit) {
    val colors = AtxTheme.colors
    val placeholder = stringResource(R.string.activities_search_placeholder)
    val style = atxText(16.sp).copy(color = colors.primaryText)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.cardBackground.copy(alpha = 0.7f)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_set_search), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(20.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(colors.primaryText),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = style.copy(color = colors.textSubtle))
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            val clear = stringResource(R.string.setup_activities_clear_search)
            Icon(
                painterResource(R.drawable.ic_close_circle),
                contentDescription = clear,
                tint = colors.secondaryText,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable(role = Role.Button) { onChange("") },
            )
        }
    }
}

/** Inline "Add \"…\" — Choose a category to create it" (a category is required). */
@Composable
private fun AddActivityOption(name: String, enabled: Boolean, onPick: (com.georgeappdev.atxfriends.domain.matching.ActivityCategory) -> Unit) {
    val colors = AtxTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.appPrimary.copy(alpha = 0.15f))
                .clickable(enabled = enabled, role = Role.Button) { menuOpen = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_set_add_circle), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.setup_activities_add, name), style = atxText(15.sp, FontWeight.SemiBold), color = colors.textStrong)
                Text(stringResource(R.string.setup_activities_add_hint), style = atxText(13.sp), color = colors.textTertiary)
            }
            Icon(painterResource(R.drawable.ic_set_arrow_circle), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(18.dp))
        }
        CategoryMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, onPick = {
            menuOpen = false
            onPick(it)
        })
    }
}

@Composable
private fun InlineError(text: String) {
    Text(
        text,
        style = atxText(13.sp, FontWeight.Medium),
        color = AtxTheme.colors.danger,
        modifier = Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun SearchPlaceholder(title: String, body: String) {
    val colors = AtxTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(R.drawable.ic_set_search), contentDescription = null, tint = colors.appPrimary.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
        Text(title, style = atxText(18.sp, FontWeight.SemiBold), color = colors.textStrong, textAlign = TextAlign.Center)
        Text(body, style = atxText(15.sp), color = colors.textTertiary, textAlign = TextAlign.Center)
    }
}

/** iOS ActivityCard: two-column grid tile; burnt orange when picked. */
@Composable
private fun ActivityCard(name: String, selected: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) colors.appPrimary else colors.cardBackground.copy(alpha = 0.7f))
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.appPrimary else Color.Gray.copy(alpha = 0.2f), shape)
            .clickable(role = Role.Checkbox, onClick = onTap)
            .semantics { toggleableState = ToggleableState(selected) }
            .padding(horizontal = 12.dp, vertical = 16.dp)
            .height(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            style = atxText(15.sp, FontWeight.SemiBold),
            color = if (selected) Color.White else colors.textStrong,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

// Step 4 — TimeSlotPickerView

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeSlotsStep(state: ProfileSetupUiState, viewModel: ProfileSetupViewModel) {
    val colors = AtxTheme.colors
    PinnedBottomLayout(
        content = { modifier ->
            Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                StepHeader(stringResource(R.string.setup_times_title), stringResource(R.string.setup_times_subtitle))
                if (state.combos.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.setup_times_selected, state.combos.size),
                            style = atxText(14.sp, FontWeight.SemiBold),
                            color = colors.secondaryText,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.combos.forEach { combo -> TimeChip(combo, onRemove = { viewModel.removeSlot(combo) }) }
                        }
                    }
                }
                Column(Modifier.padding(start = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AvailabilityRules.days.forEach { day ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(day.raw.orEmpty(), style = atxText(16.sp, FontWeight.Bold), color = colors.primaryText, modifier = Modifier.padding(start = 4.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(end = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AvailabilityRules.slots.forEach { slot ->
                                    val selected = state.isSelected(day, slot)
                                    val shape = RoundedCornerShape(12.dp)
                                    Column(
                                        Modifier
                                            .width(90.dp)
                                            .height(70.dp)
                                            .clip(shape)
                                            .background(if (selected) colors.appPrimary else colors.cardBackground.copy(alpha = 0.7f))
                                            .border(if (selected) 2.dp else 1.dp, if (selected) colors.appPrimary else Color.Gray.copy(alpha = 0.2f), shape)
                                            .clickable(role = Role.Checkbox) { viewModel.toggleSlot(day, slot) }
                                            .semantics(mergeDescendants = true) {
                                                contentDescription = "${day.raw} ${slot.raw}, ${slot.timeRange}"
                                                toggleableState = ToggleableState(selected)
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                                    ) {
                                        Text("${slot.icon} ${slot.raw}", style = atxText(12.sp, FontWeight.SemiBold), color = if (selected) Color.White else colors.textBody, maxLines = 1)
                                        Text(slot.timeRange, style = atxText(10.sp), color = if (selected) Color.White.copy(alpha = 0.8f) else colors.textMuted, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        },
        bottomBar = StepButtons(enabled = state.canContinue, onNext = viewModel::next, onBack = viewModel::back),
    )
}

@Composable
private fun TimeChip(combo: DaySlotCombo, onRemove: () -> Unit) {
    val label = stringResource(R.string.availability_remove, "${combo.day.raw} ${combo.slot.raw}")
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(AtxTheme.colors.appPrimary).padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${combo.slot.icon} ${combo.day.raw} ${combo.slot.raw}", style = atxText(14.sp, FontWeight.SemiBold), color = Color.White)
        Box(
            Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onRemove).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_close_circle), contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
        }
    }
}

// Step 5 — ProfileCompletionView

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompleteStep(state: ProfileSetupUiState, onEnter: () -> Unit) {
    val colors = AtxTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.size(160.dp).clip(CircleShape).background(colors.appPrimary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(120.dp)
                    .shadow(20.dp, CircleShape, ambientColor = colors.appPrimary.copy(alpha = 0.3f), spotColor = colors.appPrimary.copy(alpha = 0.3f))
                    .clip(CircleShape).background(colors.appPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(60.dp))
            }
        }
        Column(Modifier.padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(R.string.setup_done_title),
                style = atxText(36.sp, FontWeight.Bold).copy(brush = Brush.horizontalGradient(listOf(colors.primaryText, colors.appPrimary))),
                textAlign = TextAlign.Center,
            )
            Text(stringResource(R.string.setup_done_subtitle), style = atxText(18.sp).copy(lineHeight = 26.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
        }

        Column(Modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryCard(R.drawable.ic_set_photo, stringResource(R.string.setup_done_photos), stringResource(R.string.setup_done_photos_value, state.photos.size))
            SummaryCard(R.drawable.ic_set_heart, stringResource(R.string.setup_done_activities), stringResource(R.string.setup_done_activities_sub)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.activities.forEach { SummaryPill(it.name) }
                }
            }
            SummaryCard(R.drawable.ic_tab_upcoming, stringResource(R.string.setup_done_availability), stringResource(R.string.setup_done_availability_sub)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.combos.forEach { SummaryPill("${it.slot.icon} ${it.day.raw} ${it.slot.raw}") }
                }
            }
        }

        Column(
            Modifier.padding(horizontal = 32.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.appPrimary.copy(alpha = 0.1f)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_sparkles), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.setup_done_next_title), style = atxText(16.sp, FontWeight.SemiBold), color = colors.textStrong)
            }
            Text(stringResource(R.string.setup_done_next_body), style = atxText(15.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
        }

        Box(Modifier.padding(horizontal = 32.dp).padding(top = 8.dp)) {
            NavyButton(
                text = stringResource(R.string.setup_done_enter),
                icon = null,
                isLoading = state.isSaving,
                loadingText = stringResource(R.string.setup_done_saving),
                onClick = onEnter,
                trailingIcon = R.drawable.ic_arrow_forward,
            )
        }
    }
}

@Composable
private fun SummaryCard(icon: Int, title: String, subtitle: String, content: (@Composable () -> Unit)? = null) {
    val colors = AtxTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.cardBackground.copy(alpha = 0.6f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(colors.appPrimary), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = atxText(16.sp, FontWeight.Bold), color = colors.textStrong)
                Text(subtitle, style = atxText(14.sp), color = colors.secondaryText)
            }
        }
        content?.invoke()
    }
}

@Composable
private fun SummaryPill(text: String) {
    val primary = AtxTheme.colors.appPrimary
    Text(
        text,
        style = atxText(14.sp, FontWeight.SemiBold),
        color = primary,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(primary.copy(alpha = 0.15f)).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
