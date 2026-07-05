# 🎮 بازی Breakout - TestGame01

یک بازی سبک Breakout/Arkanoid ساخته شده با **Jetpack Compose** و **Kotlin** برای اندروید.

---

## 📋 فهرست مطالب

- [معرفی بازی](#-معرفی-بازی)
- [امکانات و ویژگی‌ها](#-امکانات-ویژگی‌ها)
- [معماری پروژه](#-معماری-پروژه)
- [پیش‌نیازها](#-پیش‌نیازها)
- [نحوه اجرا و ساخت](#-نحوه-اجرا-و-ساخت)
- [راهنمای بازی](#-راهنمای-بازی)
- [مکانیک‌های بازی](#-مکانیک‌های-بازی)
- [طراحی بصری](#-طراحی-بصری)
- [ساختار کد](#-ساختار-کد)
- [شخصی‌سازی](#-شخصی‌سازی)

---

## 🎯 معرفی بازی

این بازی یک نسخه مدرن از بازی کلاسیک Breakout است که در آن بازیکن باید با پرتاب توپ به سمت بلوک‌ها، آن‌ها را نابود کند. با هر دور بازی، تعداد توپ‌ها افزایش یافته و چالش بیشتر می‌شود.

### ویژگی‌های کلیدی:
- ✅ کنترل لمسی با سیستم Drag برای هدف‌گیری
- ✅ فیزیک برخورد واقعی توپ با دیوارها و بلوک‌ها
- ✅ سیستم نوبتی با افزایش تدریجی سختی
- ✅ گرافیک مدرن با Jetpack Compose
- ✅ معماری MVVM با StateFlow
- ✅ پشتیبانی از Edge-to-Edge

---

## 🚀 امکانات و ویژگی‌ها

### گیم‌پلی
- **هدف‌گیری دقیق**: با کشیدن انگشت روی صفحه، جهت پرتاب توپ را مشخص کنید
- **چند توپ همزمان**: در هر نوبت، تعداد توپ‌ها برابر با شماره دور بازی است
- **سیستم امتیازدهی**: با نابودی هر بلوک، ۱ امتیاز کسب می‌کنید
- **پایان بازی**: وقتی بلوک‌ها به پایین صفحه برسند، بازی تمام می‌شود

### فنی
- **Jetpack Compose**: UI کاملاً Declarative
- **StateFlow**: مدیریت state به صورت Reactive
- **ViewModel Architecture**: جداسازی منطق بازی از UI
- **Coroutines**: مدیریت async برای حلقه بازی

---

## 🏗 معماری پروژه

```
app/src/main/java/com/example/testgame01/
├── MainActivity.kt              # Activity اصلی
├── ui/
│   ├── GameScreen.kt            # کامپوننت‌های UI و رندرینگ
│   └── theme/                   # تم‌های رنگی و تایپوگرافی
├── viewmodel/
│   └── GameViewModel.kt         # منطق بازی و مدیریت State
└── model/
    ├── GameState.kt             # داده‌های کلی بازی
    └── GameModels.kt            # مدل‌های Ball, Block, GamePhase
```

### جریان داده (Data Flow)
```
User Input → GameViewModel → StateFlow<GameState> → GameScreen (Composable)
```

---

## 📦 پیش‌نیازها

| مورد | نسخه |
|------|------|
| **Android Studio** | Arctic Fox یا جدیدتر |
| **Kotlin** | 1.9+ |
| **Min SDK** | 30 (Android 11) |
| **Target SDK** | 36 |
| **Compile SDK** | 36 |
| **Jetpack Compose** | آخرین نسخه BOM |

### کتابخانه‌های اصلی:
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `androidx.activity:activity-compose`
- `androidx.compose.material3`
- `androidx.compose.ui`

---

## 🛠 نحوه اجرا و ساخت

### ۱. کلون کردن پروژه
```bash
git clone <repository-url>
cd TestGame01
```

### ۲. باز کردن در Android Studio
- پروژه را در Android Studio باز کنید
- اجازه دهید Gradle Sync انجام شود

### ۳. اجرای بازی
- دستگاه اندروید یا Emulator (SDK 30+) متصل کنید
- دکمه **Run** را بزنید یا از ترمینال:
```bash
./gradlew installDebug
```

### ۴. ساخت نسخه Release
```bash
./gradlew assembleRelease
```
خروجی در `app/build/outputs/apk/release/` قرار می‌گیرد.

---

## 🎮 راهنمای بازی

### شروع بازی
1. بازی در حالت **Idle** شروع می‌شود
2. یک توپ سفید در پایین صفحه منتظر پرتاب است

### هدف‌گیری (Aiming)
- انگشت خود را روی صفحه بکشید تا جهت پرتاب مشخص شود
- یک خط چین سفید مسیر توپ را نشان می‌دهد
- حداقل زاویه ۱۰ درجه به سمت بالا لازم است

### پرتاب (Shooting)
- انگشت را رها کنید تا توپ پرتاب شود
- در دورهای بالاتر، چندین توپ با تأخیر پرتاب می‌شوند
- توپ به دیوارها و بلوک‌ها برخورد کرده و برمی‌گردد

### پایان نوبت
- وقتی تمام توپ‌ها برگشتند، نوبت تمام می‌شود
- یک ردیف جدید از بلوک‌ها اضافه می‌شود
- تعداد توپ‌ها برای دور بعد افزایش می‌یابد

### باخت (Game Over)
- اگر بلوک‌ها به پایین صفحه برسند، بازی تمام می‌شود
- امتیاز نهایی نمایش داده می‌شود
- دکمه "PLAY AGAIN" برای شروع مجدد

---

## ⚙️ مکانیک‌های بازی

### فیزیک برخوردها

#### برخورد با دیوارها
```kotlin
// دیوار چپ و راست
if (pos.x - ballRadius <= 0f) vel = vel.copy(x = abs(vel.x))
if (pos.x + ballRadius >= canvasW) vel = vel.copy(x = -abs(vel.x))

// سقف
if (pos.y - ballRadius <= topBarHeight) vel = vel.copy(y = abs(vel.y))
```

#### برخورد با بلوک‌ها
- تشخیص برخورد دایره (توپ) با مستطیل (بلوک)
- محاسبه نقطه نزدیک‌ترین فاصله
- بازتاب سرعت بر اساس جهت برخورد

### سیستم نوبتی
| دور | تعداد توپ | HP بلوک‌ها |
|-----|-----------|------------|
| 1 | 1 | 1-2 |
| 2 | 2 | 1-3 |
| 3 | 3 | 1-4 |
| n | n | متغیر |

### تولید بلوک‌ها
```kotlin
val hp = (1..currentBallCount).random() + (0..currentBallCount / 2).random()
```
HP بلوک‌ها بر اساس شماره دور به صورت تصادفی تعیین می‌شود.

---

## 🎨 طراحی بصری

### پالت رنگی

| عنصر | رنگ | کد Hex |
|------|-----|--------|
| پس‌زمینه | سرمه‌ای تیره | `#1A1A2E` |
| نوار بالا | سرمه‌ای | `#16213E` |
| توپ | خاکستری روشن | `#E0E0E0` |
| خط هدف‌گیری | سفید شفاف | `#99FFFFFF` |

### رنگ بلوک‌ها بر اساس HP

| HP | رنگ | کد Hex |
|----|-----|--------|
| 1-3 | سبز | `#4CAF50` |
| 4-8 | سبز روشن | `#8BC34A` |
| 9-13 | زرد | `#FFC107` |
| 14-18 | نارنجی | `#FF9800` |
| 19+ | قرمز | `#F44336` |

### ابعاد و اندازه‌ها

```kotlin
val ballRadius = 22f           // شعاع توپ
val cols = 6                   // تعداد ستون بلوک‌ها
val blockPadding = 8f          // فاصله بین بلوک‌ها
val topBarHeight = 120f        // ارتفاع نوار بالا
val bottomBarHeight = 160f     // ارتفاع ناحیه پرتاب
```

---

## 💻 ساختار کد

### GameViewModel.kt

مسئولیت‌های اصلی:
- مدیریت State بازی با `MutableStateFlow`
- پردازش ورودی‌های لمسی (Drag)
- اجرای حلقه بازی (Game Loop)
- تشخیص برخوردها
- مدیریت نوبت‌ها و امتیازات

```kotlin
class GameViewModel : ViewModel() {
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()
    
    fun onDragStart(position: Offset) { ... }
    fun onDrag(position: Offset) { ... }
    fun onDragEnd() { ... }
    fun restartGame() { ... }
}
```

### GameScreen.kt

کامپوننت‌های UI:
- `GameScreen`: کامپوننت اصلی بازی
- `TopHud`: نمایش امتیاز، دور و تعداد توپ
- `GameOverScreen`: صفحه پایان بازی
- `drawBall`, `drawBlock`, `drawAimLine`: توابع رندرینگ

```kotlin
@Composable
fun GameScreen(viewModel: GameViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        // رندر بلوک‌ها، توپ و خطوط
    }
    
    TopHud(score = state.score, ...)
    
    if (state.phase == GamePhase.GameOver) {
        GameOverScreen(...)
    }
}
```

### مدل‌ها

```kotlin
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
    var isDestroyed: Boolean = false
)

sealed class GamePhase {
    object Idle : GamePhase()
    object Aiming : GamePhase()
    object Shooting : GamePhase()
    object GameOver : GamePhase()
}
```

---

## 🔧 شخصی‌سازی

### تغییر سرعت توپ
در `GameViewModel.kt`:
```kotlin
val speed = 18f  // عدد بزرگ‌تر = سرعت بیشتر
```

### تغییر تعداد ستون‌ها
```kotlin
private val cols = 6  // تعداد ستون بلوک‌ها
```

### تغییر اندازه توپ
```kotlin
val ballRadiusPublic = 22f  // شعاع توپ
```

### تغییر رنگ‌ها
در `GameScreen.kt`:
```kotlin
private val BackgroundColor = Color(0xFF1A1A2E)
private val BallColor = Color(0xFFE0E0E0)
// ...
```

### تنظیمات بلوک‌ها
- `blockPadding`: فاصله بین بلوک‌ها
- `topBarHeight`: ارتفاع نوار اطلاعات
- فرمول تولید HP در `generateRow()`

---

## 📝 مجوز

این پروژه برای اهداف آموزشی و تمرینی توسعه داده شده است.

---

## 🤝 مشارکت

برای گزارش باگ یا پیشنهاد ویژگی جدید، لطفاً Issue ایجاد کنید یا Pull Request ارسال نمایید.

---

## 📞 تماس

برای سوالات و ارتباط:
- ایمیل: developer@example.com
- گیت‌هاب: @username

---

<div align="center">

**ساخته شده با ❤️ و Jetpack Compose**

</div>
