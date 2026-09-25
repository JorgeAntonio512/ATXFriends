package com.georgeappdev.atxfriends.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.theme.AppFontFamily
import com.georgeappdev.atxfriends.ui.theme.AtxRadius
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/** iOS text style helper: every iOS call site is `.system(size:weight:design: .rounded)`. */
fun atxText(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontSize = size, fontWeight = weight, fontFamily = AppFontFamily)

/** The labeled input used on iOS sign-in: label above, icon + field inside a bordered card. */
@Composable
fun AtxLabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = AtxTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(AtxRadius.md)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = atxText(14.sp, FontWeight.SemiBold), color = colors.textBody)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.cardBackground)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) colors.appPrimary else Color.Gray.copy(alpha = 0.2f),
                    shape = shape,
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(20.dp))
            }
            val textStyle = atxText(16.sp).copy(color = colors.primaryText)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(colors.primaryText),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                interactionSource = interaction,
                modifier = Modifier.weight(1f).semantics { contentDescription = label },
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, style = textStyle.copy(color = colors.textSubtle))
                        inner()
                    }
                },
            )
        }
    }
}

/** iOS primary action button: navy, 56pt tall, 16pt corners, soft navy shadow. */
@Composable
fun AtxPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(AtxRadius.lg)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .shadow(12.dp, shape, ambientColor = colors.appNavy.copy(alpha = 0.3f), spotColor = colors.appNavy.copy(alpha = 0.3f))
            .clip(shape)
            .background(colors.appNavy)
            .clickable(enabled = !isLoading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(text, style = atxText(18.sp, FontWeight.SemiBold), color = Color.White)
        }
    }
}

/** iOS secondary button: card background with a thin border, primary-text label. */
@Composable
fun AtxSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(AtxRadius.lg)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(colors.cardBackground)
            .border(1.dp, colors.border, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = atxText(18.sp, FontWeight.SemiBold), color = colors.primaryText)
    }
}

/** Burnt-orange circle with a white symbol — the logo mark on iOS launch/onboarding screens. */
@Composable
fun AtxLogoMark(
    @DrawableRes icon: Int,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .shadow(20.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))
            .clip(CircleShape)
            .background(AtxTheme.colors.appPrimary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

/** "ATX Friends" wordmark with the iOS primaryText → burnt-orange gradient. */
@Composable
fun AtxWordmark(fontSize: TextUnit, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Text(
        text = stringResource(R.string.app_name),
        style = atxText(fontSize, FontWeight.Bold).copy(
            brush = Brush.horizontalGradient(listOf(colors.primaryText, colors.appPrimary)),
        ),
        modifier = modifier,
    )
}
