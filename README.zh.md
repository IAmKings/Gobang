# 五子棋

中文 | [English](README.md)

基于 Kotlin Multiplatform 和 Compose Multiplatform 构建的跨平台五子棋 AI 游戏。

## 功能特性

- **AI 引擎** — Negamax + Alpha-Beta 剪枝算法
- **26 标准连珠开局** — 13 种直止打法 + 13 种斜止打法，每局前 3 步固定
- **3 个难度等级** — 简单 / 中等 / 困难（搜索深度 1–3）
- **4 种游戏模式** — 人先手对战AI、AI先手对战人、双人对战、AI对战（观战）
- **主题支持** — 浅色、深色、跟随系统
- **双语界面** — 中文 / English
- **持久化存档** — 跨会话保存和加载游戏
- **跨平台** — Android、桌面端(JVM)、iOS、Web(WasmJS)

## 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Kotlin 2.1.21 |
| UI | Compose Multiplatform 1.8.1 |
| 设计 | Material Design 3 |
| 异步 | kotlinx-coroutines 1.10.2 |
| 序列化 | kotlinx-serialization 1.7.3 |

## 支持平台

| 平台 | 最低版本 | 备注 |
|------|----------|------|
| Android | API 26 (Android 8.0) | Target SDK 35 |
| 桌面端 (JVM) | JDK 17+ | 600×800 窗口 |
| iOS | iOS 13+ | 通过 Compose Multiplatform |
| Web | 现代浏览器 | Kotlin/WasmJS |

## 项目结构

```
gobang/
├── engine/             # 纯 Kotlin AI 引擎（无 UI 依赖）
│   ├── Board.kt            # 15×15 棋盘逻辑
│   ├── Evaluator.kt        # 局面评估器
│   ├── Searcher.kt         # Negamax + Alpha-Beta 搜索
│   └── OpeningBook.kt      # 26 种标准开局
│
├── app/                # Compose Multiplatform UI 应用
│   ├── commonMain/
│   │   ├── App.kt              # 应用入口 + 主题
│   │   ├── AppContentImpl.kt   # 导航逻辑
│   │   ├── i18n/               # 语言管理
│   │   ├── model/              # GameState、Difficulty、GameMode
│   │   ├── storage/            # 存档/读档（Repository 模式）
│   │   ├── ui/
│   │   │   ├── component/Board.kt   # Canvas 棋盘渲染
│   │   │   ├── screen/              # 主菜单、游戏、设置
│   │   │   └── theme/               # 浅色/深色配色方案
│   │   └── viewmodel/GameViewModel.kt
│   ├── androidMain/       # Android 平台代码
│   ├── jvmMain/           # 桌面端平台代码
│   ├── iosMain/           # iOS 平台代码
│   └── wasmJsMain/        # Web 平台代码
│
└── iosApp/             # iOS Xcode 工程包装器
```

## 构建与运行

### 环境要求

- JDK 17+
- Android SDK（用于 Android 构建）
- Xcode（用于 iOS 构建）

### 命令

```bash
# Android
./gradlew assembleDebug

# 桌面端 (JVM)
./gradlew :app:run

# Web (WasmJS)
./gradlew :app:wasmJsBrowserDevelopmentRun

# 运行全部测试
./gradlew allTests
```

## 架构

- **MVI 模式** — `GameViewModel` 通过 `StateFlow` 管理 `GameState`
- **expect/actual** — 平台特定实现：音效、存储、状态栏
- **Repository 模式** — `GameStateRepository` 接口，各平台独立存储后端
- **密封类** — 导航（`Screen`）和开局选择（`OpeningChoice`）

## 存储后端

| 平台 | 方式 |
|------|------|
| Android | SharedPreferences (JSON) |
| 桌面端 | 文件 (`~/.gobang/save.json`) |
| iOS | NSUserDefaults (JSON) |
| Web | localStorage (JSON) |

## AI 引擎

位于纯 Kotlin `engine` 模块，零 UI 依赖。

### 搜索算法：Negamax + Alpha-Beta 剪枝

```
search(board, turn, depth)
  │
  ├─ 复制棋盘到临时数组（避免污染原始状态）
  │
  └─ searchInternal(turn, depth, α, β)
       │
       ├─ 叶节点（depth=0）→ 调用 Evaluator 评估局面评分
       │
       ├─ 提前终止：若评分绝对值 ≥ 9999（已分胜负），直接返回
       │
       ├─ 生成候选着法：遍历所有空位，按位置权重矩阵降序排列
       │
       └─ 对每个候选着法：
            ├─ 落子 → 递归搜索对手视角（取负）
            ├─ 撤子（回溯）
            └─ Alpha-Beta 剪枝：α ≥ β 时终止剩余分支
```

**关键特性：**
- **Negamax 框架**：对手的最优选择等价于己方评分取负，简化了搜索逻辑
- **Alpha-Beta 剪枝**：β 剪枝跳过不可能更好的分支，平均减少约 √N 搜索量
- **高分确认**：当搜索评分 > 8000 时，以 depth=1 重新搜索，精确确认必胜/必败着法
- **仅根节点记录最佳着法**：`bestMove` 仅在 `depth == maxDepth` 时更新

### 局面评估器

评估器对棋盘四方向（横、竖、左斜、右斜）逐一扫描，识别棋型并加权评分。

```
棋盘扫描流程：
  遍历每个有棋子的位置 (i, j)：
      ├─ 横向扫描 → analysisHorizontal()
      ├─ 纵向扫描 → analysisVertical()
      ├─ 左斜扫描 → analysisLeft()
      └─ 右斜扫描 → analysisRight()

  每个方向 → analysisLine()：
      1. 从 pos 向两侧扩展，找到连续同色棋子范围 [xl, xr]
      2. 继续扩展到被异色棋子阻挡的范围 [leftRange, rightRange]
      3. 根据连续棋子数 + 两端空位情况，识别棋型
```

**棋型定义与分值：**

| 棋型 | 代号 | 含义 | 威胁等级 |
|------|------|------|----------|
| FIVE | 7 | 连五（五子连线） | 必胜（9999） |
| FOUR | 6 | 活四（两端空，必成五） | 必胜（9990） |
| SFOUR | 3 | 冲四（一端空） | 极高（9980） |
| THREE | 5 | 活三（两端空，可成活四） | 高（9950） |
| STHREE | 2 | 眠三（一端空） | 中（×10） |
| TWO | 4 | 活二 | 低（×4） |
| STWO | 1 | 眠二 | 极低（×1） |

**评分逻辑：**

```
快速判定（优先级递减）：
  ├─ 连五           → ±9999（立即结束）
  ├─ 己方活四       → +9990
  ├─ 己方冲四       → +9980
  ├─ 对方活四       → -9970（必须防守）
  ├─ 对方冲四+活三  → -9960（必须防守）
  ├─ 己方活三且对方无冲四 → +9950
  └─ 否则 → 加权求和（活三×200, 眠三×10, 活二×4, 眠二×1）

加权求和：
  finalScore = 攻方加权总分 - 守方加权总分 + 位置权重差
```

**位置权重矩阵（POS）：**

```
中心=7，逐圈递减至边缘=0：
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

中心位置权重最高，鼓励 AI 争夺棋盘中央。

### 开局库

基于连珠标准 26 开局体系，每局包含前 3 步（黑-白-黑）：

- **直止打法（13 种）**：白 2 落于天元正上方 GH(6,7)
- **斜止打法（13 种）**：白 2 落于天元右上对角 GI(6,8)

开局命名：寒星、溪月、疏星、花月、残月、雨月、金星、松月、丘月、新月、瑞星、山月、游星、长星、峡月、恒星、水月、流星、云月、浦月、岚月、银月、明星、斜月、名月、彗星

数据来源：[New-Renju joseki.json](https://github.com/yutokure/New-Renju/blob/master/joseki.json)

### AI 执行流程

```
用户落子 → ViewModel.handleUserMove()
  │
  ├─ 检查胜负 → 若游戏结束，停止
  ├─ 切换到对方回合
  └─ AIvAI / PvAI 模式 → triggerAiMove()
       │
       └─ LaunchedEffect 监听 isAiThinking → computeAiMove()
            │
            ├─ while 循环（支持连续 AI 回合，如 AIvAI 模式）
            │   ├─ 检查游戏是否已结束
            │   ├─ 在 Dispatchers.Default 上执行搜索
            │   │   ├─ 从 UI state 重建临时 GobangBoard
            │   │   ├─ searcher.search(board, turn, depth)
            │   │   │   └─ Negamax + Alpha-Beta（见上文）
            │   │   └─ 返回 SearchResult(score, row, col)
            │   ├─ 落子到棋盘
            │   └─ 若仍轮到 AI → 继续循环
            │
            └─ PvP 模式：不触发 AI
```

**难度与搜索深度：**

| 难度 | 搜索深度 | 特点 |
|------|----------|------|
| 简单 | 1 | 仅看一步，随机性强 |
| 中等 | 2 | 看两步，有一定策略 |
| 困难 | 3 | 看三步，较强但较慢 |

## 开源协议

MIT
