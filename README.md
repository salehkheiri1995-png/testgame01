# TestGame01 - Android Breakout Game

A simple Breakout-style arcade game built for Android using **Kotlin** and **Jetpack Compose**.

## 🎮 Game Overview

TestGame01 is a single-player arcade game where you launch balls to destroy blocks. The game features:

- **Drag-to-aim mechanics**: Drag on the screen to aim your shot
- **Progressive difficulty**: Each turn adds more balls and increases block HP
- **Dynamic block colors**: Blocks change color based on their remaining HP
- **Physics-based ball movement**: Balls bounce off walls and blocks realistically
- **Turn-based gameplay**: Launch all balls, wait for them to return, then plan your next shot

## 🏗️ Architecture

The app follows a clean **MVVM (Model-View-ViewModel)** architecture:

```
app/src/main/java/com/example/testgame01/
├── MainActivity.kt          # Entry point, sets up Compose theme
├── model/
│   ├── GameModels.kt        # Data classes: Ball, Block, GamePhase
│   └── GameState.kt         # Game state holder
├── ui/
│   ├── GameScreen.kt        # Composable UI with Canvas rendering
│   └── theme/               # App theming (Color, Type, Theme)
└── viewmodel/
    └── GameViewModel.kt     # Game logic, physics, state management
```

### Key Components

#### Models (`model/`)
- **Ball**: Represents a game ball with position, velocity, and state
- **Block**: Represents destructible blocks with HP and dynamic colors
- **GamePhase**: Sealed class defining game states (Idle, Aiming, Shooting, GameOver)
- **GameState**: Holds the entire game state as immutable data

#### ViewModel (`viewmodel/GameViewModel.kt`)
- Manages game state using `StateFlow` for reactive updates
- Handles drag gestures for aiming
- Runs the game loop with 16ms delay (~60 FPS)
- Implements collision detection (circle-rectangle)
- Manages ball reflection physics
- Controls turn progression and game over conditions

#### UI (`ui/GameScreen.kt`)
- Renders game using Jetpack Compose `Canvas`
- Draws blocks with gradient fills and HP labels
- Shows aim direction with dashed line indicator
- Displays active balls with glow effects
- Includes top HUD (Score, Turn, Balls) and Game Over overlay

## ⚙️ Technical Requirements

- **Minimum SDK**: 30 (Android 11)
- **Target SDK**: 36
- **Compile SDK**: 36
- **Java Version**: 11

## 🛠️ Dependencies

- **AndroidX Core KTX**: Kotlin extensions for Android
- **Jetpack Compose**: Modern UI toolkit
  - Compose BOM, UI, Graphics, Material3
  - Compose Tooling for preview
- **Lifecycle ViewModel Compose**: ViewModel integration with Compose
- **Activity Compose**: Compose support in Activity

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 11 or higher
- Android SDK with API level 30+

### Build & Run

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd testgame01
   ```

2. Open the project in Android Studio

3. Sync Gradle files

4. Run on an emulator or physical device:
   ```bash
   ./gradlew installDebug
   ```

## 🎯 How to Play

1. **Aim**: Drag anywhere on the screen to set the launch direction
   - A dashed line shows your aim trajectory
   - Drag must be upward (balls only launch upward)
   - Minimum drag distance required to register aim

2. **Launch**: Release to shoot all available balls
   - Balls are launched sequentially with slight delays
   - First turn: 1 ball, increases each turn

3. **Destroy Blocks**: Hit blocks to reduce their HP
   - Green (HP 1-3) → Light Green (4-8) → Yellow (9-13) → Orange (14-18) → Red (19+)
   - Each hit earns 1 point

4. **Collect Balls**: Wait for balls to return to the launch zone
   - All balls must return before the next turn begins
   - Each turn adds +1 ball to your arsenal

5. **Avoid Game Over**: Don't let blocks reach the bottom!
   - Blocks move down each turn
   - Game ends when any block reaches the ball launch area

## 📐 Game Mechanics

### Physics
- **Ball Speed**: Fixed at 18 units per frame
- **Ball Radius**: 22 pixels
- **Wall Bouncing**: Perfect reflection on left, right, and top walls
- **Block Collision**: Circle-rectangle intersection detection with normal-based reflection

### Grid System
- **Columns**: 6 blocks per row
- **Block Size**: Dynamically calculated based on canvas width
- **Block Padding**: 8 pixels between blocks
- **Block Height**: 55% of block width

### Turn Progression
1. All balls launched → All balls returned → New turn
2. Each turn: +1 ball count, blocks move down 1 row
3. New row of blocks spawned at top each turn
4. Block HP scales with current ball count

## 🎨 Visual Design

- **Background**: Deep navy blue (#1A1A2E)
- **Top Bar**: Darker navy (#16213E) with score display
- **Balls**: Light gray (#E0E0E0) with radial glow effect
- **Aim Line**: Semi-transparent white dashed line with fade effect
- **Blocks**: Gradient fill with color based on HP, white border overlay

## 📝 Code Highlights

### Reactive State Management
```kotlin
private val _state = MutableStateFlow(GameState())
val state: StateFlow<GameState> = _state.asStateFlow()
```

### Game Loop
```kotlin
viewModelScope.launch {
    while (_state.value.phase == GamePhase.Shooting) {
        delay(16L)  // ~60 FPS
        tickUpdate()
    }
}
```

### Collision Detection
```kotlin
private fun circleRectCollide(
    center: Offset, radius: Float,
    rect: androidx.compose.ui.geometry.Rect
): Boolean {
    val nearX = center.x.coerceIn(rect.left, rect.right)
    val nearY = center.y.coerceIn(rect.top, rect.bottom)
    val dx = center.x - nearX
    val dy = center.y - nearY
    return dx * dx + dy * dy <= radius * radius
}
```

## 🔧 Customization

You can easily modify game parameters in `GameViewModel.kt`:

```kotlin
val ballRadiusPublic = 22f          // Ball size
private val cols = 6                 // Number of columns
private val blockPadding = 8f        // Space between blocks
private val topBarHeight = 120f      // Top HUD height
private val bottomBarHeight = 160f   // Bottom margin for ball launch
```

## 📄 License

This project is open source and available for educational purposes.

## 🤝 Contributing

Feel free to fork the repository and submit pull requests for:
- New game modes
- Power-ups and special balls
- Enhanced visual effects
- Sound effects and music
- High score tracking

---

**Built with ❤️ using Kotlin & Jetpack Compose**
