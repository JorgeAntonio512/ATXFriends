package com.georgeappdev.atxfriends.ui.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.auth.BackButton
import com.georgeappdev.atxfriends.ui.auth.ErrorCard
import com.georgeappdev.atxfriends.ui.components.AtxLabeledField
import com.georgeappdev.atxfriends.ui.components.AtxPrimaryButton
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/** Port of iOS SignUpView: email, password, confirm → "Create Account". */
@Composable
fun SignUpScreen(viewModel: SignUpViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors
    val focus = LocalFocusManager.current
    val submit = {
        focus.clearFocus()
        viewModel.submit()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .safeDrawingPadding()
            .imePadding()
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight)) {
            BackButton(onBack)
            Column(Modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.sign_up_title), style = atxText(28.sp, FontWeight.Bold), color = colors.primaryText, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.sign_up_subtitle), style = atxText(16.sp), color = colors.secondaryText)
                }

                AtxLabeledField(
                    label = stringResource(R.string.field_email),
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    placeholder = stringResource(R.string.field_email_placeholder),
                    icon = R.drawable.ic_mail,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                )
                AtxLabeledField(
                    label = stringResource(R.string.field_password),
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    placeholder = stringResource(R.string.sign_up_password_placeholder),
                    icon = R.drawable.ic_lock,
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                )
                AtxLabeledField(
                    label = stringResource(R.string.sign_up_confirm_label),
                    value = state.confirmPassword,
                    onValueChange = viewModel::onConfirmChange,
                    placeholder = stringResource(R.string.sign_up_confirm_placeholder),
                    icon = R.drawable.ic_lock,
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )

                state.error?.let { ErrorCard(it) }

                AtxPrimaryButton(
                    text = stringResource(R.string.sign_up_button),
                    isLoading = state.isLoading,
                    onClick = submit,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.appPrimary.copy(alpha = 0.1f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(R.drawable.ic_set_info), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.sign_up_next_title), style = atxText(13.sp, FontWeight.SemiBold), color = colors.textBody)
                    }
                    Text(stringResource(R.string.sign_up_next_body), style = atxText(12.sp), color = colors.secondaryText)
                }

                // iOS: "// placeholder" — no real links wired yet.
                Text(
                    stringResource(R.string.sign_up_terms),
                    style = atxText(11.sp),
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                )
            }
        }
    }
}
