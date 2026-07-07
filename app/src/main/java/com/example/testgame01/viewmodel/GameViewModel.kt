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

    val ballRadiusPublic = 14f
    private val ballRadius = ballRadiusPublic
    private val cols = 6
    private val blockPadding = 8f
    private val ballSpeed = 22f   // سرعت بالاتر؛ با subSteps=4 هنوز collision miss نمی‌کنه
    private val launchDelayTicks = 8

    // --- افزایش تدریجی سرعت وقتی برگشتن توپ‌ها طول می‌کشه ---
    private val speedRampStartTicks = 90   // تا این تیک، سرعت طبیعی (~۱.۵ ثانیه)
    private val speedRampFullTicks  = 240  // از این تیک به بعد، سرعت به حداکثر می‌رسه (~۴ ثانیه)
    private val maxSpeedMultiplier  = 3f   // حداکثر چند برابر شدن سرعت

    private var topBarHeightPx    = 0f
    private var bottomBarHeightPx = 0f
    private var lastKnownSize = Size.Zero

    fun onCanvasReady(size: Size, topBarPx: Float, bottomBarPx: Float) {
        val changed = size != lastKnownSize ||
                topBarPx    != topBarHeightPx ||
                bottomBarPx != bottomBarHeightPx
        if (!changed) return
        lastKnownSize      = size
        topBarHeightPx     = topBarPx
        bottomBarHeightPx  = bottomBarPx
        val origin = Offset(size.width / 2f, size.height - bottomBarHeightPx - ballRadius * 0.3f)
        val wasEmpty = _state.value.blocks.isEmpty()
        _state.update { it.copy(canvasSize = size, ballLaunchOrigin = origin) }
        if (wasEmpty) spawnInitialBlocks(size)
    }

    private fun spawnInitialBlocks(size: Size) {
        _state.update { it.copy(blocks = generateRow(size, 0, 1)) }
    }

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
        // فقط جلوی کاملاً افقی یا پایین رو بگیر — حداقل ۵ درجه از افق
        val minSin = sin(Math.toRadians(5.0)).toFloat()
        if (normY > -minSin) {
            normY = -minSin
            val rem = sqrt(max(0f, 1f - normY * normY))
            normX = if (normX >= 0f) rem else -rem
        }
        return Offset(normX * ballSpeed, normY * ballSpeed)
    }

    private fun launchBalls(velocity: Offset) {
        val s = _state.value
        val balls = (0 until s.ballCount).map { i ->
            Ball(id = i, position = s.ballLaunchOrigin, velocity = velocity,
                isMoving = false, isReturned = false)
        }.toMutableList()
        if (balls.isNotEmpty()) balls[0] = balls[0].copy(isMoving = true)
        _state.update {
            it.copy(
                balls = balls, phase = GamePhase.Shooting,
                aimDirection = Offset.Zero,
                ballsToLaunchLeft = s.ballCount - 1, launchDelayCounter = 0,
                ticksSinceLaunch = 0
            )
        }
    }

    fun tick() {
        val s = _state.value
        if (s.phase != GamePhase.Shooting) return
        if (s.canvasSize == Size.Zero) return

        val canvasW   = s.canvasSize.width
        val blockSize = blockSizeFor(canvasW)
        val mutableBlocks = s.blocks.toMutableList()
        var scoreGained = 0

        var nextBallsToLaunchLeft  = s.ballsToLaunchLeft
        var nextLaunchDelayCounter = s.launchDelayCounter

        val updatedBalls = s.balls.mapIndexed { index, ball ->
            val ballToLaunchIndex = s.ballCount - nextBallsToLaunchLeft
            if (index == ballToLaunchIndex && nextBallsToLaunchLeft > 0) {
                if (nextLaunchDelayCounter >= launchDelayTicks) ball.copy(isMoving = true) else ball
            } else ball
        }.toMutableList()

        if (nextBallsToLaunchLeft > 0) {
            if (nextLaunchDelayCounter >= launchDelayTicks) {
                nextLaunchDelayCounter = 0; nextBallsToLaunchLeft--
            } else nextLaunchDelayCounter++
        }

        val hitBlockHpReductions = mutableMapOf<String, Int>()

        // پایین‌ترین لبه‌ی بلوک‌های زنده — اگر توپی از این پایین‌تر رفته و در حال نزوله،
        // دیگه امکان برخورد با هیچ بلوکی نداره، پس نباید منتظر بازگشتش از دیواره‌ها بمونیم.
        val lowestBlockBottom = mutableBlocks
            .filter { !it.isDestroyed }
            .maxOfOrNull { blockRect(it, canvasW, blockSize).bottom }
            ?: topBarHeightPx

        // هر چه بیشتر طول بکشه تا توپ‌ها برگردن، سرعت بیشتر می‌شه تا نوبت زودتر تموم بشه
        val speedMultiplier = speedMultiplierFor(s.ticksSinceLaunch)

        for (i in updatedBalls.indices) {
            val ball = updatedBalls[i]
            if (!ball.isMoving || ball.isReturned) continue

            // در حال نزول و پایین‌تر از آخرین بلوک زنده → دیگه به هیچ بلوکی نمی‌خوره،
            // پس به‌جای صبر برای چند بار برخورد با دیواره‌ها، فوراً برش می‌گردونیم.
            if (ball.velocity.y > 0f && ball.position.y - ballRadius > lowestBlockBottom) {
                updatedBalls[i] = ball.copy(
                    position = s.ballLaunchOrigin, isMoving = false, isReturned = true
                )
                continue
            }

            // --- substep: تعداد substep با سرعت لحظه‌ای هماهنگ می‌شه تا با افزایش سرعت هم tunneling رخ نده ---
            val subSteps = (4 * speedMultiplier).roundToInt().coerceIn(4, 14)
            var pos = ball.position
            var vel = ball.velocity
            var returned = false
            var hitThisTick = false

            repeat(subSteps) { step ->
                if (returned || hitThisTick) return@repeat
                // سرعت هر substep از vel فعلی (ضرب‌شده در speedMultiplier) محاسبه می‌شود
                val stepVel = Offset(
                    vel.x * speedMultiplier / subSteps,
                    vel.y * speedMultiplier / subSteps
                )
                pos = pos + stepVel

                // دیوار چپ/راست
                if (pos.x - ballRadius <= 0f) {
                    vel = vel.copy(x = abs(vel.x)); pos = pos.copy(x = ballRadius)
                } else if (pos.x + ballRadius >= canvasW) {
                    vel = vel.copy(x = -abs(vel.x)); pos = pos.copy(x = canvasW - ballRadius)
                }
                // دیوار بالا
                if (pos.y - ballRadius <= topBarHeightPx) {
                    vel = vel.copy(y = abs(vel.y))
                    pos = pos.copy(y = topBarHeightPx + ballRadius)
                }
                // برگشت — مقایسه مرکز به مرکز؛ ballLaunchOrigin مرکز توپ است نه کف آن.
                // فقط وقتی برگرد که واقعاً از بالا نزول کرده و از خط launch رد شده باشد
                // (نه در همون substepهای اول بعد از شلیک).
                if (pos.y >= s.ballLaunchOrigin.y && vel.y > 0f) {
                    pos = pos.copy(y = s.ballLaunchOrigin.y)
                    returned = true; return@repeat
                }
                // برخورد با بلوک
                for (blk in mutableBlocks) {
                    if (blk.isDestroyed) continue
                    val rect = blockRect(blk, canvasW, blockSize)
                    val cx = pos.x.coerceIn(rect.left, rect.right)
                    val cy = pos.y.coerceIn(rect.top, rect.bottom)
                    val dx = pos.x - cx
                    val dy = pos.y - cy
                    if (dx * dx + dy * dy < ballRadius * ballRadius) {
                        hitBlockHpReductions[blk.id] = (hitBlockHpReductions[blk.id] ?: 0) + 1
                        scoreGained++
                        val ox = ballRadius - abs(dx)
                        val oy = ballRadius - abs(dy)
                        vel = if (cx == rect.left || cx == rect.right) {
                            if (cy == rect.top || cy == rect.bottom) {
                                if (ox < oy) vel.copy(x = -vel.x) else vel.copy(y = -vel.y)
                            } else vel.copy(x = -vel.x)
                        } else vel.copy(y = -vel.y)
                        // نوته: stepVel بار بعد از vel جدید محاسبه می‌شود، نیاز به آپدیت دستی نیست
                        hitThisTick = true
                        break
                    }
                }
            }

            updatedBalls[i] = if (returned) {
                ball.copy(position = s.ballLaunchOrigin, velocity = vel,
                    isMoving = false, isReturned = true)
            } else {
                ball.copy(position = pos, velocity = vel)
            }
        }

        val newlyDestroyed = mutableListOf<ExplodingBlock>()
        val updatedBlocks = mutableBlocks.mapNotNull { blk ->
            val reduction = hitBlockHpReductions[blk.id] ?: 0
            if (reduction > 0) {
                val newHp = blk.hp - reduction
                if (newHp <= 0) {
                    val rect = blockRect(blk, canvasW, blockSize)
                    newlyDestroyed += ExplodingBlock(
                        id = blk.id,
                        fragments = createShatterFragments(rect, blockColorForHp(blk.hp, blk.maxHp))
                    )
                    null
                } else blk.copy(hp = newHp)
            } else blk
        }

        // انیمیشن شکستن: ~۲۴ تیک (~ ۳۸۰ میلی‌ثانیه) — تکه‌ها پخش می‌شن، با گرانش می‌افتن و محو می‌شن
        val explodeStep = 1f / 24f
        val fragmentGravity = 0.7f
        val updatedExploding = (s.explodingBlocks.map { eb ->
            eb.copy(
                progress = eb.progress + explodeStep,
                fragments = eb.fragments.map { f ->
                    f.copy(
                        cx = f.cx + f.vx,
                        cy = f.cy + f.vy,
                        vy = f.vy + fragmentGravity,
                        rotationDeg = f.rotationDeg + f.rotationSpeed
                    )
                }
            )
        } + newlyDestroyed).filter { it.progress < 1f }

        val newScore = s.score + scoreGained
        val allReturned = updatedBalls.all { it.isReturned }
        if (allReturned) {
            _state.update { it.copy(explodingBlocks = updatedExploding) }
            endTurn(updatedBlocks, newScore)
        } else _state.update {
            it.copy(
                balls = updatedBalls, blocks = updatedBlocks, score = newScore,
                ballsToLaunchLeft = nextBallsToLaunchLeft,
                launchDelayCounter = nextLaunchDelayCounter,
                ticksSinceLaunch = s.ticksSinceLaunch + 1,
                explodingBlocks = updatedExploding
            )
        }
    }

    private fun endTurn(remainingBlocks: List<Block>, newScore: Int) {
        val s = _state.value
        val canvasW      = s.canvasSize.width
        val newBallCount = s.ballCount + 1
        val newTurn      = s.turn + 1
        val movedBlocks  = remainingBlocks.map { it.copy(row = it.row + 1) }
        val blockSize    = blockSizeFor(canvasW)
        val maxRow = ((s.canvasSize.height - topBarHeightPx - bottomBarHeightPx) /
                (blockSize.height + blockPadding)).toInt()
        if (movedBlocks.any { it.row >= maxRow }) {
            _state.update { it.copy(blocks = movedBlocks, score = newScore, phase = GamePhase.GameOver, ticksSinceLaunch = 0, explodingBlocks = emptyList()) }
            return
        }
        val newRow = generateRow(s.canvasSize, 0, newBallCount)
        _state.update {
            it.copy(
                balls = emptyList(), blocks = newRow + movedBlocks,
                ballCount = newBallCount, score = newScore,
                phase = GamePhase.Idle, turn = newTurn,
                ballsToLaunchLeft = 0, launchDelayCounter = 0, ticksSinceLaunch = 0,
                explodingBlocks = emptyList()
            )
        }
    }

    /**
     * هر چه از شروع شلیک این نوبت تیک بیشتری گذشته باشه، سرعت بیشتر می‌شه —
     * تا زمانی که برگشتن توپ‌ها بیش از حد طول بکشه، بازی خودش سرعتش رو زیاد کنه.
     */
    private fun speedMultiplierFor(ticksSinceLaunch: Int): Float {
        if (ticksSinceLaunch <= speedRampStartTicks) return 1f
        val rampDuration = (speedRampFullTicks - speedRampStartTicks).coerceAtLeast(1)
        val progress = ((ticksSinceLaunch - speedRampStartTicks).toFloat() / rampDuration)
            .coerceIn(0f, 1f)
        return 1f + progress * (maxSpeedMultiplier - 1f)
    }

    private fun blockSizeFor(canvasW: Float): Size {
        val totalPad = blockPadding * (cols + 1)
        val w = (canvasW - totalPad) / cols
        return Size(w, w * 0.55f)
    }

    fun blockRect(block: Block, canvasW: Float, blockSize: Size): androidx.compose.ui.geometry.Rect {
        val x = blockPadding + block.col * (blockSize.width + blockPadding)
        val y = topBarHeightPx + blockPadding + block.row * (blockSize.height + blockPadding)
        return androidx.compose.ui.geometry.Rect(x, y, x + blockSize.width, y + blockSize.height)
    }

    fun blockSizePublic(canvasW: Float) = blockSizeFor(canvasW)

    private fun generateRow(size: Size, row: Int, currentBallCount: Int): List<Block> {
        val rng = java.util.Random()
        val result = mutableListOf<Block>()
        var spawnedAny = false
        for (col in 0 until cols) {
            if (rng.nextFloat() < 0.7f) {
                val hp = (1..currentBallCount.coerceAtLeast(1)).random()
                result.add(Block(id = UUID.randomUUID().toString(), hp = hp, maxHp = hp, col = col, row = row))
                spawnedAny = true
            }
        }
        if (!spawnedAny) {
            val col = rng.nextInt(cols)
            result.add(Block(id = UUID.randomUUID().toString(), hp = 1, maxHp = 1, col = col, row = row))
        }
        return result
    }

    fun restartGame() {
        lastKnownSize = Size.Zero
        val size   = _state.value.canvasSize
        val origin = Offset(size.width / 2f, size.height - bottomBarHeightPx - ballRadius * 0.3f)
        _state.value = GameState(canvasSize = size, ballLaunchOrigin = origin)
        if (size != Size.Zero) spawnInitialBlocks(size)
    }
}