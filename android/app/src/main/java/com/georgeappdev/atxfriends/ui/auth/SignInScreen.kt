package com.georgeappdev.atxfriends.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.auth.AuthMessage
import com.georgeappdev.atxfriends.ui.components.AtxLabeledField
import com.georgeappdev.atxfriends.ui.components.AtxPrimaryButton
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxRadius
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/**
 * Port of iOS SignInView. Existing accounts never see the location gate; "New here? Create an
 * account" (shown for a wrong email/password) leads to it. Sign in with Apple isn't on Android.
 */
@Composable
fun SignInScreen(
    onBack: () -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: SignInViewModel = viewModel(factory = SignInViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors
    val focus = LocalFocusManager.current

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .safeDrawingPadding()
            .imePadding()
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
        ) {
            BackButton(onBack)

            Column(
                Modifier.padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.sign_in_title),
                        style = atxText(28.sp, FontWeight.Bold),
                        color = colors.primaryText,
                        textAlign = TextAlign.Center,
                    )
                    Text(stringResource(R.string.sign_in_subtitle), style = atxText(16.sp), color = colors.secondaryText)
                }

                AtxLabeledField(
                    label = stringResource(R.string.field_email),
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    placeholder = stringResource(R.string.field_email_placeholder),
                    icon = R.drawable.ic_mail,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                )

                AtxLabeledField(
                    label = stringResource(R.string.field_password),
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    placeholder = stringResource(R.string.field_password_placeholder),
                    icon = R.drawable.ic_lock,
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focus.clearFocus()
                        viewModel.submit()
                    }),
                )

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        stringResource(R.string.forgot_password),
                        style = atxText(13.sp, FontWeight.Medium),
                        color = colors.appPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(role = Role.Button, onClick = viewModel::openReset)
                            .padding(4.dp),
                    )
                }

                state.error?.let { error ->
                    ErrorCard(error) {
                        if (error.suggestsAccountCreation) {
                            Text(
                                stringResource(R.string.sign_in_create_account),
                                style = atxText(13.sp, FontWeight.SemiBold),
                                color = colors.appPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(role = Role.Button, onClick = onCreateAccount)
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                }

                AtxPrimaryButton(
                    text = stringResource(R.string.action_sign_in),
                    isLoading = state.isLoading,
                    onClick = {
                        focus.clearFocus()
                        viewModel.submit()
                    },
                )
                OrDivider(fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
                GoogleButton(height = 52.dp, fontSize = 17.sp)
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    state.reset?.let { reset ->
        ForgotPasswordSheet(
            state = reset,
            onEmailChange = viewModel::onResetEmailChange,
            onSend = viewModel::sendReset,
            onDismiss = viewModel::dismissReset,
        )
    }

    if (state.showResetSuccess) {
        AlertDialog(
            onDismissRequest = viewModel::dismissResetSuccess,
            title = { Text(stringResource(R.string.reset_success_title)) },
            text = { Text(stringResource(R.string.reset_success_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissResetSuccess) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

/** iOS toolbar back button: chevron + "Back" in burnt orange. */
@Composable
internal fun BackButton(onBack: () -> Unit) {
    val color = AtxTheme.colors.appPrimary
    Row(
        Modifier
            .padding(start = 8.dp, top = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onBack)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(stringResource(R.string.action_back), style = atxText(17.sp), color = color)
    }
}

@Composable
internal fun ErrorCard(message: AuthMessage, action: (@Composable () -> Unit)? = null) {
    val danger = AtxTheme.colors.danger
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AtxRadius.md))
            .background(danger.copy(alpha = 0.1f))
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = danger, modifier = Modifier.size(20.dp))
            Text(stringResource(message.text), style = atxText(14.sp, FontWeight.Medium), color = danger)
        }
        action?.invoke()
    }
}

/** Port of iOS ForgotPasswordSheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForgotPasswordSheet(
    state: ResetUiState,
    onEmailChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AtxTheme.colors
    val focus = LocalFocusManager.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.appBackground,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel), style = atxText(17.sp), color = colors.appPrimary)
                }
            }
            Box(
                Modifier.size(80.dp).clip(CircleShape).background(colors.appPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_lock_reset), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(36.dp))
            }
            Column(
                Modifier.padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.reset_title), style = atxText(24.sp, FontWeight.Bold), color = colors.primaryText)
                Text(
                    stringResource(R.string.reset_body),
                    style = atxText(15.sp),
                    color = colors.secondaryText,
                    textAlign = TextAlign.Center,
                )
            }
            AtxLabeledField(
                label = stringResource(R.string.field_email),
                value = state.email,
                onValueChange = onEmailChange,
                placeholder = stringResource(R.string.field_email_placeholder),
                icon = R.drawable.ic_mail,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = {
                    focus.clearFocus()
                    onSend()
                }),
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            // iOS shows reset errors on the sign-in screen behind the sheet; here they show in
            // the sheet so they aren't hidden. The wording is the same.
            state.error?.let { Box(Modifier.padding(horizontal = 32.dp)) { ErrorCard(it) } }
            AtxPrimaryButton(
                text = stringResource(R.string.reset_send),
                isLoading = state.isLoading,
                onClick = {
                    focus.clearFocus()
                    onSend()
                },
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}
