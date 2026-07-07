package com.example.testgame01.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import com.example.testgame01.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
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
    private val ballSpeed = 25f          // pixels per tick (same as Ballz)
    private val launchDelayTicks = 8    // ticks between each ball launch (same as Ballz)

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
        // Clamp so ball always goes upward
        if (normY >= -0.05f) normY = -0.05f
        val minSin = sin(Math.toRadians(10.0)).toFloat()
        if (abs(normY) < minSin) {
            normY = -minSin
            val rem = sqrt(max(0f, 1f - normY * normY))
            normX = if (normX < 0) -rem else rem
        }
        // Return as velocity vector (pre-scaled by speed, same as Ballz onDragEnd)
        return Offset(normX * ballSpeed, normY * ballSpeed)
    }

    // ---- Launch (same pattern as Ballz) ----

    private fun launchBalls(velocity: Offset) {
        val s = _state.value
        // Prepare all balls at origin, none moving yet
        val balls = (0 until s.ballCount).map { i ->
            Ball(
                id = i,
                position = s.ballLaunchOrigin,
                velocity = velocity,
                isMoving = false,
                isReturned = false
            )
        }.toMutableList()
        // Launch first ball immediately (same as Ballz)
        if (balls.isNotEmpty()) {
            balls[0] = balls[0].copy(isMoving = true)
        }
        _state.update {
            it.copy(
                balls = balls,
                phase = GamePhase.Shooting,
                aimDirection = Offset.Zero,
                ballsToLaunchLeft = s.ballCount - 1,
                launchDelayCounter = 0
            )
        }
    }

    // ---- Tick: called every 16ms from delay(16L) in GameScreen ----

    fun tick() {
        val s = _state.value
        if (s.phase != GamePhase.Shooting) return
        if (s.canvasSize == Size.Zero) return

        val canvasW = s.canvasSize.width
        val blockSize = blockSizeFor(canvasW)
        val mutableBlocks = s.blocks.toMutableList()
        var scoreGained = 0

        var nextBallsToLaunchLeft = s.ballsToLaunchLeft
        var nextLaunchDelayCounter = s.launchDelayCounter

        // 1. Stagger launch: activate next ball after delay ticks (same as Ballz)
        val updatedBalls = s.balls.mapIndexed { index, ball ->
            val ballToLaunchIndex = s.ballCount - nextBallsToLaunchLeft
            if (index == ballToLaunchIndex && nextBallsToLaunchLeft > 0) {
                if (nextLaunchDelayCounter >= launchDelayTicks) {
                    ball.copy(isMoving = true)
                } else {
                    ball
                }
            } else {
                ball
            }
        }.toMutableList()

        if (nextBallsToLaunchLeft > 0) {
            if (nextLaunchDelayCounter >= launchDelayTicks) {
                nextLaunchDelayCounter = 0
                nextBallsToLaunchLeft--
            } else {
                nextLaunchDelayCounter++
            }
        }

        // 2. Physics for each moving ball
        val hitBlockHpReductions = mutableMapOf<String, Int>()

        for (i in updatedBalls.indices) {
            val ball = updatedBalls[i]
            if (!ball.isMoving || ball.isReturned) continue

            var pos = ball.position + ball.velocity
            var vel = ball.velocity

            // Wall collisions
            if (pos.x - ballRadius <= 0f) {
                vel = vel.copy(x = abs(vel.x))
                pos = pos.copy(x = ballRadius)
            } else if (pos.x + ballRadius >= canvasW) {
                vel = vel.copy(x = -abs(vel.x))
                pos = pos.copy(x = canvasW - ballRadius)
            }
            if (pos.y - ballRadius <= topBarHeight) {
                vel = vel.copy(y = abs(vel.y))
                pos = pos.copy(y = topBarHeight + ballRadius)
            }

            // Bottom: return ball
            if (pos.y + ballRadius >= s.ballLaunchOrigin.y) {
                updatedBalls[i] = ball.copy(
                    position = s.ballLaunchOrigin,
                    velocity = vel,
                    isMoving = false,
                    isReturned = true
                )
                continue
            }

            // Block collisions
            for (blk in mutableBlocks) {
                if (blk.isDestroyed) continue
                val rect = blockRect(blk, canvasW, blockSize)

                val closestX = pos.x.coerceIn(rect.left, rect.right)
                val closestY = pos.y.coerceIn(rect.top, rect.bottom)
                val dx = pos.x - closestX
                val dy = pos.y - closestY
                if (dx * dx + dy * dy < ballRadius * ballRadius) {
                    val currentReductions = hitBlockHpReductions.getOrDefault(blk.id, 0)
                    hitBlockHpReductions[blk.id] = currentReductions + 1
                    scoreGained++

                    // Reflection (same logic as Ballz)
                    val overlapX = ballRadius - abs(dx)
                    val overlapY = ballRadius - abs(dy)
                    vel = if (closestX == rect.left || closestX == rect.right) {
                        if (closestY == rect.top || closestY == rect.bottom) {
                            if (overlapX < overlapY) vel.copy(x = -vel.x)
                            else vel.copy(y = -vel.y)
                        } else vel.copy(x = -vel.x)
                    } else {
                        vel.copy(y = -vel.y)
                    }
                    break
                }
            }

            updatedBalls[i] = ball.copy(position = pos, velocity = vel)
        }

        // 3. Apply HP reductions to blocks
        val updatedBlocks = mutableBlocks.mapNotNull { blk ->
            val reduction = hitBlockHpReductions.getOrDefault(blk.id, 0)
            if (reduction > 0) {
                val newHp = blk.hp - reduction
                if (newHp <= 0) null else blk.copy(hp = newHp)
            } else blk
        }

        val newScore = s.score + scoreGained

        // 4. End turn when all balls returned
        val allReturned = updatedBalls.all { it.isReturned }
        if (allReturned) {
            endTurn(updatedBlocks, newScore)
        } else {
            _state.update {
                it.copy(
                    balls = updatedBalls,
                    blocks = updatedBlocks,
                    score = newScore,
                    ballsToLaunchLeft = nextBallsToLaunchLeft,
                    launchDelayCounter = nextLaunchDelayCounter
                )
            }
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
                turn = newTurn,
                ballsToLaunchLeft = 0,
                launchDelayCounter = 0
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
        val java = java.util.Random()
        val result = mutableListOf<Block>()
        var spawnedAny = false
        for (col in 0 until cols) {
            if (java.nextFloat() < 0.7f) {
                val hp = (1..currentBallCount.coerceAtLeast(1)).random()
                result.add(Block(id = UUID.randomUUID().toString(), hp = hp, maxHp = hp, col = col, row = row))
                spawnedAny = true
            }
        }
        if (!spawnedAny) {
            val col = java.nextInt(cols)
            result.add(Block(id = UUID.randomUUID().toString(), hp = 1, maxHp = 1, col = col, row = row))
        }
        return result
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
