# Gobang

[中文](README.zh.md) | English

A cross-platform Gomoku (Five in a Row) game with AI, built with Kotlin Multiplatform and Compose Multiplatform.

## Features

- **AI Engine** — Negamax + Alpha-Beta pruning
- **26 Standard Renju Openings** — 13 direct + 13 indirect openings with 3-move sequences
- **3 Difficulty Levels** — Easy / Medium / Hard (search depth 1–3)
- **4 Game Modes** — PvAI, AIvP, PvP, AIvAI (spectate)
- **Theme Support** — Light, Dark, System (auto-detect)
- **Bilingual UI** — Chinese / English
- **Persistent Saves** — Save/load games across sessions
- **Cross-Platform** — Android, Desktop (JVM), iOS, Web (WasmJS)

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.1.21 |
| UI | Compose Multiplatform 1.8.1 |
| Design | Material Design 3 |
| Async | kotlinx-coroutines 1.10.2 |
| Serialization | kotlinx-serialization 1.7.3 |

## Supported Platforms

| Platform | Min Version | Notes |
|----------|-------------|-------|
| Android | API 26 (Android 8.0) | Target SDK 35 |
| Desktop (JVM) | JDK 17+ | 600×800 window |
| iOS | iOS 13+ | via Compose Multiplatform |
| Web | Modern browsers | Kotlin/WasmJS |

## Project Structure

```
gobang/
├── engine/             # Pure Kotlin AI engine (no UI dependencies)
│   ├── Board.kt            # 15×15 game board
│   ├── Evaluator.kt        # Position evaluation
│   ├── Searcher.kt         # Negamax + Alpha-Beta search
│   └── OpeningBook.kt      # 26 standard openings
│
├── app/                # Compose Multiplatform UI
│   ├── commonMain/
│   │   ├── App.kt              # App entry + theme
│   │   ├── AppContentImpl.kt   # Navigation logic
│   │   ├── i18n/               # Language management
│   │   ├── model/              # GameState, Difficulty, GameMode
│   │   ├── storage/            # Save/load (Repository pattern)
│   │   ├── ui/
│   │   │   ├── component/Board.kt   # Canvas board rendering
│   │   │   ├── screen/              # MainMenu, Game, Settings
│   │   │   └── theme/               # Light/Dark color schemes
│   │   └── viewmodel/GameViewModel.kt
│   ├── androidMain/       # Android platform code
│   ├── jvmMain/           # Desktop platform code
│   ├── iosMain/           # iOS platform code
│   └── wasmJsMain/        # Web platform code
│
└── iosApp/             # iOS Xcode project wrapper
```

## Build & Run

### Prerequisites

- JDK 17+
- Android SDK (for Android builds)
- Xcode (for iOS builds)

### Commands

```bash
# Android
./gradlew assembleDebug

# Desktop (JVM)
./gradlew :app:run

# Web (WasmJS)
./gradlew :app:wasmJsBrowserDevelopmentRun

# Run all tests
./gradlew allTests
```

## Architecture

- **MVI Pattern** — `GameViewModel` manages `GameState` via `StateFlow`
- **expect/actual** — Platform-specific implementations for sound, storage, status bar
- **Repository Pattern** — `GameStateRepository` interface with per-platform storage backends
- **Sealed Classes** — Navigation (`Screen`) and opening selection (`OpeningChoice`)

## Storage Backends

| Platform | Method |
|----------|--------|
| Android | SharedPreferences (JSON) |
| Desktop | File (`~/.gobang/save.json`) |
| iOS | NSUserDefaults (JSON) |
| Web | localStorage (JSON) |

## AI Engine

Pure Kotlin `engine` module with zero UI dependencies.

### Search Algorithm: Negamax + Alpha-Beta Pruning

```
search(board, turn, depth)
  │
  ├─ Copy board to temp array (avoid mutating original state)
  │
  └─ searchInternal(turn, depth, α, β)
       │
       ├─ Leaf node (depth=0) → call Evaluator to score the position
       │
       ├─ Early termination: if |score| ≥ 9999 (game decided), return immediately
       │
       ├─ Generate candidate moves: scan all empty cells, sorted by position weight descending
       │
       └─ For each candidate move:
            ├─ Place stone → recurse with opponent's perspective (negate score)
            ├─ Undo stone (backtrack)
            └─ Alpha-Beta pruning: terminate when α ≥ β
```

**Key features:**
- **Negamax framework**: Opponent's best choice equals negating own score, simplifying search logic
- **Alpha-Beta pruning**: β-pruning skips branches that cannot be better, reducing search space by ~√N on average
- **High-score confirmation**: When search score > 8000, re-search at depth=1 to precisely confirm winning/losing moves
- **Best move recorded at root only**: `bestMove` is updated only when `depth == maxDepth`

### Position Evaluator

The evaluator scans the board in four directions (horizontal, vertical, left-diagonal, right-diagonal), identifies patterns, and computes a weighted score.

```
Board scan flow:
  for each occupied position (i, j):
      ├─ Horizontal scan → analysisHorizontal()
      ├─ Vertical scan   → analysisVertical()
      ├─ Left-diagonal   → analysisLeft()
      └─ Right-diagonal  → analysisRight()

  Each direction → analysisLine():
      1. Expand outward from pos to find consecutive same-color range [xl, xr]
      2. Continue expanding until blocked by opponent stones → [leftRange, rightRange]
      3. Identify pattern based on consecutive count + empty endpoints
```

**Pattern definitions and scores:**

| Pattern | Code | Description | Threat Level |
|---------|------|-------------|--------------|
| FIVE | 7 | Five in a row | Instant win (9999) |
| FOUR | 6 | Open four (both ends open, guaranteed five) | Winning (9990) |
| SFOUR | 3 | Half-open four (one end open) | Very high (9980) |
| THREE | 5 | Open three (both ends open, can form open four) | High (9950) |
| STHREE | 2 | Half-open three (one end open) | Medium (×10) |
| TWO | 4 | Open two | Low (×4) |
| STWO | 1 | Half-open two | Very low (×1) |

**Scoring logic:**

```
Quick detection (by priority, descending):
  ├─ Five in a row       → ±9999 (game over)
  ├─ Own open four       → +9990
  ├─ Own half-open four  → +9980
  ├─ Opponent open four  → -9970 (must defend)
  ├─ Opponent half-open four + open three → -9960 (must defend)
  ├─ Own open three and no opponent half-open four → +9950
  └─ Otherwise → weighted sum (open three ×200, half-open three ×10, open two ×4, half-open two ×1)

Weighted sum:
  finalScore = attackerWeightedScore - defenderWeightedScore + positionWeightDiff
```

**Position weight matrix (POS):**

```
Center=7, decreasing by ring to edge=0:
  0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
  0 1 1 1 1 1 1 1 1 1 1 1 1 1 0
  0 1 2 2 2 2 2 2 2 2 2 2 2 1 0
  0 1 2 3 3 3 3 3 3 3 3 3 2 1 0
  0 1 2 3 4 4 4 4 4 4 4 3 2 1 0
  0 1 2 3 4 5 5 5 5 5 4 3 2 1 0
  0 1 2 3 4 5 6 6 6 5 4 3 2 1 0
  0 1 2 3 4 5 6 7 6 5 4 3 2 1 0
  0 1 2 3 4 5 6 6 6 5 4 3 2 1 0
  ...
```

Center positions have the highest weight, encouraging AI to contest the board center.

### Opening Book

Based on the official 26-standard Renju opening system. Each opening contains the first 3 moves (Black-White-Black):

- **Direct openings (13)**: White 2 placed at GH(6,7), one step above center
- **Indirect openings (13)**: White 2 placed at GI(6,8), diagonal from center

Opening names: Hanxing, Xiyue, Shuxing, Huayue, Canyue, Yuyue, Jinxing, Songyue, Qiuyue, Xinyue, Ruixing, Shanyue, Youxing, Changxing, Xiayue, Hengxing, Shuiyue, Liuxing, Yunyue, Puyue, Lanyue, Yinyue, Mingxing, Xieyue, Mingyue, Huixing

Data source: [New-Renju joseki.json](https://github.com/yutokure/New-Renju/blob/master/joseki.json)

### AI Execution Flow

```
User places stone → ViewModel.handleUserMove()
  │
  ├─ Check win/loss → if game over, stop
  ├─ Switch to opponent's turn
  └─ AIvAI / PvAI mode → triggerAiMove()
       │
       └─ LaunchedEffect observes isAiThinking → computeAiMove()
            │
            ├─ while loop (supports consecutive AI turns, e.g. AIvAI)
            │   ├─ Check if game has ended
            │   ├─ Run search on Dispatchers.Default
            │   │   ├─ Rebuild temporary GobangBoard from UI state
            │   │   ├─ searcher.search(board, turn, depth)
            │   │   │   └─ Negamax + Alpha-Beta (see above)
            │   │   └─ Return SearchResult(score, row, col)
            │   ├─ Apply move to board
            │   └─ If still AI's turn → continue loop
            │
            └─ PvP mode: AI not triggered
```

**Difficulty and search depth:**

| Difficulty | Search Depth | Description |
|------------|--------------|-------------|
| Easy | 1 | Looks one move ahead, high randomness |
| Medium | 2 | Looks two moves ahead, moderate strategy |
| Hard | 3 | Looks three moves ahead, stronger but slower |

## License

MIT
