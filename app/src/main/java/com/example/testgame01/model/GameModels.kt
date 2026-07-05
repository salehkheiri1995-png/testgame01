package com.example.testgame01.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

data class Ball(
    val id: Int,
    var position: Offset,
    var velocity: Offset,
    var isActive: Boolean = false,
    var isReturned: Boolean = false
)

data class Block(
    val id: Int,
    var hp: Int,
    var col: Int,
    var row: Int,
    var isDestroyed: Boolean = false,
    var alpha: Float = 1f
) {
    val color: Color
        get() = when {
            hp <= 3 -> Color(0xFF4CAF50)
            hp <= 8 -> Color(0xFF8BC34A)
            hp <= 13 -> Color(0xFFFFC107)
            hp <= 18 -> Color(0xFFFF9800)
            else -> Color(0xFFF44336)
        }
}

sealed class GamePhase {
    object Idle : GamePhase()
    object Aiming : GamePhase()
    object Shooting : GamePhase()
    object GameOver : GamePhase()
}
