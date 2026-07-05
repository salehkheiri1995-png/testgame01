package com.example.testgame01

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GameScreen() }
    }
}

@Composable
fun GameScreen(viewModel: GameViewModel = viewModel()) {
    val measurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel.isBallMoving) {
        while (viewModel.isBallMoving) {
            withFrameNanos { viewModel.update() }
        }
    }

    Canvas(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF121212))
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { viewModel.onDrag(it, it) },
                onDrag = { change, _ -> viewModel.onDrag(viewModel.dragStart, change.position) },
                onDragEnd = { scope.launch { viewModel.shoot() } }
            )
        }
    ) {
        viewModel.canvasSize = size
        viewModel.blocks.forEach { b ->
            drawRect(b.color, b.position, b.size)
            drawText(measurer.measure(b.value.toString(), TextStyle(Color.White, 20.sp)), b.position + Offset(20f, 20f))
        }
        drawCircle(Color.White, viewModel.ballRadius, viewModel.ballPosition)
    }
}
