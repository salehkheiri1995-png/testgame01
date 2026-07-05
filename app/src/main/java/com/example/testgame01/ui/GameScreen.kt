package com.example.testgame01.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.testgame01.*
import kotlin.math.*

@Composable
fun GameScreen(vm: GameViewModel = viewModel()) {
    val state = vm.gameState

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E))) {

        // ── Main Canvas ─────────────────────────────────────────────
        GameCanvas(
            state = state,
            onCanvasSize = { w, h -> vm.onCanvasSizeChanged(w, h) },
            onDragStart = { vm.onDragStart(it) },
            onDrag = { vm.onDrag(it) },
            onDragEnd = { vm.onDragEnd() }
        )

        // ── HUD Overlay ─────────────────────────────────────────────
        HudOverlay(state = state)

        // ── Game Over Screen ─────────────────────────────────────────
        if (state.phase == GamePhase.GAME_OVER) {
            GameOverOverlay(score = state.score, round = state.round, onRestart = { vm.restart() })
        }
    }
}

// ─── Game Canvas ──────────────────────────────────────────────────────────────

@Composable
private fun GameCanvas(
    state: GameState,
    onCanvasSize: (Float, Float) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart(it) },
                    onDrag = { change, _ -> onDrag(change.position) },
                    onDragEnd = { onDragEnd() }
                )
            }
    ) {
        onCanvasSize(size.width, size.height)
        drawBackground()
        drawBlocks(state, measurer)
        drawAimLine(state)
        drawBalls(state)
        drawBallBase(state)
    }
}

// ─── Draw Helpers ─────────────────────────────────────────────────────────────

private fun DrawScope.drawBackground() {
    drawRect(Color(0xFF1A1A2E), size = size)
}

private fun DrawScope.drawBlocks(state: GameState, measurer: TextMeasurer) {
    val blockSize = GameEngine.blockSize(size.width, state.cols)
    state.blocks.forEach { block ->
        val rect = GameEngine.blockRect(block, blockSize, size.width)
        val alpha = if (block.isDestroyed) 1f - block.destroyAnimProgress else 1f
        val scale = if (block.isDestroyed) 1f - block.destroyAnimProgress * 0.5f else 1f

        if (alpha <= 0f) return@forEach

        val cx = rect.left + rect.width / 2
        val cy = rect.top + rect.height / 2
        val scaledW = rect.width * scale
        val scaledH = rect.height * scale
        val scaledRect = Rect(Offset(cx - scaledW / 2, cy - scaledH / 2), Size(scaledW, scaledH))

        // Block background
        drawRoundRect(
            color = block.color().copy(alpha = alpha),
            topLeft = scaledRect.topLeft,
            size = scaledRect.size,
            cornerRadius = CornerRadius(8f, 8f)
        )
        // Block border
        drawRoundRect(
            color = Color.White.copy(alpha = alpha * 0.3f),
            topLeft = scaledRect.topLeft,
            size = scaledRect.size,
            cornerRadius = CornerRadius(8f, 8f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        )

        // HP text
        if (!block.isDestroyed) {
            val text = measurer.measure(
                block.hp.toString(),
                TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
            drawText(
                text,
                topLeft = Offset(
                    cx - text.size.width / 2f,
                    cy - text.size.height / 2f
                ),
                alpha = alpha
            )
        }
    }
}

private fun DrawScope.drawAimLine(state: GameState) {
    if (!state.isDragging || state.phase != GamePhase.IDLE) return

    val startPos = Offset(state.ballStartX, state.ballStartY - GameEngine.BALL_RADIUS)
    val angle = state.aimAngleDeg
    val rad = Math.toRadians(angle.toDouble())
    val length = size.height * 0.55f
    val endPos = Offset(
        startPos.x + (cos(rad) * length).toFloat(),
        startPos.y - (sin(rad) * length).toFloat()
    )

    // Dotted line
    val dotSpacing = 22f
    val totalLen = length
    val steps = (totalLen / dotSpacing).toInt()
    val dx = (endPos.x - startPos.x) / steps
    val dy = (endPos.y - startPos.y) / steps

    for (i in 0 until steps) {
        if (i % 2 == 0) {
            val x = startPos.x + dx * i
            val y = startPos.y + dy * i
            drawCircle(
                color = Color.White.copy(alpha = 0.7f - i.toFloat() / steps * 0.5f),
                radius = 5f,
                center = Offset(x, y)
            )
        }
    }
}

private fun DrawScope.drawBalls(state: GameState) {
    state.balls.forEach { ball ->
        if (!ball.isActive || ball.isReturned) return@forEach
        // Glow effect
        drawCircle(
            color = Color(0x44FFFFFF),
            radius = GameEngine.BALL_RADIUS * 1.8f,
            center = ball.position
        )
        drawCircle(
            color = Color.White,
            radius = GameEngine.BALL_RADIUS,
            center = ball.position
        )
    }
}

private fun DrawScope.drawBallBase(state: GameState) {
    // Bottom line indicator
    val lineY = state.ballStartY + GameEngine.BALL_RADIUS
    drawLine(
        color = Color.White.copy(alpha = 0.15f),
        start = Offset(0f, lineY),
        end = Offset(size.width, lineY),
        strokeWidth = 2f
    )

    // Ball waiting at base (shown when idle)
    if (state.phase == GamePhase.IDLE) {
        drawCircle(
            color = Color(0x44FFFFFF),
            radius = GameEngine.BALL_RADIUS * 1.8f,
            center = Offset(state.ballStartX, state.ballStartY - GameEngine.BALL_RADIUS)
        )
        drawCircle(
            color = Color.White,
            radius = GameEngine.BALL_RADIUS,
            center = Offset(state.ballStartX, state.ballStartY - GameEngine.BALL_RADIUS)
        )
    }
}

// ─── HUD ──────────────────────────────────────────────────────────────────────

@Composable
private fun HudOverlay(state: GameState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudCard(label = "امتیاز", value = state.score.toString())
            HudCard(label = "توپ‌ها", value = "x${state.ballCount}")
            HudCard(label = "نوبت", value = state.round.toString())
        }
    }
}

@Composable
private fun HudCard(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

// ─── Game Over ────────────────────────────────────────────────────────────────

@Composable
private fun GameOverOverlay(score: Int, round: Int, onRestart: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    "💀 Game Over",
                    color = Color(0xFFF44336),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(16.dp))
                Text("نوبت $round", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "امتیاز: $score",
                    color = Color(0xFFFFC107),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("بازی مجدد", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
