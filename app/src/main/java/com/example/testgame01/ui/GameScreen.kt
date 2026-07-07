package com.example.testgame01.ui

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sqrt

private val BackgroundColor = Color(0xFF1A1A2E)
private val BallColor       = Color(0xFFE0E0E0)
private val AimLineColor    = Color(0x99FFFFFF)
private val TopBarBg        = Color(0xFF16213E)
private val BottomBarBg     = Color(0xFF16213E)

@Composable
fun GameScreen(viewModel: GameViewModel = viewModel()) {
    val state   by viewModel.state.collectAsState()
    val measurer = rememberTextMeasurer()
    val density  = LocalDensity.current

    var topBarPx    by remember { mutableStateOf(0f) }
    var bottomBarPx by remember { mutableStateOf(0f) }
    var canvasSizePx by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(canvasSizePx, topBarPx, bottomBarPx) {
        if (canvasSizePx != Size.Zero && topBarPx > 0f && bottomBarPx > 0f) {
            viewModel.onCanvasReady(canvasSizePx, topBarPx, bottomBarPx)
        }
    }

    // یک loop ثابت — همیشه در حال اجراست و هر 16ms یه tick میزنه
    LaunchedEffect(Unit) {
        while (isActive) {
            viewModel.tick()
            delay(16L)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val ph = viewModel.state.value.phase
                            if (ph == GamePhase.Idle || ph == GamePhase.Aiming)
                                viewModel.onDragStart(offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (viewModel.state.value.phase == GamePhase.Aiming)
                                viewModel.onDrag(change.position)
                        },
                        onDragEnd = {
                            if (viewModel.state.value.phase == GamePhase.Aiming)
                                viewModel.onDragEnd()
                        },
                        onDragCancel = {
                            if (viewModel.state.value.phase == GamePhase.Aiming)
                                viewModel.onDragEnd()
                        }
                    )
                }
                .onSizeChanged { intSize ->
                    canvasSizePx = Size(intSize.width.toFloat(), intSize.height.toFloat())
                }
        ) {
            val canvasW   = size.width
            val blockSize = viewModel.blockSizePublic(canvasW)

            drawRect(BackgroundColor)

            // Blocks
            state.blocks.forEach { block ->
                val rect = viewModel.blockRect(block, canvasW, blockSize)
                drawBlock(block.color, rect, block.hp.toString(), measurer)
            }

            // Aim line
            if (state.phase == GamePhase.Aiming && state.aimDirection != Offset.Zero) {
                drawAimLine(state.ballLaunchOrigin, state.aimDirection)
            }

            // توپ‌ها در حین شلیک — همه رو نشون بده به جز returned
            if (state.phase == GamePhase.Shooting) {
                state.balls.forEach { ball ->
                    if (!ball.isReturned) {
                        drawBall(ball.position, viewModel.ballRadiusPublic)
                    }
                }
            }

            // توپ ثابت هنگام Idle یا Aiming
            if (state.ballLaunchOrigin != Offset.Zero &&
                (state.phase == GamePhase.Idle || state.phase == GamePhase.Aiming)) {
                drawBall(state.ballLaunchOrigin, viewModel.ballRadiusPublic)
            }
        }

        TopHud(
            score     = state.score,
            ballCount = state.ballCount,
            turn      = state.turn,
            modifier  = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { intSize ->
                    val px = intSize.height.toFloat()
                    if (px != topBarPx) topBarPx = px
                }
        )

        BottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { intSize ->
                    val px = intSize.height.toFloat()
                    if (px != bottomBarPx) bottomBarPx = px
                }
        )

        if (state.phase == GamePhase.GameOver) {
            GameOverScreen(score = state.score, onRestart = { viewModel.restartGame() })
        }
    }
}

// ---- Draw helpers ----

fun DrawScope.drawBall(center: Offset, radius: Float) {
    drawCircle(color = BallColor, radius = radius, center = center)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x55FFFFFF), Color.Transparent),
            center = center, radius = radius * 2f
        ),
        radius = radius * 2f, center = center
    )
}

fun DrawScope.drawBlock(
    color: Color, rect: Rect, label: String,
    measurer: androidx.compose.ui.text.TextMeasurer
) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(color, color.copy(alpha = 0.7f)),
            startY = rect.top, endY = rect.bottom
        ),
        topLeft = Offset(rect.left, rect.top),
        size    = Size(rect.width, rect.height)
    )
    drawRect(
        color   = Color.White.copy(alpha = 0.25f),
        topLeft = Offset(rect.left, rect.top),
        size    = Size(rect.width, rect.height),
        style   = Stroke(width = 1.5f)
    )
    val measured = measurer.measure(
        text  = label,
        style = TextStyle(
            color = Color.White, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
    )
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(
            rect.left + (rect.width  - measured.size.width)  / 2f,
            rect.top  + (rect.height - measured.size.height) / 2f
        )
    )
}

fun DrawScope.drawAimLine(origin: Offset, direction: Offset) {
    val len = sqrt(direction.x * direction.x + direction.y * direction.y)
    if (len == 0f) return
    val normX = direction.x / len
    val normY = direction.y / len
    val dashLength = 14f; val gapLength = 8f
    var traveled = 0f; var current = origin
    val maxTravel = 350f
    while (traveled < maxTravel) {
        val endX = current.x + normX * dashLength
        val endY = current.y + normY * dashLength
        val a    = (1f - traveled / maxTravel).coerceIn(0f, 1f)
        drawLine(
            color = AimLineColor.copy(alpha = a),
            start = current, end = Offset(endX, endY),
            strokeWidth = 3f, cap = StrokeCap.Round
        )
        current  = Offset(endX + normX * gapLength, endY + normY * gapLength)
        traveled += dashLength + gapLength
    }
}

// ---- Composable UI pieces ----

@Composable
fun TopHud(score: Int, ballCount: Int, turn: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(TopBarBg)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        HudItem(label = "SCORE", value = score.toString())
        HudItem(label = "TURN",  value = turn.toString())
        HudItem(label = "BALLS", value = "\u25CF x$ballCount")
    }
}

@Composable
fun BottomBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(BottomBarBg.copy(alpha = 0.6f))
            .padding(vertical = 28.dp)
    )
}

@Composable
fun HudItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color(0xFF9E9E9E), fontSize = 10.sp,
             fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
        Text(text = value, color = Color.White, fontSize = 18.sp,
             fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GameOverScreen(score: Int, onRestart: () -> Unit) {
    Box(
        modifier         = Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("GAME OVER", color = Color(0xFFF44336), fontSize = 42.sp,
                 fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
            Text("Score: $score", color = Color.White, fontSize = 28.sp,
                 fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick  = onRestart,
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth(0.6f).height(52.dp)
            ) {
                Text("PLAY AGAIN", fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }
    }
}
