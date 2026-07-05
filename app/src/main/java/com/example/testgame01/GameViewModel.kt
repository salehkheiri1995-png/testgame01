package com.example.testgame01

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.delay
import kotlin.random.Random

data class Block(val id: Int, var value: Int, var position: Offset, val size: Size) {
    val color: Color get() = if (value < 5) Color(0xFF4CAF50) else if (value < 15) Color(0xFFFFC107) else Color(0xFFF44336)
}

class GameViewModel : ViewModel() {
    var canvasSize by mutableStateOf(Size.Zero)
    var ballPosition by mutableStateOf(Offset(500f, 1500f))
    val ballRadius = 25f
    
    var blocks = mutableStateListOf<Block>()
    var ballCount by mutableStateOf(1)
    var isBallMoving by mutableStateOf(false)
    var velocity by mutableStateOf(Offset.Zero)
    
    var isDragging by mutableStateOf(false)
    var dragStart by mutableStateOf(Offset.Zero)
    var dragCurrent by mutableStateOf(Offset.Zero)

    init { generateRow() }

    private fun generateRow() {
        val blockWidth = 140f
        val padding = 20f
        for (i in 0 until 6) {
            blocks.add(Block(Random.nextInt(), Random.nextInt(1, 10) + ballCount, Offset(i * (blockWidth + padding) + 20f, 100f), Size(blockWidth, 100f)))
        }
    }

    fun onDrag(start: Offset, current: Offset) {
        if (!isBallMoving) { isDragging = true; dragStart = start; dragCurrent = current }
    }

    suspend fun shoot() {
        isDragging = false
        isBallMoving = true
        val dx = dragStart.x - dragCurrent.x
        val dy = dragStart.y - dragCurrent.y
        val length = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val speed = 25f
        velocity = Offset((dx / length) * speed, (dy / length) * speed)
        
        // شبیه‌سازی چند توپ
        repeat(ballCount) {
            delay(150)
            // در پیاده‌سازی حرفه‌ای اینجا لیست توپ‌ها را مدیریت می‌کنیم
        }
    }

    fun update() {
        if (!isBallMoving) return
        val nextPos = ballPosition + velocity
        
        // برخورد بلوک
        val iterator = blocks.iterator()
        while(iterator.hasNext()){
            val b = iterator.next()
            if(Rect(b.position, b.size).contains(nextPos)){
                velocity = velocity.copy(y = -velocity.y)
                b.value--
                if(b.value <= 0) iterator.remove()
                return
            }
        }
        
        if (nextPos.x < ballRadius || nextPos.x > canvasSize.width - ballRadius) velocity = velocity.copy(x = -velocity.x)
        if (nextPos.y < ballRadius) velocity = velocity.copy(y = -velocity.y)
        if (nextPos.y > canvasSize.height - ballRadius) {
            isBallMoving = false
            ballPosition = Offset(canvasSize.width / 2, canvasSize.height - 100f)
            ballCount++
            generateRow()
            blocks.forEach { it.position = Offset(it.position.x, it.position.y + 120f) }
        } else ballPosition = nextPos
    }
}
