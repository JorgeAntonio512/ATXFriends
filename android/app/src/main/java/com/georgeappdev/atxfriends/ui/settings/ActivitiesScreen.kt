package com.georgeappdev.atxfriends.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import com.georgeappdev.atxfriends.domain.matching.ActivityCategory
import com.georgeappdev.atxfriends.domain.profile.ActivitySelection
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/** Port of iOS ActivitiesSettingsView. */
@Composable
fun ActivitiesScreen(onBack: () -> Unit, viewModel: ActivitiesViewModel = viewModel(factory = ActivitiesViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors
    val focus = LocalFocusManager.current
    var showSheet by rememberSaveable { mutableStateOf(false) }

    // A successful add from the sheet closes it (iOS dismiss()).
    LaunchedEffect(state.customAdded) { if (state.customAdded > 0) showSheet = false }
    // At the 10-activity cap the search keyboard goes away (iOS isSearchFocused = false).
    LaunchedEffect(state.canAddMore) { if (!state.canAddMore) focus.clearFocus() }

    SettingsSubScreen(
        title = stringResource(R.string.settings_row_activities),
        onBack = onBack,
        backEnabled = state.canLeave,
    ) {
        LazyColumn(Modifier.fillMaxSize().imePadding()) {
            if (!state.isValid) {
                item {
                    SelectionWarningBanner(
                        stringResource(R.string.activities_warning_title),
                        stringResource(R.string.activities_warning_count, state.main.size),
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                    )
                }
            }
            item {
                ScreenHeader(
                    stringResource(R.string.activities_title),
                    stringResource(R.string.activities_subtitle),
                    Modifier.padding(top = 20.dp, bottom = 16.dp, start = 24.dp, end = 24.dp),
                )
            }
            if (state.saveFailed) {
                item {
                    SettingsErrorBanner(
                        stringResource(R.string.settings_autosave_failed),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        onRetry = viewModel::retrySave,
                    )
                }
            }

            if (state.main.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.activities_main_header, state.main.size)) }
                items(state.main, key = { "main-${it.id}" }) { activity ->
                    SelectedActivityChip(activity, onToggleMain = { viewModel.makeMain(activity.id) }, onRemove = { viewModel.remove(activity.id) })
                }
            }
            if (state.extras.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.activities_extras_header, state.extras.size), top = 16.dp) }
                items(state.extras, key = { "extra-${it.id}" }) { activity ->
                    SelectedActivityChip(activity, onToggleMain = { viewModel.makeMain(activity.id) }, onRemove = { viewModel.remove(activity.id) })
                }
            }

            item { SearchField(state.search, viewModel::onSearchChange, Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 16.dp)) }

            when {
                !state.canAddMore -> item { MaxReached() }
                state.catalogLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.appPrimary)
                    }
                }
                state.catalogFailed -> item {
                    SettingsErrorBanner(
                        stringResource(R.string.activities_load_failed),
                        Modifier.padding(horizontal = 16.dp),
                        onRetry = viewModel::loadCatalog,
                    )
                }
                else -> {
                    item { SectionLabel(stringResource(R.string.activities_available)) }
                    items(state.available, key = { "avail-${it.id}" }) { activity ->
                        AvailableActivityRow(activity.name, onClick = { viewModel.pick(activity) })
                    }
                    if (state.addFailed) {
                        item {
                            SettingsErrorBanner(stringResource(R.string.activities_add_failed), Modifier.padding(horizontal = 32.dp, vertical = 6.dp))
                        }
                    }
                    when {
                        state.showInlineAdd -> item {
                            InlineAddButton(
                                name = state.inlineAddName,
                                enabled = !state.isAdding,
                                onPick = { category ->
                                    focus.clearFocus()
                                    viewModel.addCustom(state.search, category, fromSheet = false)
                                },
                            )
                        }
                        state.showAlreadyExists -> item { AlreadyExists() }
                        state.showAddCustomButton -> item { AddCustomButton(onClick = { showSheet = true }) }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }

        if (state.isSaving) {
            SavingIndicator(stringResource(R.string.settings_saving), Modifier.align(Alignment.TopCenter).padding(top = 80.dp))
        }
        if (state.isAdding && !showSheet) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
                SavingIndicator(stringResource(R.string.activities_adding))
            }
        }
    }

    if (showSheet) {
        AddCustomActivitySheet(
            isAdding = state.isAdding,
            addFailed = state.addFailed,
            onAdd = { name, category -> viewModel.addCustom(name, category, fromSheet = true) },
            onDismiss = {
                showSheet = false
                viewModel.dismissAddError()
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String, top: Dp = 0.dp) {
    Text(
        text,
        style = atxText(14.sp, FontWeight.SemiBold),
        color = AtxTheme.colors.textBody,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = top, bottom = 12.dp),
    )
}

/** iOS ActivityChip: filled orange row with a Main/Extra star and a remove ✕. */
@Composable
private fun SelectedActivityChip(activity: ProfileActivity, onToggleMain: () -> Unit, onRemove: () -> Unit) {
    val removeLabel = stringResource(R.string.activities_remove, activity.name)
    val starLabel = if (activity.isPrimary) stringResource(R.string.activities_is_main, activity.name)
    else stringResource(R.string.activities_make_main, activity.name)
    Row(
        Modifier
            .padding(start = 32.dp, end = 32.dp, bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AtxTheme.colors.appPrimary)
            .clickable(role = Role.Button, onClickLabel = removeLabel, onClick = onRemove)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(enabled = !activity.isPrimary, role = Role.Button, onClick = onToggleMain)
                .semantics { contentDescription = starLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (activity.isPrimary) R.drawable.ic_set_star else R.drawable.ic_set_star_outline),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(activity.name, style = atxText(15.sp, FontWeight.SemiBold), color = Color.White, modifier = Modifier.weight(1f))
        Icon(
            painterResource(R.drawable.ic_close_circle),
            contentDescription = removeLabel,
            tint = Color.White,
            modifier = Modifier.padding(end = 8.dp).size(18.dp),
        )
    }
}

/** iOS ActivityRow: heart, name, plus. */
@Composable
private fun AvailableActivityRow(name: String, onClick: () -> Unit) {
    val colors = AtxTheme.colors
    Row(
        Modifier
            .padding(start = 32.dp, end = 32.dp, bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardBackground.copy(alpha = 0.6f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_heart_outline), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(22.dp))
        }
        Text(name, style = atxText(16.sp, FontWeight.Medium), color = colors.textStrong, modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_set_add_circle_outline), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    val placeholder = stringResource(R.string.activities_search_placeholder)
    val style = atxText(16.sp).copy(color = colors.primaryText)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardBackground.copy(alpha = 0.9f))
            .padding(16.dp),
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
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = style.copy(color = colors.textSubtle))
                    inner()
                }
            },
        )
    }
}

/** "Add '<search>' — choose a category": a dashed button opening the category menu. */
@Composable
private fun InlineAddButton(name: String, enabled: Boolean, onPick: (ActivityCategory) -> Unit) {
    val colors = AtxTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    val primary = colors.appPrimary
    Box(Modifier.padding(horizontal = 32.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(primary.copy(alpha = 0.15f))
                .drawBehind {
                    drawRoundRect(
                        color = primary,
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                    )
                }
                .clickable(enabled = enabled, role = Role.Button) { menuOpen = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_set_add_circle), contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
            Text(stringResource(R.string.activities_add_inline, name), style = atxText(16.sp, FontWeight.SemiBold), color = primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        CategoryMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, onPick = {
            menuOpen = false
            onPick(it)
        })
    }
}

@Composable
internal fun CategoryMenu(expanded: Boolean, onDismiss: () -> Unit, onPick: (ActivityCategory) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        ActivityCategory.entries.forEach { category ->
            DropdownMenuItem(text = { Text(category.displayName) }, onClick = { onPick(category) })
        }
    }
}

@Composable
private fun AlreadyExists() {
    val colors = AtxTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(R.drawable.ic_msg_check_circle), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(40.dp))
        Text(stringResource(R.string.activities_already_exists), style = atxText(16.sp, FontWeight.Medium), color = colors.textBody)
    }
}

@Composable
private fun AddCustomButton(onClick: () -> Unit) {
    val colors = AtxTheme.colors
    Row(
        Modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardBackground.copy(alpha = 0.6f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_set_add_circle), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(24.dp))
        Text(stringResource(R.string.activities_add_custom), style = atxText(16.sp, FontWeight.SemiBold), color = colors.appPrimary)
    }
}

@Composable
private fun MaxReached() {
    val colors = AtxTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp, start = 40.dp, end = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(R.drawable.ic_msg_check_circle), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(60.dp))
        Text(stringResource(R.string.activities_max_title), style = atxText(18.sp, FontWeight.SemiBold), color = colors.primaryText, textAlign = TextAlign.Center)
        Text(stringResource(R.string.activities_max_body), style = atxText(15.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
    }
}

/** iOS AddCustomActivitySheet: name + required category, then "Add Activity". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomActivitySheet(
    isAdding: Boolean,
    addFailed: Boolean,
    onAdd: (String, ActivityCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AtxTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<ActivityCategory?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val canSubmit = name.isNotBlank() && category != null
    val submit = { category?.let { if (name.isNotBlank() && !isAdding) onAdd(name, it) } }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    ModalBottomSheet(
        onDismissRequest = { if (!isAdding) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.appBackground,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !isAdding) {
                    Text(stringResource(R.string.action_cancel), style = atxText(17.sp), color = colors.appPrimary)
                }
            }
            Box(Modifier.size(80.dp).clip(CircleShape).background(colors.appPrimary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_set_add_circle), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(36.dp))
            }
            Column(Modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.activities_add_custom), style = atxText(24.sp, FontWeight.Bold), color = colors.primaryText)
                Text(stringResource(R.string.activities_custom_body), style = atxText(15.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val label = stringResource(R.string.activities_custom_name_label)
                Text(label, style = atxText(14.sp, FontWeight.SemiBold), color = colors.textBody)
                val style = atxText(16.sp).copy(color = colors.primaryText)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = style,
                    enabled = !isAdding,
                    cursorBrush = SolidColor(colors.primaryText),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.cardBackground)
                        .padding(16.dp)
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = label },
                    decorationBox = { inner ->
                        Box {
                            if (name.isEmpty()) Text(stringResource(R.string.activities_custom_name_placeholder), style = style.copy(color = colors.textSubtle))
                            inner()
                        }
                    },
                )
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.activities_custom_category_label), style = atxText(14.sp, FontWeight.SemiBold), color = colors.textBody)
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.cardBackground)
                            .clickable(enabled = !isAdding, role = Role.DropdownList) { menuOpen = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            category?.displayName ?: stringResource(R.string.activities_custom_category_placeholder),
                            style = atxText(16.sp),
                            color = if (category == null) colors.textTertiary else colors.textStrong,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(painterResource(R.drawable.ic_set_unfold), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(20.dp))
                    }
                    CategoryMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, onPick = {
                        category = it
                        menuOpen = false
                    })
                }
            }

            if (addFailed) SettingsErrorBanner(stringResource(R.string.activities_add_failed), Modifier.padding(horizontal = 32.dp))

            val buttonShape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .then(if (canSubmit) Modifier.shadow(12.dp, buttonShape, ambientColor = colors.appPrimary.copy(alpha = 0.3f), spotColor = colors.appPrimary.copy(alpha = 0.3f)) else Modifier)
                    .clip(buttonShape)
                    .background(if (canSubmit) colors.appPrimary else Color.Gray.copy(alpha = 0.3f))
                    .clickable(enabled = canSubmit && !isAdding, role = Role.Button) { submit() },
                contentAlignment = Alignment.Center,
            ) {
                if (isAdding) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                else Text(stringResource(R.string.activities_custom_add_button), style = atxText(18.sp, FontWeight.SemiBold), color = Color.White)
            }
        }
    }
}
