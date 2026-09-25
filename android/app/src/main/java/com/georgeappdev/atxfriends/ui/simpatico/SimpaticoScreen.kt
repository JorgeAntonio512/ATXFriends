package com.georgeappdev.atxfriends.ui.simpatico

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.model.SimpaticoImportance
import com.georgeappdev.atxfriends.domain.simpatico.SimpaticoOption
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/** Port of iOS `SimpaticoView`: intro → 12-question flow → "All done!". */
@Composable
fun SimpaticoScreen(
    modifier: Modifier = Modifier,
    viewModel: SimpaticoViewModel = viewModel(factory = SimpaticoViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors

    Column(modifier.fillMaxSize().background(colors.appBackground)) {
        // iOS large navigation title.
        Text(
            stringResource(R.string.tab_simpatico),
            style = atxText(34.sp, FontWeight.Bold),
            color = colors.primaryText,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp).semantics { heading() },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    color = colors.appPrimary,
                    modifier = Modifier.align(Alignment.Center),
                )
                state.isComplete -> SimpaticoCompleteView(state, onEdit = viewModel::startEditing)
                state.showIntro -> SimpaticoIntroView(state.showUpgradeBanner, onStart = viewModel::start)
                else -> SimpaticoQuestionFlow(state, viewModel)
            }
        }
    }
}

// region Intro

@Composable
private fun SimpaticoIntroView(showUpgradeBanner: Boolean, onStart: () -> Unit) {
    val colors = AtxTheme.colors
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            SmilingBadge(size = 100.dp, iconSize = 50.dp, modifier = Modifier.align(Alignment.CenterHorizontally))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(if (showUpgradeBanner) R.string.simpatico_intro_title_upgrade else R.string.simpatico_intro_title),
                    style = atxText(26.sp, FontWeight.Bold),
                    color = colors.primaryText,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(if (showUpgradeBanner) R.string.simpatico_intro_body_upgrade else R.string.simpatico_intro_body),
                    style = atxText(15.sp).copy(lineHeight = 22.sp),
                    color = colors.secondaryText,
                )
            }

            val cardShape = RoundedCornerShape(18.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .softShadow(12.dp, cardShape, 0.06f)
                    .clip(cardShape)
                    .background(colors.cardBackground)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IntroFactRow(R.drawable.ic_verified, R.string.simpatico_intro_fact_questions)
                HorizontalDivider(Modifier.padding(start = 38.dp), color = colors.border)
                IntroFactRow(R.drawable.ic_schedule, R.string.simpatico_intro_fact_time)
                HorizontalDivider(Modifier.padding(start = 38.dp), color = colors.border)
                IntroFactRow(R.drawable.ic_cloud_upload, R.string.simpatico_intro_fact_saves)
            }
        }

        val shape = RoundedCornerShape(14.dp)
        Row(
            Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .softShadow(8.dp, shape, 0.30f, colors.appPrimary)
                .clip(shape)
                .background(colors.appPrimary)
                .clickable(role = Role.Button, onClick = onStart),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.simpatico_start), style = atxText(17.sp, FontWeight.SemiBold), color = Color.White)
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun IntroFactRow(@DrawableRes icon: Int, @StringRes text: Int) {
    val colors = AtxTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = colors.appPrimary,
            modifier = Modifier.padding(top = 1.dp).width(24.dp).height(18.dp),
        )
        Text(stringResource(text), style = atxText(14.sp).copy(lineHeight = 20.sp), color = colors.secondaryText)
    }
}

// endregion

// region Question flow

@Composable
private fun SimpaticoQuestionFlow(state: SimpaticoUiState, viewModel: SimpaticoViewModel) {
    val colors = AtxTheme.colors
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ProgressHeader(state, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp))
            AnimatedContent(
                targetState = state.currentIndex,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "simpaticoQuestion",
            ) { _ ->
                QuestionCard(state, viewModel, Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp))
            }
        }

        state.error?.let { error ->
            Text(
                stringResource(error.message),
                style = atxText(13.sp, FontWeight.Medium),
                color = colors.danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp),
            )
        }
        NavButtons(state, viewModel, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp))
    }
}

private val SimpaticoError.message: Int
    get() = when (this) {
        SimpaticoError.LOAD -> R.string.simpatico_error_load
        SimpaticoError.SAVE -> R.string.simpatico_error_save
        SimpaticoError.FINISH -> R.string.simpatico_error_finish
    }

@Composable
private fun ProgressHeader(state: SimpaticoUiState, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    val progress by animateFloatAsState(
        (state.currentIndex + 1).toFloat() / state.totalCount,
        animationSpec = tween(300),
        label = "simpaticoProgress",
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // SpaceBetween + a non-filling weight looks the same as the old Spacer layout, but at
        // large font sizes the progress text wraps instead of squeezing the category pill away.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.simpatico_question_progress, state.currentIndex + 1, state.totalCount),
                style = atxText(15.sp, FontWeight.SemiBold),
                color = colors.secondaryText,
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
            )
            Text(
                state.currentQuestion.category.displayName,
                style = atxText(12.sp, FontWeight.Medium),
                color = colors.appPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.appPrimary.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(colors.border.copy(alpha = 0.5f))
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(colors.appPrimary))
        }
    }
}

@Composable
private fun QuestionCard(state: SimpaticoUiState, viewModel: SimpaticoViewModel, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    val question = state.currentQuestion
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .softShadow(16.dp, shape, 0.07f)
            .clip(shape)
            .background(colors.cardBackground)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(question.prompt, style = atxText(22.sp, FontWeight.Bold), color = colors.primaryText)
        HorizontalDivider(color = colors.border)

        OptionSection(R.string.simpatico_your_answer) {
            question.options.forEach { option ->
                OptionButton(
                    text = option.text,
                    isSelected = state.selectedAnswerID == option.id,
                    interaction = Modifier.selectable(
                        selected = state.selectedAnswerID == option.id,
                        role = Role.RadioButton,
                        onClick = { viewModel.selectAnswer(option.id) },
                    ),
                )
            }
        }

        OptionSection(R.string.simpatico_good_with) {
            question.options.forEach { option: SimpaticoOption ->
                val checked = option.id in state.selectedAcceptable
                OptionButton(
                    text = option.text,
                    isSelected = checked,
                    interaction = Modifier.toggleable(
                        value = checked,
                        role = Role.Checkbox,
                        onValueChange = { viewModel.toggleAcceptable(option.id) },
                    ),
                )
            }
        }

        if (state.doesNotMatter) {
            Text(stringResource(R.string.simpatico_doesnt_matter), style = atxText(15.sp, FontWeight.SemiBold), color = colors.appPrimary)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.simpatico_how_much), style = atxText(15.sp, FontWeight.SemiBold), color = colors.primaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IMPORTANCE_BUTTONS.forEach { (importance, label) ->
                        val selected = state.selectedImportance == importance
                        OptionButton(
                            text = stringResource(label),
                            isSelected = selected,
                            interaction = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = { viewModel.selectImportance(importance) }),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** iOS `SimpaticoImportance.allCases` with their display names. */
private val IMPORTANCE_BUTTONS = listOf(
    SimpaticoImportance.LITTLE to R.string.simpatico_importance_little,
    SimpaticoImportance.SOMEWHAT to R.string.simpatico_importance_somewhat,
    SimpaticoImportance.VERY to R.string.simpatico_importance_very,
)

@Composable
private fun OptionSection(@StringRes title: Int, options: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(title), style = atxText(15.sp, FontWeight.SemiBold), color = AtxTheme.colors.primaryText)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { options() }
    }
}

/** iOS `SimpaticoOptionButton`: tinted pill, filled burnt orange with a checkmark when selected. */
@Composable
private fun OptionButton(text: String, isSelected: Boolean, interaction: Modifier, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(interaction)
            .background(if (isSelected) colors.appPrimary else colors.appPrimary.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text,
            style = atxText(14.sp, if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (isSelected) Color.White else colors.appPrimary,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun NavButtons(state: SimpaticoUiState, viewModel: SimpaticoViewModel, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.canGoBack) {
            val back = stringResource(R.string.action_back)
            Box(
                Modifier
                    .size(52.dp)
                    .clip(shape)
                    .background(colors.cardBackground)
                    .clickable(role = Role.Button, onClick = viewModel::goBack)
                    .semantics { contentDescription = back },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(20.dp))
            }
        }

        Box(
            Modifier
                .heightIn(min = 52.dp)
                .clip(shape)
                .background(colors.cardBackground)
                .clickable(enabled = !state.isSaving, role = Role.Button, onClick = viewModel::skip)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.simpatico_skip), style = atxText(16.sp, FontWeight.SemiBold), color = colors.appPrimary)
        }

        val enabled = state.canAdvance && !state.isSaving
        val content = if (state.canAdvance) Color.White else colors.secondaryText
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .then(if (state.canAdvance) Modifier.softShadow(8.dp, shape, 0.30f, colors.appPrimary) else Modifier)
                .clip(shape)
                .background(if (state.canAdvance) colors.appPrimary else colors.border)
                .clickable(enabled = enabled, role = Role.Button, onClick = viewModel::saveAndAdvance),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    stringResource(if (state.isLastQuestion) R.string.simpatico_finish else R.string.simpatico_next),
                    style = atxText(16.sp, FontWeight.SemiBold),
                    color = content,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    painterResource(if (state.isLastQuestion) R.drawable.ic_check else R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// endregion

// region Complete

@Composable
private fun SimpaticoCompleteView(state: SimpaticoUiState, onEdit: () -> Unit) {
    val colors = AtxTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        SmilingBadge(size = 130.dp, iconSize = 64.dp)

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.simpatico_all_done),
                style = atxText(30.sp, FontWeight.Bold),
                color = colors.primaryText,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.simpatico_answered_count, state.answeredCount, state.totalCount),
                style = atxText(16.sp, FontWeight.Medium),
                color = colors.secondaryText,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardBackground)
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_sparkles),
                contentDescription = null,
                tint = colors.appPrimary,
                modifier = Modifier.padding(top = 1.dp).size(20.dp),
            )
            Text(
                stringResource(R.string.simpatico_complete_body),
                style = atxText(15.sp).copy(lineHeight = 21.sp),
                color = colors.secondaryText,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.cardBackground)
                .clickable(role = Role.Button, onClick = onEdit),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.simpatico_edit_answers), style = atxText(16.sp, FontWeight.SemiBold), color = colors.appPrimary)
        }
    }
}

// endregion

/** iOS `face.smiling.fill` on a 20%-tinted burnt-orange circle. */
@Composable
private fun SmilingBadge(size: Dp, iconSize: Dp, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Box(
        modifier.size(size).clip(CircleShape).background(colors.appPrimary.copy(alpha = 0.20f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_tab_simpatico_selected), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(iconSize))
    }
}

/** iOS `.shadow(color: color.opacity(alpha), radius:)`. */
private fun Modifier.softShadow(radius: Dp, shape: Shape, alpha: Float, color: Color = Color.Black) =
    shadow(radius, shape, clip = false, ambientColor = color.copy(alpha = alpha), spotColor = color.copy(alpha = alpha))
