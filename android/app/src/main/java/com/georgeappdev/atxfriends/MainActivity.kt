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
}
