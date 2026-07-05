package com.example.testgame01.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

data class GameState(
    val balls: List<Ball> = emptyList(),
    val blocks: List<Block> = emptyList(),
    val ballCount: Int = 1,
    val score: Int = 0,
    val phase: GamePhase = GamePhase.Idle,
    val aimDirection: Offset = Offset.Zero,
    val ballLaunchOrigin: Offset = Offset.Zero,
    val canvasSize: Size = Size.Zero,
    val turn: Int = 1
)
