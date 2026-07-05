package com.example.testgame01.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testgame01.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.*

class GameViewModel : ViewModel() {

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var gameLoopJob: Job? = null
    private var activationJob: Job? = null
    private var idCounter = 0
    val ballRadiusPublic = 22f
    private val ballRadius = ballRadiusPublic
    private val cols = 6
    private val blockPadding = 8f
    private val topBarHeight = 120f
    private val bottomBarHeight = 160f

    private var totalBallsToLaunch = 0
    private var ballsActivated = 0

    private var lastKnownSize = Size.Zero

    fun onCanvasReady(size: Size) {
        if (size == lastKnownSize) return
        lastKnownSize = size
        val origin = Offset(size.width / 2f, size.height - bottomBarHeight)
        val wasEmpty = _state.value.blocks.isEmpty()
        _state.update { it.copy(canvasSize = size, ballLaunchOrigin = origin) }
        if (wasEmpty) {
            spawnInitialBlocks(size)
        }
    }

    private fun spawnInitialBlocks(size: Size) {
        val newBlocks = generateRow(size, row = 0, currentBallCount = 1)
        _state.update { it.copy(blocks = newBlocks) }
    }

    // ---- Aiming ----

    fun onDragStart(position: Offset) {
        val s = _state.value
        // Only allow aiming in Idle phase
        if (s.phase != GamePhase.Idle) return
        _state.update { it.copy(phase = GamePhase.Aiming) }
    }

    fun onDrag(position: Offset) {
        val s = _state.value
        if (s.phase != GamePhase.Aiming) return
        val origin = s.ballLaunchOrigin
        // direction = from launch origin toward touch position
        val raw = Offset(position.x - origin.x, position.y - origin.y)
        val clamped = clampToValidAngle(raw)
        _state.update { it.copy(aimDirection = clamped) }
    }

    fun onDragEnd() {
        val s = _state.value
        if (s.phase != GamePhase.Aiming) return
        val dir = s.aimDirection
        if (dir == Offset.Zero) {
            _state.update { it.copy(phase = GamePhase.Idle) }
            return
        }
        launchBalls(dir)
    }

    private fun clampToValidAngle(dir: Offset): Offset {
        if (dir.x == 0f && dir.y == 0f) return Offset.Zero
        val len = sqrt(dir.x * dir.x + dir.y * dir.y)
        // Minimum drag distance to register aim
        if (len < 15f) return Offset.Zero

        var normX = dir.x / len
        var normY = dir.y / len

        // Force upward direction (negative Y in screen coords)
        if (normY >= 0f) normY = -0.05f

        val minSin = sin(Math.toRadians(10.0)).toFloat()
        if (abs(normY) < minSin) {
            normY = if (normY < 0) -minSin else minSin
            val remaining = sqrt(max(0f, 1f - normY * normY))
            normX = if (normX < 0) -remaining else remaining
        }

        val speed = 18f
        return Offset(normX * speed, normY * speed)
    }

    // ---- Shooting ----

    private fun launchBalls(direction: Offset) {
        val s = _state.value
        totalBallsToLaunch = s.ballCount
        ballsActivated = 0

        val balls = (0 until s.ballCount).map { i ->
            Ball(
                id = i,
                position = s.ballLaunchOrigin,
                velocity = direction,
                isActive = false,
                isReturned = false
            )
        }

        _state.update {
            it.copy(balls = balls, phase = GamePhase.Shooting, aimDirection = Offset.Zero)
        }

        startGameLoop()

        activationJob?.cancel()
        activationJob = viewModelScope.launch {
            for (index in balls.indices) {
                delay(if (index == 0) 0L else index * 120L)
                _state.update { gs ->
                    gs.copy(balls = gs.balls.mapIndexed { i, b ->
                        if (i == index) b.copy(isActive = true) else b
                    })
                }
                ballsActivated++
            }
        }
    }

    // ---- Game Loop ----

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            while (_state.value.phase == GamePhase.Shooting) {
                delay(16L)
                tickUpdate()
            }
        }
    }

    private fun tickUpdate() {
        val s = _state.value
        if (s.phase != GamePhase.Shooting) return
        if (s.canvasSize == Size.Zero) return

        val canvasW = s.canvasSize.width
        val blockSize = blockSizeFor(canvasW)

        val newBlocks = s.blocks.toMutableList()
        var scoreGained = 0

        val updatedBalls = s.balls.map { ball ->
            if (!ball.isActive || ball.isReturned) return@map ball

            var pos = ball.position
            var vel = ball.velocity

            // Wall bouncing
            if (pos.x - ballRadius <= 0f) {
                vel = vel.copy(x = abs(vel.x))
                pos = pos.copy(x = ballRadius)
            }
            if (pos.x + ballRadius >= canvasW) {
                vel = vel.copy(x = -abs(vel.x))
                pos = pos.copy(x = canvasW - ballRadius)
            }
            if (pos.y - ballRadius <= topBarHeight) {
                vel = vel.copy(y = abs(vel.y))
                pos = pos.copy(y = topBarHeight + ballRadius)
            }

            // Block collision
            for (i in newBlocks.indices) {
                val blk = newBlocks[i]
                if (blk.isDestroyed) continue
                val bRect = blockRect(blk, canvasW, blockSize)
                if (circleRectCollide(pos, ballRadius, bRect)) {
                    val newHp = blk.hp - 1
                    scoreGained++
                    newBlocks[i] = if (newHp <= 0) blk.copy(hp = 0, isDestroyed = true)
                    else blk.copy(hp = newHp)
                    vel = reflectBall(vel, pos, bRect)
                    break
                }
            }

            // Move ball forward
            pos = Offset(pos.x + vel.x, pos.y + vel.y)

            // Return check
            val returned = pos.y + ballRadius >= s.ballLaunchOrigin.y
            if (returned) {
                return@map ball.copy(
                    position = s.ballLaunchOrigin,
                    velocity = vel,
                    isReturned = true
                )
            }

            ball.copy(position = pos, velocity = vel)
        }

        val newScore = s.score + scoreGained
        val filteredBlocks = newBlocks.filter { !it.isDestroyed }

        // End turn only when every ball has been launched AND returned
        val activeBalls = updatedBalls.filter { it.isActive }
        val allActiveBallsReturned = activeBalls.isNotEmpty() && activeBalls.all { it.isReturned }
        val allBallsLaunched = ballsActivated >= totalBallsToLaunch

        if (allActiveBallsReturned && allBallsLaunched) {
            endTurn(filteredBlocks, newScore)
        } else {
            _state.update { it.copy(balls = updatedBalls, blocks = filteredBlocks, score = newScore) }
        }
    }

    private fun endTurn(remainingBlocks: List<Block>, newScore: Int) {
        activationJob?.cancel()
        gameLoopJob?.cancel()
        ballsActivated = 0
        totalBallsToLaunch = 0

        val s = _state.value
        val canvasW = s.canvasSize.width
        val newBallCount = s.ballCount + 1
        val newTurn = s.turn + 1

        val movedBlocks = remainingBlocks.map { it.copy(row = it.row + 1) }
        val blockSize = blockSizeFor(canvasW)
        val maxRow = ((s.canvasSize.height - topBarHeight - bottomBarHeight) /
                      (blockSize.height + blockPadding)).toInt()

        if (movedBlocks.any { it.row >= maxRow }) {
            _state.update { it.copy(blocks = movedBlocks, score = newScore, phase = GamePhase.GameOver) }
            return
        }

        val newRow = generateRow(s.canvasSize, row = 0, currentBallCount = newBallCount)
        val allBlocks = newRow + movedBlocks

        _state.update {
            it.copy(
                balls = emptyList(),
                blocks = allBlocks,
                ballCount = newBallCount,
                score = newScore,
                phase = GamePhase.Idle,
                turn = newTurn
            )
        }
    }

    // ---- Helpers ----

    private fun blockSizeFor(canvasW: Float): Size {
        val totalPad = blockPadding * (cols + 1)
        val w = (canvasW - totalPad) / cols
        return Size(w, w * 0.55f)
    }

    fun blockRect(block: Block, canvasW: Float, blockSize: Size): androidx.compose.ui.geometry.Rect {
        val x = blockPadding + block.col * (blockSize.width + blockPadding)
        val y = topBarHeight + blockPadding + block.row * (blockSize.height + blockPadding)
        return androidx.compose.ui.geometry.Rect(
            left = x, top = y,
            right = x + blockSize.width, bottom = y + blockSize.height
        )
    }

    fun blockSizePublic(canvasW: Float) = blockSizeFor(canvasW)

    private fun generateRow(size: Size, row: Int, currentBallCount: Int): List<Block> {
        return (0 until cols).map { col ->
            val hp = (1..currentBallCount.coerceAtLeast(1)).random().coerceAtLeast(1) +
                     (0..currentBallCount / 2).random()
            Block(id = ++idCounter, hp = hp.coerceIn(1, 30), col = col, row = row)
        }
    }

    private fun circleRectCollide(
        center: Offset, radius: Float,
        rect: androidx.compose.ui.geometry.Rect
    ): Boolean {
        val nearX = center.x.coerceIn(rect.left, rect.right)
        val nearY = center.y.coerceIn(rect.top, rect.bottom)
        val dx = center.x - nearX
        val dy = center.y - nearY
        return dx * dx + dy * dy <= radius * radius
    }

    private fun reflectBall(
        vel: Offset, ballPos: Offset,
        rect: androidx.compose.ui.geometry.Rect
    ): Offset {
        val fromTop = abs(ballPos.y - rect.top)
        val fromBottom = abs(ballPos.y - rect.bottom)
        val fromLeft = abs(ballPos.x - rect.left)
        val fromRight = abs(ballPos.x - rect.right)
        val minDist = minOf(fromTop, fromBottom, fromLeft, fromRight)
        return when (minDist) {
            fromTop, fromBottom -> vel.copy(y = -vel.y)
            else -> vel.copy(x = -vel.x)
        }
    }

    fun restartGame() {
        activationJob?.cancel()
        gameLoopJob?.cancel()
        idCounter = 0
        ballsActivated = 0
        totalBallsToLaunch = 0
        lastKnownSize = Size.Zero
        val size = _state.value.canvasSize
        val origin = Offset(size.width / 2f, size.height - bottomBarHeight)
        _state.value = GameState(
            canvasSize = size,
            ballLaunchOrigin = origin
        )
        if (size != Size.Zero) spawnInitialBlocks(size)
    }
}
