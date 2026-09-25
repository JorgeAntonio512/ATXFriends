package com.georgeappdev.atxfriends.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.settings.NotificationPermissionPrefs
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/**
 * iOS "Stay Connected!" (NotificationPermissionPromptView + MatchesViewModel): right after a
 * Yay makes a mutual match, offer once to turn on notifications — only if this device has never
 * been asked and the system permission is still undecided. Android before 13 needs no permission,
 * so it's never shown there. [requested] is the view model's one-shot signal; [onHandled] clears
 * it whether or not the card is shown. Shown after the celebration card closes ([canShow]).
 */
@Composable
fun StayConnectedPrompt(requested: Boolean, canShow: Boolean, onHandled: () -> Unit) {
    val context = LocalContext.current
    var visible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(requested) {
        if (!requested) return@LaunchedEffect
        onHandled()
        val ask = StayConnectedRules.shouldOffer(
            alreadyOffered = StayConnectedPrefs.wasOffered(context),
            needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            granted = isGranted(context),
            systemPromptShown = NotificationPermissionPrefs.wasAsked(context),
        )
        Log.i(TAG, "notifications: new mutual match — offer Stay Connected? $ask")
        visible = ask
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Log.i(TAG, "notifications: Stay Connected prompt answered, granted=$granted")
    }

    if (!visible || !canShow) return
    StayConnectedCard(
        onEnable = {
            visible = false
            StayConnectedPrefs.markOffered(context)
            NotificationPermissionPrefs.markAsked(context)
            Log.i(TAG, "notifications: Stay Connected → showing the system prompt")
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onDismiss = {
            visible = false
            StayConnectedPrefs.markOffered(context)
            Log.i(TAG, "notifications: Stay Connected → Not Now")
        },
    )
}

/** The decision, separated for tests. */
object StayConnectedRules {
    fun shouldOffer(alreadyOffered: Boolean, needsRuntimePermission: Boolean, granted: Boolean, systemPromptShown: Boolean): Boolean =
        !alreadyOffered && needsRuntimePermission && !granted && !systemPromptShown
}

/** iOS UserDefaults `hasBeenAskedForNotifications`: set once the card is answered, never cleared. */
private object StayConnectedPrefs {
    private const val FILE = "push"
    private const val KEY = "hasBeenAskedForNotifications"

    fun wasOffered(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)
    fun markOffered(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(KEY, true) }
}

private fun isGranted(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
private fun StayConnectedCard(onEnable: () -> Unit, onDismiss: () -> Unit) {
    val colors = AtxTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            // Tapping outside doesn't dismiss, as on iOS.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .shadow(20.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(colors.cardBackground)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(Modifier.size(80.dp).clip(CircleShape).background(colors.appPrimary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_set_bell_active), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(36.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.push_prompt_title), style = atxText(24.sp, FontWeight.Bold), color = colors.primaryText)
                Text(stringResource(R.string.push_prompt_body), style = atxText(16.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = colors.appNavy.copy(alpha = 0.3f), spotColor = colors.appNavy.copy(alpha = 0.3f))
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.appNavy)
                        .clickable(role = Role.Button, onClick = onEnable),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_set_bell_active), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.push_prompt_enable), style = atxText(17.sp, FontWeight.SemiBold), color = Color.White)
                }
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.push_prompt_not_now), style = atxText(16.sp, FontWeight.Medium), color = colors.secondaryText)
                }
            }
        }
    }
}
