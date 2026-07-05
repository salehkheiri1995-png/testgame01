package com.example.testgame01

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.*
import kotlin.random.Random

// ─────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────

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
    var destroyAnimProgress: Float = 0f  // 0f = alive, 1f = fully faded
) {
    val maxHp: Int = hp

    fun color(): Color {
        val ratio = hp.toFloat() / maxHp.toFloat().coerceAtLeast(1f)
        return when {
            ratio > 0.66f -> Color(0xFF4CAF50)   // green
            ratio > 0.33f -> Color(0xFFFFC107)   // yellow
            else          -> Color(0xFFF44336)   // red
        }
    }
}

enum class GamePhase { IDLE, AIMING, SHOOTING, ROUND_END, GAME_OVER }

data class GameState(
    val balls: List<Ball> = emptyList(),
    val blocks: List<Block> = emptyList(),
    val phase: GamePhase = GamePhase.IDLE,
    val ballCount: Int = 1,
    val score: Int = 0,
    val round: Int = 1,
    val aimAngleDeg: Float = 90f,       // 10–170
    val isDragging: Boolean = false,
    val ballStartX: Float = 0f,
    val ballStartY: Float = 0f,
    val canvasWidth: Float = 0f,
    val canvasHeight: Float = 0f,
    val cols: Int = 6,
    val rows: Int = 8                  // number of block rows visible at once
)

// ─────────────────────────────────────────────
// Pure Engine Functions (no Android deps)
// ─────────────────────────────────────────────

object GameEngine {

    const val BALL_RADIUS = 22f
    const val BALL_SPEED  = 22f
    const val BLOCK_H_PAD = 6f
    const val BLOCK_V_PAD = 6f
    const val TOP_BAR_H   = 120f   // header height for score/balls HUD
    const val BOTTOM_BAR_H = 140f  // bottom zone for ball start

    fun blockSize(canvasWidth: Float, cols: Int): Size {
        val cellW = (canvasWidth - BLOCK_H_PAD * (cols + 1)) / cols
        return Size(cellW, cellW * 0.55f)  // aspect ratio
    }

    fun blockRect(block: Block, size: Size, canvasWidth: Float): Rect {
        val cellW = size.width + BLOCK_H_PAD
        val cellH = size.height + BLOCK_V_PAD
        val x = BLOCK_H_PAD + block.col * cellW
        val y = TOP_BAR_H + block.row * (cellH)
        return Rect(Offset(x, y), size)
    }

    // Clamp angle so ball always goes upward (10° – 170°)
    fun clampAngleDeg(rawDeg: Float): Float = rawDeg.coerceIn(10f, 170f)

    fun angleDegFromDrag(ballStart: Offset, dragPoint: Offset): Float {
        val dx = dragPoint.x - ballStart.x
        val dy = dragPoint.y - ballStart.y   // positive = downward on screen
        // We want angle measured from positive-x axis going counter-clockwise
        // but visually, upward is negative-y, so invert dy
        val rawDeg = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())).toFloat()
        val normalized = ((rawDeg % 360) + 360) % 360  // 0–360
        return clampAngleDeg(normalized)
    }

    fun velocityFromAngleDeg(angleDeg: Float): Offset {
        val rad = Math.toRadians(angleDeg.toDouble())
        return Offset(
            (cos(rad) * BALL_SPEED).toFloat(),
            (-sin(rad) * BALL_SPEED).toFloat()   // negative = upward
        )
    }

    fun createInitialBalls(count: Int, startX: Float, startY: Float): List<Ball> =
        (0 until count).map { Ball(id = it, position = Offset(startX, startY), velocity = Offset.Zero) }

    fun generateBlockRow(row: Int, cols: Int, round: Int): List<Block> =
        (0 until cols).map { col ->
            Block(
                id = Random.nextInt(Int.MAX_VALUE),
                hp = Random.nextInt(1, round * 2 + 3),
                col = col,
                row = row
            )
        }

    fun generateInitialBlocks(cols: Int, rows: Int, round: Int): List<Block> {
        val result = mutableListOf<Block>()
        for (r in 0 until rows) {
            result.addAll(generateBlockRow(r, cols, round))
        }
        return result
    }

    /**
     * Move blocks down by one row; add a new row at the top (row = 0).
     * Returns null if any block has reached the bottom danger zone.
     */
    fun advanceBlocksAndCheck(
        blocks: List<Block>,
        cols: Int,
        round: Int,
        maxSafeRow: Int
    ): Pair<List<Block>, Boolean> {
        val moved = blocks.map { it.copy(row = it.row + 1) }
        val isGameOver = moved.any { it.row >= maxSafeRow }
        val newRow = generateBlockRow(0, cols, round)
        return Pair(moved + newRow, isGameOver)
    }

    /**
     * Single physics tick for one ball.
     * Returns updated ball + how many hp points were deducted (for score).
     */
    fun tickBall(
        ball: Ball,
        blocks: MutableList<Block>,
        canvasWidth: Float,
        canvasHeight: Float,
        blockSize: Size,
        cols: Int,
        ballStartY: Float
    ): Pair<Ball, Int> {
        if (!ball.isActive || ball.isReturned) return Pair(ball, 0)

        var pos = ball.position
        var vel = ball.velocity
        var scoreGained = 0

        val nextX = pos.x + vel.x
        val nextY = pos.y + vel.y

        // Wall collisions
        val newVelX = if (nextX - BALL_RADIUS < 0f || nextX + BALL_RADIUS > canvasWidth) -vel.x else vel.x
        val newVelY = if (nextY - BALL_RADIUS < 0f) -vel.y else vel.y
        vel = Offset(newVelX, newVelY)

        pos = Offset(
            (pos.x + vel.x).coerceIn(BALL_RADIUS, canvasWidth - BALL_RADIUS),
            pos.y + vel.y
        )

        // Block collision (bounding box)
        for (i in blocks.indices) {
            val block = blocks[i]
            if (block.isDestroyed) continue
            val rect = blockRect(block, blockSize, canvasWidth).inflate(BALL_RADIUS * 0.5f)
            if (rect.contains(pos)) {
                vel = vel.copy(y = -vel.y)
                val newHp = block.hp - 1
                scoreGained++
                blocks[i] = block.copy(hp = newHp, isDestroyed = newHp <= 0)
                break  // one block per tick
            }
        }

        // Bottom: ball returned
        val returned = pos.y + BALL_RADIUS >= ballStartY
        if (returned) {
            return Pair(ball.copy(position = Offset(pos.x, ballStartY - BALL_RADIUS), isReturned = true), scoreGained)
        }

        return Pair(ball.copy(position = pos, velocity = vel), scoreGained)
    }
}
