package com.example.feynman

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.feynman.ui.AppScreen
import com.example.feynman.ui.AppSettings
import com.example.feynman.ui.theme.FeynmanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        setContent {
            FeynmanTheme {
                // Keep the screen on while the app is open, if chosen in settings.
                val view = androidx.compose.ui.platform.LocalView.current
                val keepOn = AppSettings.keepScreenOn
                androidx.compose.runtime.SideEffect { view.keepScreenOn = keepOn }
                AppScreen()
            }
        }
    }
}
