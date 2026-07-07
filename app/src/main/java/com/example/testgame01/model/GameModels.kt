package com.example.testgame01.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

data class Ball(
    val id: Int,
    val position: Offset,
    val velocity: Offset,
    val isMoving: Boolean = false,
    val isReturned: Boolean = false
)

data class Block(
    val id: String,
    val hp: Int,
    val maxHp: Int,
    val col: Int,
    val row: Int,
    val isDestroyed: Boolean = false,
    val color: Color = randomBlockColor()
)

enum class GamePhase { Idle, Aiming, Shooting, GameOver }

fun randomBlockColor(): Color {
    val palette = listOf(
        Color(0xFF3A86FF), Color(0xFFFF006E), Color(0xFFFB5607),
        Color(0xFFFFBE0B), Color(0xFF8338EC), Color(0xFF06D6A0)
    )
    return palette[Random.nextInt(palette.size)]
}
