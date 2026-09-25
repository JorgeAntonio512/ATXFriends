package com.georgeappdev.atxfriends

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.georgeappdev.atxfriends.ui.root.RootScreen
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AtxTheme {
                RootScreen()
            }
        }
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
        var restartingForConfigChange = false
    }
}
