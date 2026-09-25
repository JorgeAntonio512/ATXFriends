package com.georgeappdev.atxfriends.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.domain.profile.PhotoRules
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/** Port of iOS PhotosSettingsView: three fixed slots, tap one to replace just that photo. */
@Composable
fun PhotosScreen(onBack: () -> Unit, viewModel: PhotosViewModel = viewModel(factory = PhotosViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors
    var replacingIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    // Android's photo picker needs no storage permission (like iOS PHPicker).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val index = replacingIndex
        replacingIndex = null
        if (uri != null && index != null) viewModel.replace(index, uri)
    }
    val pick = { index: Int ->
        replacingIndex = index
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    SettingsSubScreen(title = stringResource(R.string.settings_row_photos), onBack = onBack, backEnabled = state.uploading.isEmpty()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            ScreenHeader(
                stringResource(R.string.photos_title),
                stringResource(R.string.photos_subtitle),
                Modifier.padding(top = 20.dp, start = 40.dp, end = 40.dp),
            )

            Row(Modifier.padding(horizontal = 32.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(PhotoRules.SLOT_COUNT) { index ->
                    PhotoSlot(
                        index = index,
                        state = state,
                        onTap = { if (index !in state.uploading) pick(index) },
                    )
                }
            }

            state.error?.let { SettingsErrorBanner(stringResource(it), Modifier.padding(horizontal = 32.dp)) }

            InfoBox(
                R.drawable.ic_set_lightbulb,
                stringResource(R.string.photos_tips_title),
                listOf(stringResource(R.string.photos_tip_1), stringResource(R.string.photos_tip_2), stringResource(R.string.photos_tip_3)),
                Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

@Composable
private fun PhotoSlot(index: Int, state: PhotosUiState, onTap: () -> Unit) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val model: Any? = state.localJpegs[index] ?: state.url(index)
    val label = if (model != null) stringResource(R.string.photos_slot_description, index + 1)
    else stringResource(R.string.photos_empty_slot_description, index + 1)

    Box(
        Modifier
            .size(100.dp)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .clickable(role = Role.Button, onClick = onTap),
    ) {
        if (model != null) {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = {
                    Box(Modifier.fillMaxSize().background(colors.cardBackground), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.appPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                },
                error = { Box(Modifier.fillMaxSize().background(colors.cardBackground)) },
                modifier = Modifier.fillMaxSize().clip(shape).border(2.dp, colors.appPrimary, shape),
            )
            // iOS draws a red ✕ badge that, like tapping the photo, opens the picker.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .size(28.dp)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.Red),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_set_close), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        } else {
            val primary = colors.appPrimary
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(primary.copy(alpha = 0.2f))
                    .drawBehind {
                        drawRoundRect(
                            color = primary,
                            cornerRadius = CornerRadius(16.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = primary, modifier = Modifier.size(32.dp))
            }
        }

        if (index in state.uploading) {
            Column(
                Modifier.fillMaxSize().clip(shape).background(Color.Black.copy(alpha = 0.6f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.photos_uploading), style = atxText(10.sp, FontWeight.Medium), color = Color.White)
            }
        }
    }
}
