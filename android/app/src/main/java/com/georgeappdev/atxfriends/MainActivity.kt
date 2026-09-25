package com.georgeappdev.atxfriends

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.georgeappdev.atxfriends.push.PushRoute
import com.georgeappdev.atxfriends.push.PushKeys
import com.georgeappdev.atxfriends.ui.root.RootScreen
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Icon-on-background splash, then the normal window theme. Held only until the first
        // frame; the Loading screen covers anything slower.
        installSplashScreen()
        Log.d(TAG, "onCreate: splash installed (restored=${savedInstanceState != null})")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A cold start from a tapped push. (After a rotation the tap was already handled.)
        if (savedInstanceState == null) handlePushTap(intent)
        setContent {
            AtxTheme {
                RootScreen()
            }
        }
    }

    /** A tapped push while the app is already running (warm start; launchMode singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePushTap(intent)
    }

    /**
     * Android puts a push's `data` on the tap intent as string extras — both for pushes it shows
     * itself (app in background) and for ones the app shows (AtxMessagingService).
     */
    private fun handlePushTap(intent: Intent?) {
        val extras = intent?.extras ?: return
        if (!extras.containsKey(PushKeys.TYPE)) return
        val data = extras.keySet().associateWith { extras.getString(it) }
        val route = PushRoute.fromData(data)
        Log.i("ATXF", "push: tapped, type=${data[PushKeys.TYPE]} → ${route ?: "nowhere to go"}")
        route?.let { (application as AtxFriendsApp).container.pushRoutes.post(it) }
        intent.removeExtra(PushKeys.TYPE) // handled; don't route again
    }

    override fun onStart() {
        super.onStart()
        // A rotation restarts the activity without the app ever leaving the foreground.
        if (!restartingForConfigChange) (application as AtxFriendsApp).container.onAppForeground()
        restartingForConfigChange = false
    }

    override fun onStop() {
        super.onStop()
        restartingForConfigChange = isChangingConfigurations
    }

    private companion object {
        const val TAG = "ATXF"
        var restartingForConfigChange = false
    }
}
