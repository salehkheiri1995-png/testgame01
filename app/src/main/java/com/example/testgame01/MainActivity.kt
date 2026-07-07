package com.example.testgame01

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.testgame01.ui.GameScreen
import com.example.testgame01.ui.theme.Testgame01Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON )
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.parseColor("#FF16213E")),
            navigationBarStyle = SystemBarStyle.dark(Color.parseColor("#FF1A1A2E"))
        )
        setContent {
            Testgame01Theme {
                GameScreen()
            }
        }
    }
}
