package com.example.testgame01.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

data class Ball(
    val id: Int,
    val position: Offset,
    val velocity: Offset,
    val isMoving: Boolean = false,
    val isReturned: Boolean = false
)

data class Block(
    val id: String,
    val hp: Int,
    val maxHp: Int,
    val col: Int,
    val row: Int,
    val isDestroyed: Boolean = false
)

/** یک تکه‌ی کوچک از بلوکی که شکسته — با سرعت و چرخش خودش پخش می‌شه و محو می‌شه. */
data class BlockFragment(
    val cx: Float,
    val cy: Float,
    val vx: Float,
    val vy: Float,
    val width: Float,
    val height: Float,
    val rotationDeg: Float,
    val rotationSpeed: Float,
    val color: Color
)

/**
 * وقتی یک بلوک نابود می‌شه، بلافاصله از لیست blocks حذف می‌شه ولی چندتا تکه‌ی
 * کوچیک ازش این‌جا نگه داشته می‌شه تا انیمیشن «تیکه‌تیکه شدن» پخش بشه.
 */
data class ExplodingBlock(
    val id: String,
    val fragments: List<BlockFragment>,
    val progress: Float = 0f // 0 = لحظه شکستن، 1 = انیمیشن تمام شده
)

/** بلوک رو به چند تکه تقسیم می‌کنه و به هر کدوم یه سرعت پخش‌شونده از مرکز می‌ده. */
fun createShatterFragments(rect: Rect, color: Color): List<BlockFragment> {
    val gridCols = 3
    val gridRows = 3
    val fw = rect.width  / gridCols
    val fh = rect.height / gridRows
    val centerX = rect.left + rect.width  / 2f
    val centerY = rect.top  + rect.height / 2f
    val fragments = mutableListOf<BlockFragment>()
    for (row in 0 until gridRows) {
        for (col in 0 until gridCols) {
            val fx = rect.left + col * fw + fw / 2f
            val fy = rect.top  + row * fh + fh / 2f
            val dx = fx - centerX
            val dy = fy - centerY
            val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val speed = 5f + Random.nextFloat() * 5f
            fragments += BlockFragment(
                cx = fx, cy = fy,
                vx = (dx / dist) * speed,
                vy = (dy / dist) * speed - 3f, // یه کم به سمت بالا پرت بشن، بعد با گرانش برگردن پایین
                width  = fw * 0.8f,
                height = fh * 0.8f,
                rotationDeg = 0f,
                rotationSpeed = Random.nextFloat() * 20f - 10f,
                color = color
            )
        }
    }
    return fragments
}

enum class GamePhase { Idle, Aiming, Shooting, GameOver }

/**
 * رنگ بلوک بر اساس مقدار مطلق hp تعیین می‌شه.
 * hp = 1 (کمترین، نزدیک‌ترین به شکستن) → همیشه سبز ثابت.
 * برای hp های بالاتر، رنگ‌ها با «زاویه طلایی» (golden angle) روی چرخه‌ی hue تولید
 * می‌شن، پس عملاً برای هر عدد hp یک رنگ متمایز و همیشگی داریم (بدون محدودیت تعداد).
 */
fun blockColorForHp(hp: Int, maxHp: Int): Color {
    if (hp <= 1) return Color(0xFF2ECC71) // سبز: کمترین hp
    val hue = (((hp - 2) * 137.508f) % 360f + 360f) % 360f
    return hsvColor(hue = hue, saturation = 0.62f, value = 0.92f)
}

private fun hsvColor(hue: Float, saturation: Float, value: Float): Color {
    val c = value * saturation
    val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
    val m = value - c
    val (r1, g1, b1) = when {
        hue < 60f  -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else       -> Triple(c, 0f, x)
    }
    return Color(r1 + m, g1 + m, b1 + m)
}