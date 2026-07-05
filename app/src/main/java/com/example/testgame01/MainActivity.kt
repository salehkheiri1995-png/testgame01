package com.example.testgame01

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.example.testgame01.ui.GameScreen
import com.example.testgame01.ui.theme.Testgame01Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen on while playing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Let content go edge-to-edge but handle insets manually
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            Testgame01Theme {
                GameScreen()
            }
        }
    }
}
