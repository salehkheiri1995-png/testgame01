package com.example.testgame01.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.testgame01.model.GamePhase
import com.example.testgame01.viewmodel.GameViewModel
import kotlin.math.sqrt

private val BackgroundColor = Color(0xFF1A1A2E)
private val BallColor = Color(0xFFE0E0E0)
private val AimLineColor = Color(0x99FFFFFF)
private val TopBarBg = Color(0xFF16213E)

@Composable
fun GameScreen(viewModel: GameViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val measurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.phase) {
                    detectDragGestures(
                        onDragStart = { viewModel.onDragStart(it) },
                        onDrag = { change, _ -> viewModel.onDrag(change.position) },
                        onDragEnd = { viewModel.onDragEnd() }
                    )
                }
        ) {
            // Notify ViewModel of canvas size
            viewModel.onCanvasReady(this.size)

            val canvasW = this.size.width
            val blockSize = viewModel.blockSizePublic(canvasW)

            // Background
            drawRect(BackgroundColor)

            // Draw blocks
            state.blocks.forEach { block ->
                val rect = viewModel.blockRect(block, canvasW, blockSize)
                drawBlock(block.color, rect, block.hp.toString(), measurer, block.alpha)
            }

            // Draw aim line
            if (state.phase == GamePhase.Aiming && state.aimDirection != Offset.Zero) {
                drawAimLine(state.ballLaunchOrigin, state.aimDirection, this.size)
            }

            // Draw balls
            state.balls.forEach { ball ->
                if (ball.isActive && !ball.isReturned) {
                    drawCircle(
                        color = BallColor,
                        radius = viewModel.ballRadiusPublic,
                        center = ball.position
                    )
                    // Glow effect
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x44FFFFFF), Color.Transparent),
                            center = ball.position,
                            radius = viewModel.ballRadiusPublic * 2.5f
                        ),
                        radius = viewModel.ballRadiusPublic * 2.5f,
                        center = ball.position
                    )
                }
            }

            // Draw idle ball at origin
            if (state.phase == GamePhase.Idle || state.phase == GamePhase.Aiming) {
                drawCircle(BallColor, viewModel.ballRadiusPublic, state.ballLaunchOrigin)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x44FFFFFF), Color.Transparent),
                        center = state.ballLaunchOrigin,
                        radius = viewModel.ballRadiusPublic * 2.5f
                    ),
                    radius = viewModel.ballRadiusPublic * 2.5f,
                    center = state.ballLaunchOrigin
                )
            }
        }

        // Top HUD
        TopHud(
            score = state.score,
            ballCount = state.ballCount,
            turn = state.turn
        )

        // Game Over overlay
        if (state.phase == GamePhase.GameOver) {
            GameOverScreen(
                score = state.score,
                onRestart = { viewModel.restartGame() }
            )
        }
    }
}

fun DrawScope.drawBlock(
    color: Color,
    rect: Rect,
    label: String,
    measurer: androidx.compose.ui.text.TextMeasurer,
    alpha: Float = 1f
) {
    // Block fill with gradient
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.7f)),
            startY = rect.top,
            endY = rect.bottom
        ),
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        alpha = alpha
    )
    // Border
    drawRect(
        color = Color.White.copy(alpha = 0.25f * alpha),
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        style = Stroke(width = 1.5f),
        alpha = alpha
    )
    // HP text
    val measured = measurer.measure(
        text = label,
        style = TextStyle(
            color = Color.White.copy(alpha = alpha),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    )
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(
            rect.left + (rect.width - measured.size.width) / 2f,
            rect.top + (rect.height - measured.size.height) / 2f
        ),
        alpha = alpha
    )
}

fun DrawScope.drawAimLine(origin: Offset, direction: Offset, canvasSize: Size) {
    val len = sqrt(direction.x * direction.x + direction.y * direction.y)
    val normX = direction.x / len
    val normY = direction.y / len

    val dashLength = 14f
    val gapLength = 8f
    var traveled = 0f
    var current = origin
    val maxTravel = 350f

    while (traveled < maxTravel) {
        val endX = current.x + normX * dashLength
        val endY = current.y + normY * dashLength
        val alpha = (1f - traveled / maxTravel).coerceIn(0f, 1f)
        drawLine(
            color = AimLineColor.copy(alpha = alpha),
            start = current,
            end = Offset(endX, endY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        current = Offset(endX + normX * gapLength, endY + normY * gapLength)
        traveled += dashLength + gapLength
    }
}

@Composable
fun TopHud(score: Int, ballCount: Int, turn: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TopBarBg)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HudItem(label = "SCORE", value = score.toString())
        HudItem(label = "TURN", value = turn.toString())
        HudItem(label = "BALLS", value = "● x$ballCount")
    }
}

@Composable
fun HudItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color(0xFF9E9E9E),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.5.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun GameOverScreen(score: Int, onRestart: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "GAME OVER",
                color = Color(0xFFF44336),
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
            )
            Text(
                text = "Score: $score",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth(0.6f)
                    .height(52.dp)
            ) {
                Text(
                    "PLAY AGAIN",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}
