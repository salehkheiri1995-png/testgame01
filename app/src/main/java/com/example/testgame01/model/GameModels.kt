package com.example.testgame01.model

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

data class Block(
    val id: Int,
    val hp: Int,
    val col: Int,
    val row: Int,
    val isDestroyed: Boolean = false,
    val color: Color = randomBlockColor(),
    val alpha: Float = 1f
)

data class Ball(
    val id: Int,
    val position: androidx.compose.ui.geometry.Offset,
    val velocity: androidx.compose.ui.geometry.Offset,  // unit direction vector
    val isActive: Boolean = true,
    val isReturned: Boolean = false,
    val launchDelaySeconds: Float = 0f                  // seconds before this ball activates
)

enum class GamePhase { Idle, Aiming, Shooting, GameOver }

fun randomBlockColor(): Color {
    val palette = listOf(
        Color(0xFF3A86FF), Color(0xFFFF006E), Color(0xFFFB5607),
        Color(0xFFFFBE0B), Color(0xFF8338EC), Color(0xFF06D6A0)
    )
    return palette[Random.nextInt(palette.size)]
}
