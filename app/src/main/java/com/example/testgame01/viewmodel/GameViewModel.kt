package com.example.testgame01.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import com.example.testgame01.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.*

class GameViewModel : ViewModel() {

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var idCounter = 0
    val ballRadiusPublic = 14f
    private val ballRadius = ballRadiusPublic
    private val cols = 6
    private val blockPadding = 8f
    private val topBarHeight = 120f
    private val bottomBarHeight = 160f

    // Speed in pixels per second — frame-rate independent with delta time
    private val ballSpeed = 420f

    // Stagger between balls in seconds (fixed, does not grow with ball count)
    private val launchStaggerSeconds = 0.07f

    private var lastKnownSize = Size.Zero

    // ---- Canvas init ----

    fun onCanvasReady(size: Size) {
        if (size == lastKnownSize) return
        lastKnownSize = size
        val origin = Offset(size.width / 2f, size.height - bottomBarHeight)
        val wasEmpty = _state.value.blocks.isEmpty()
        _state.update { it.copy(canvasSize = size, ballLaunchOrigin = origin) }
        if (wasEmpty) spawnInitialBlocks(size)
    }

    private fun spawnInitialBlocks(size: Size) {
        _state.update { it.copy(blocks = generateRow(size, 0, 1)) }
    }

    // ---- Aiming ----

    fun onDragStart(position: Offset) {
        if (_state.value.phase != GamePhase.Idle) return
        _state.update { it.copy(phase = GamePhase.Aiming) }
    }

    fun onDrag(position: Offset) {
        if (_state.value.phase != GamePhase.Aiming) return
        val origin = _state.value.ballLaunchOrigin
        val raw = Offset(position.x - origin.x, position.y - origin.y)
        _state.update { it.copy(aimDirection = clampToValidAngle(raw)) }
    }

    fun onDragEnd() {
        val s = _state.value
        if (s.phase != GamePhase.Aiming) return
        if (s.aimDirection == Offset.Zero) {
            _state.update { it.copy(phase = GamePhase.Idle) }
            return
        }
        launchBalls(s.aimDirection)
    }

    private fun clampToValidAngle(dir: Offset): Offset {
        val len = sqrt(dir.x * dir.x + dir.y * dir.y)
        if (len < 15f) return Offset.Zero
        var normX = dir.x / len
        var normY = dir.y / len
        if (normY >= -0.05f) normY = -0.05f
        val minSin = sin(Math.toRadians(10.0)).toFloat()
        if (abs(normY) < minSin) {
            normY = -minSin
            val rem = sqrt(max(0f, 1f - normY * normY))
            normX = if (normX < 0) -rem else rem
        }
        // Store as normalized direction — speed applied in tick() using delta time
        return Offset(normX, normY)
    }

    // ---- Launch ----

    private fun launchBalls(direction: Offset) {
        val s = _state.value
        val balls = (0 until s.ballCount).map { i ->
            Ball(
                id = i,
                position = s.ballLaunchOrigin,
                velocity = direction,          // unit-direction; scaled by ballSpeed * dt in tick()
                isActive = true,
                isReturned = false,
                launchDelaySeconds = i * launchStaggerSeconds
            )
        }
        _state.update {
            it.copy(balls = balls, phase = GamePhase.Shooting, aimDirection = Offset.Zero)
        }
    }

    // ---- Tick (called from withFrameNanos in GameScreen with real delta time) ----

    fun tick(dt: Float) {
        val s = _state.value
        if (s.phase != GamePhase.Shooting) return
        if (s.canvasSize == Size.Zero) return

        val canvasW = s.canvasSize.width
        val blockSize = blockSizeFor(canvasW)
        val mutableBlocks = s.blocks.toMutableList()
        var scoreGained = 0

        val updatedBalls = s.balls.map { ball ->
            // Count down launch delay in real seconds
            if (ball.launchDelaySeconds > 0f) {
                return@map ball.copy(launchDelaySeconds = ball.launchDelaySeconds - dt)
            }
            if (!ball.isActive || ball.isReturned) return@map ball

            var pos = ball.position
            // velocity is a unit vector — scale by speed and dt each frame
            val stepDist = ballSpeed * dt
            var vel = ball.velocity  // unit direction
            val steps = 2

            repeat(steps) { _ ->
                val dx = vel.x * stepDist / steps
                val dy = vel.y * stepDist / steps
                val next = Offset(pos.x + dx, pos.y + dy)

                var nx = next.x
                var ny = next.y
                var vx = vel.x
                var vy = vel.y

                if (nx - ballRadius <= 0f) { vx = abs(vx); nx = ballRadius }
                if (nx + ballRadius >= canvasW) { vx = -abs(vx); nx = canvasW - ballRadius }
                if (ny - ballRadius <= topBarHeight) { vy = abs(vy); ny = topBarHeight + ballRadius }

                vel = Offset(vx, vy)
                pos = Offset(nx, ny)

                // Block collisions
                for (i in mutableBlocks.indices) {
                    val blk = mutableBlocks[i]
                    if (blk.isDestroyed) continue
                    val rect = blockRect(blk, canvasW, blockSize)
                    if (circleRectCollide(pos, ballRadius, rect)) {
                        val newHp = blk.hp - 1
                        scoreGained++
                        mutableBlocks[i] = if (newHp <= 0)
                            blk.copy(hp = 0, isDestroyed = true)
                        else
                            blk.copy(hp = newHp)
                        vel = reflectBall(vel, pos, rect)
                        pos = pushOut(pos, rect)
                        break
                    }
                }
            }

            // Bottom: return ball
            if (pos.y + ballRadius >= s.ballLaunchOrigin.y) {
                return@map ball.copy(
                    position = s.ballLaunchOrigin,
                    velocity = vel,
                    isReturned = true
                )
            }

            ball.copy(position = pos, velocity = vel)
        }

        // Always update blocks so HP changes are reflected immediately
        val visibleBlocks = mutableBlocks.filter { !it.isDestroyed }
        val newScore = s.score + scoreGained

        // End turn when all active (non-delayed) balls have returned
        val activeBalls = updatedBalls.filter { it.isActive && it.launchDelaySeconds <= 0f }
        val allReturned = activeBalls.isNotEmpty() && activeBalls.all { it.isReturned }

        if (allReturned) {
            endTurn(visibleBlocks, newScore)
        } else {
            _state.update { it.copy(balls = updatedBalls, blocks = visibleBlocks, score = newScore) }
        }
    }

    private fun pushOut(pos: Offset, rect: androidx.compose.ui.geometry.Rect): Offset {
        val fromTop    = abs(pos.y - rect.top)
        val fromBottom = abs(pos.y - rect.bottom)
        val fromLeft   = abs(pos.x - rect.left)
        val fromRight  = abs(pos.x - rect.right)
        val minDist = minOf(fromTop, fromBottom, fromLeft, fromRight)
        return when (minDist) {
            fromTop    -> pos.copy(y = rect.top    - ballRadius)
            fromBottom -> pos.copy(y = rect.bottom + ballRadius)
            fromLeft   -> pos.copy(x = rect.left   - ballRadius)
            else       -> pos.copy(x = rect.right  + ballRadius)
        }
    }

    private fun endTurn(remainingBlocks: List<Block>, newScore: Int) {
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

        val newRow = generateRow(s.canvasSize, 0, newBallCount)
        _state.update {
            it.copy(
                balls = emptyList(),
                blocks = newRow + movedBlocks,
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
        return androidx.compose.ui.geometry.Rect(x, y, x + blockSize.width, y + blockSize.height)
    }

    fun blockSizePublic(canvasW: Float) = blockSizeFor(canvasW)

    private fun generateRow(size: Size, row: Int, currentBallCount: Int): List<Block> {
        return (0 until cols).map { col ->
            val hp = ((1..currentBallCount.coerceAtLeast(1)).random() +
                    (0..currentBallCount / 2).random()).coerceIn(1, 30)
            Block(id = ++idCounter, hp = hp, col = col, row = row)
        }
    }

    private fun circleRectCollide(center: Offset, radius: Float, rect: androidx.compose.ui.geometry.Rect): Boolean {
        val nearX = center.x.coerceIn(rect.left, rect.right)
        val nearY = center.y.coerceIn(rect.top, rect.bottom)
        val dx = center.x - nearX
        val dy = center.y - nearY
        return dx * dx + dy * dy <= radius * radius
    }

    private fun reflectBall(vel: Offset, ballPos: Offset, rect: androidx.compose.ui.geometry.Rect): Offset {
        val fromTop    = abs(ballPos.y - rect.top)
        val fromBottom = abs(ballPos.y - rect.bottom)
        val fromLeft   = abs(ballPos.x - rect.left)
        val fromRight  = abs(ballPos.x - rect.right)
        val minDist = minOf(fromTop, fromBottom, fromLeft, fromRight)
        return when (minDist) {
            fromTop, fromBottom -> vel.copy(y = -vel.y)
            else                -> vel.copy(x = -vel.x)
        }
    }

    fun restartGame() {
        idCounter = 0
        lastKnownSize = Size.Zero
        val size = _state.value.canvasSize
        val origin = Offset(size.width / 2f, size.height - bottomBarHeight)
        _state.value = GameState(canvasSize = size, ballLaunchOrigin = origin)
        if (size != Size.Zero) spawnInitialBlocks(size)
    }
}
