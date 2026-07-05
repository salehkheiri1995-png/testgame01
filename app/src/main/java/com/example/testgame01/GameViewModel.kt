package com.example.testgame01

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {

    // ── Observable UI state ──────────────────────────────────────────
    var gameState by mutableStateOf(GameState())
        private set

    private var loopJob: Job? = null

    // ── Canvas size (set from Composable) ────────────────────────────
    fun onCanvasSizeChanged(width: Float, height: Float) {
        if (width == gameState.canvasWidth && height == gameState.canvasHeight) return
        val startX = width / 2f
        val startY = height - GameEngine.BOTTOM_BAR_H / 2f
        val cols = gameState.cols
        val rows = gameState.rows
        val round = gameState.round

        gameState = gameState.copy(
            canvasWidth = width,
            canvasHeight = height,
            ballStartX = startX,
            ballStartY = startY,
            blocks = if (gameState.blocks.isEmpty())
                GameEngine.generateInitialBlocks(cols, rows, round)
            else gameState.blocks
        )
    }

    // ── Drag / Aim ───────────────────────────────────────────────────
    fun onDragStart(pos: Offset) {
        if (gameState.phase != GamePhase.IDLE) return
        gameState = gameState.copy(isDragging = true)
    }

    fun onDrag(dragPoint: Offset) {
        if (!gameState.isDragging) return
        val ballStart = Offset(gameState.ballStartX, gameState.ballStartY)
        val angle = GameEngine.angleDegFromDrag(ballStart, dragPoint)
        gameState = gameState.copy(aimAngleDeg = angle)
    }

    fun onDragEnd() {
        if (!gameState.isDragging) return
        gameState = gameState.copy(isDragging = false)
        shootBalls()
    }

    // ── Shooting ─────────────────────────────────────────────────────
    private fun shootBalls() {
        val state = gameState
        if (state.phase != GamePhase.IDLE) return

        val velocity = GameEngine.velocityFromAngleDeg(state.aimAngleDeg)
        val startPos = Offset(state.ballStartX, state.ballStartY - GameEngine.BALL_RADIUS)

        // Create all balls at start, none active yet
        val balls = GameEngine.createInitialBalls(state.ballCount, startPos.x, startPos.y)

        gameState = gameState.copy(phase = GamePhase.SHOOTING, balls = balls)

        loopJob = viewModelScope.launch {
            // Launch balls one by one with 100ms delay
            var launched = 0
            val activeBalls = balls.map { it.copy() }.toMutableList()

            launch {
                for (i in activeBalls.indices) {
                    activeBalls[i] = activeBalls[i].copy(isActive = true, velocity = velocity)
                    gameState = gameState.copy(balls = activeBalls.toList())
                    delay(100)
                    launched++
                }
            }

            // Game loop at ~60fps
            val blocks = gameState.blocks.toMutableList()
            var totalScore = gameState.score

            while (true) {
                delay(16)

                val blockSize = GameEngine.blockSize(gameState.canvasWidth, gameState.cols)

                for (i in activeBalls.indices) {
                    val (updated, gained) = GameEngine.tickBall(
                        ball = activeBalls[i],
                        blocks = blocks,
                        canvasWidth = gameState.canvasWidth,
                        canvasHeight = gameState.canvasHeight,
                        blockSize = blockSize,
                        cols = gameState.cols,
                        ballStartY = gameState.ballStartY
                    )
                    activeBalls[i] = updated
                    totalScore += gained
                }

                // Animate destroyed blocks
                for (i in blocks.indices) {
                    if (blocks[i].isDestroyed && blocks[i].destroyAnimProgress < 1f) {
                        blocks[i] = blocks[i].copy(destroyAnimProgress = (blocks[i].destroyAnimProgress + 0.08f).coerceAtMost(1f))
                    }
                }

                gameState = gameState.copy(
                    balls = activeBalls.toList(),
                    blocks = blocks.toList(),
                    score = totalScore
                )

                // Check if all launched balls have returned
                val allLaunched = launched >= activeBalls.size
                val allReturned = activeBalls.all { !it.isActive || it.isReturned }

                if (allLaunched && allReturned) break
            }

            // Round end
            onRoundEnd()
        }
    }

    private fun onRoundEnd() {
        val state = gameState
        val cols = state.cols
        val round = state.round + 1

        // Remove fully animated destroyed blocks, advance remaining down
        val surviving = state.blocks.filter { !it.isDestroyed }
        val blockSize = GameEngine.blockSize(state.canvasWidth, cols)
        val maxSafeRow = ((state.ballStartY - GameEngine.TOP_BAR_H) / (blockSize.height + GameEngine.BLOCK_V_PAD)).toInt() - 1

        val (newBlocks, isGameOver) = GameEngine.advanceBlocksAndCheck(surviving, cols, round, maxSafeRow)

        if (isGameOver) {
            gameState = gameState.copy(
                phase = GamePhase.GAME_OVER,
                blocks = newBlocks,
                round = round
            )
            return
        }

        // Snap ball return position to first returned ball's x, or center
        val firstReturned = state.balls.firstOrNull { it.isReturned }
        val newStartX = firstReturned?.position?.x ?: (state.canvasWidth / 2f)

        gameState = gameState.copy(
            phase = GamePhase.IDLE,
            balls = emptyList(),
            blocks = newBlocks,
            ballCount = state.ballCount + 1,
            ballStartX = newStartX,
            round = round
        )
    }

    // ── Restart ──────────────────────────────────────────────────────
    fun restart() {
        loopJob?.cancel()
        val w = gameState.canvasWidth
        val h = gameState.canvasHeight
        val cols = gameState.cols
        val rows = gameState.rows
        gameState = GameState(
            canvasWidth = w,
            canvasHeight = h,
            ballStartX = w / 2f,
            ballStartY = h - GameEngine.BOTTOM_BAR_H / 2f,
            blocks = GameEngine.generateInitialBlocks(cols, rows, 1),
            cols = cols,
            rows = rows
        )
    }
}
