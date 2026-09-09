# Android 本地五子棋 AI 推理引擎 PRD

版本：V1.0  
状态：可开发  
目标平台：Android 8.0+，优先 ARM64 设备  
棋盘：15×15，标准无禁手五子棋

## 1. 产品目标

将 `Nagi-ovo/alphazero-gomoku` 的训练成果转化为无需联网、可在 Android 手机上实时运行的五子棋 AI。移动端采用混合引擎：

```text
Tactical Solver → Candidate Pruning → AlphaZero MCTS → Policy/Value NN
```

目标是：规则正确、模型输出一致、思考时间可控、低端设备可用，并保留未来重训轻量 Mobile ResNet 的演进空间。

### 非目标

- APK 内不运行 Python、PyTorch 训练器、自对弈、优化器或 wandb。
- V0 不追求与桌面版 400/2000 simulation 完全等强度。
- V0 不支持联网对战、云端推理、棋谱社交、禁手规则和多棋盘尺寸。
- V0 不在没有评测数据的情况下宣称棋力等级。

## 2. 规则与模型假设

- 棋盘固定 15×15，共 225 个交叉点。
- 黑方先手；双方轮流落子；横、竖、斜线连续五子即胜。
- 无禁手：三三、四四、长连均不额外判负；先形成五子者胜。
- 棋盘编码：空位 `0`，当前玩家 `1`，对手 `-1`。
- 模型输入使用当前玩家视角的 canonical board；交换执棋方后，局面应满足 `canonical(-board) == -canonical(board)`。
- Action 映射必须固定：`action = row * 15 + col`，`row = action / 15`，`col = action % 15`。
- Policy 输出长度固定为 225；非法落子必须在引擎层 mask 掉。
- Value 约定为当前 canonical 玩家视角：胜为 `+1`，负为 `-1`，和棋为 `0`；MCTS 回传时每经过一层取反。

## 3. 产品形态与难度

UI 只负责棋盘、落子、撤销、重新开始、难度选择和思考状态。AI 线程不得阻塞 UI。

| 难度 | simulations | 时间上限 | 候选数 | 说明 |
|---|---:|---:|---:|---|
| 简单 | 32 | 0.5 s | 12 | Tactical + 浅层 MCTS |
| 普通 | 96 | 1.5 s | 20 | MVP 默认 |
| 困难 | 256 | 3 s | 32 | 适配中高端设备 |
| 专家 | 512 | 5 s | 48 | 后续优化项，可动态降级 |

配置以先达到 simulation 或先达到 deadline 为准；每次落子记录实际耗时和 simulations。

## 4. 整体架构

```text
Kotlin UI / Game Session
        │ JNI/NDK
        ▼
C++ Engine Facade
  ├─ Board + Rules
  ├─ Tactical Solver
  ├─ Candidate Generator / Pruner
  ├─ MCTS + PUCT + Tree Reuse
  └─ NN Adapter
       └─ ONNX Runtime C++ / CPU → NNAPI/GPU 后续
```

训练端与推理端边界：

```text
Python/PyTorch：训练、评估、checkpoint、导出、校验数据生成
Android/C++：规则、战术搜索、候选裁剪、MCTS、模型加载、性能控制
Kotlin：生命周期、渲染、交互、难度和取消任务
```

推荐 Kotlin + JNI + C++17 + ONNX Runtime Mobile。模型和搜索热路径放在 C++，避免 Kotlin 对象分配、JNI 往返和 GC；Kotlin 只调用 `think()` 一次并接收结果。

## 5. Board 与 Canonical Form

```cpp
using Action = uint16_t; // 0..224

struct Board {
  int8_t cell[225];
  uint16_t occupied;
  int8_t player; // 1 or -1
  bool play(Action a);
  void undo(Action a);
  bool isWinAfter(Action a) const;
  bool isTerminal() const;
  void legalMoves(std::vector<Action>& out) const;
  void toCanonical(float out[225]) const;
};
```

`toCanonical()` 输出：若 `player == 1`，原样输出；若 `player == -1`，每格乘 `-1`。MCTS 的 state key 必须包含 canonical board；建议使用 64-bit Zobrist hash，并将 player 纳入 hash。不要以字符串或每节点复制完整 Python 风格数组作为线上实现。

规则层必须提供 `make/unmake`，并在局部方向上检查胜负，避免每次扫描整盘。

## 6. 混合引擎决策顺序

每次 AI 落子按以下顺序执行：

1. 检查当前局面是否已有终局。
2. `Immediate Win`：若我方存在一步成五，直接落子。
3. `Forced Block`：若对手下一步存在一步成五，直接封堵；多个封堵点进入后续 MCTS。
4. 生成邻域候选点，并进行合法性、距离、局部威胁、policy 先验排序。
5. `VCF/VCT` tactical solver：在有限深度和节点预算内搜索连续冲四/双威胁；找到可验证强制线则直接返回。
6. 剩余局面进入 AlphaZero MCTS。
7. 复用上一步已选 action 对应的子树；没有匹配时新建 root。

### 候选生成与剪枝

- 空棋盘固定优先中心点 `(7,7)`。
- 非空棋盘只考虑距离已有棋子 Chebyshev 距离 ≤2 的空点；若候选不足，逐圈扩展到 ≤3。
- 保留我方成五、冲四、活三、双威胁和对手同类威胁相关点。
- 最终按 `tacticalScore + λ * policyPrior` 排序，保留难度配置中的候选数。
- 任何剪枝都不得移除 Immediate Win 或唯一 Forced Block。
- 提供 debug 开关输出被剪掉的原因，便于发现棋力回退。

### Tactical Solver

V0 实现 Immediate Win、Forced Block、局部活三/冲四识别；V1 增加 VCF（Victory by Continuous Four）和 VCT（Victory by Continuous Threat）。战术搜索采用 alpha-beta/DFS、make/unmake、深度上限、节点上限和 deadline；超时返回 `UNKNOWN`，不能把未证明线路当成胜利。

## 7. MCTS / PUCT

```cpp
struct Node {
  uint64_t key;
  float prior;
  float valueSum;
  uint32_t visitCount;
  Action actionFromParent;
  Node* parent;
  SmallVector<Child, 48> children;
};
```

PUCT：

```text
Q = valueSum / max(1, visitCount)
U = cpuct * prior * sqrt(parent.visitCount) / (1 + visitCount)
score = Q + U
```

叶节点流程：选择 → make move → 终局判断 → NN predict → 合法 mask → 扩展 → value 回传并逐层取反。MVP 可单线程、单 batch；V2 再加入批量叶节点推理。

### Tree Reuse

- root 保存于当前对局 session。
- AI 落子后，将选中 child 提升为新 root，断开其 parent。
- 用户落子后，在当前 root children 中查找对应 action；命中则继续复用，否则丢弃旧树。
- 棋盘尺寸、模型版本、规则版本变化时必须清树。
- 树节点设上限；超限按最少访问数或最老分支回收，优先保留 root 及高访问子树。

## 8. Policy / Value NN 接口

```cpp
struct Prediction { float policy[225]; float value; };
Prediction predict(const float canonicalBoard[225]);
```

输入布局、dtype、归一化、输出顺序必须写入 model manifest：

```json
{"board_size":15,"action_order":"row-major","input":"float32[1,1,15,15]","policy":"float32[1,225]","value":"float32[1,1]","value_perspective":"current_player","model_version":"..."}
```

Android 端加载模型后校验 manifest、输入输出 shape 和模型 hash。NN Adapter 不应知道 MCTS；模型推理异常时返回明确错误，由上层执行安全降级。

## 9. ONNX 导出与一致性验证

训练端固定 `model.eval()`、关闭 dropout、固定随机种子，并导出 `opset` 版本。导出流水线：

```text
best.pth.tar → PyTorch eval → ONNX → ONNX Runtime CPU → Android ORT
```

必须生成 golden cases，覆盖：空盘、单子、双方视角、满盘、终局、边角和随机合法局面。每个 case 保存输入、PyTorch policy/value、合法 mask 后结果和选定 action。

验收阈值：

- PyTorch 与 ONNX 原始输出：`max_abs_error ≤ 1e-4`（FP32）。
- Android FP32 与桌面 ORT：`max_abs_error ≤ 1e-4`。
- 允许量化模型按校准集设 `policy cosine similarity ≥ 0.999`、`value MAE ≤ 0.02`，并以对局回归为最终依据。
- 相同 seed、相同 simulation、相同候选列表时，首选 action 必须一致；若浮点 tie，按 action 升序稳定打破。

## 10. 性能、线程、内存与功耗

- UI 主线程只处理渲染和事件。
- 每个 AI 请求使用一个可取消的 worker；新请求到达时取消旧请求。
- C++ engine session 复用 ORT environment、session、allocator 和搜索缓冲区。
- 禁止 search 热路径反复 `new/delete`；使用 arena、对象池或预分配 vector。
- MVP 目标：普通难度中端 ARM64 设备 ≤1.5 秒/步，P95 ≤2 秒；首步 ≤2.5 秒。
- NN 单次推理 P95 ≤8 ms；若不达标先降低候选数和 simulation，再启用硬件加速。
- 单局引擎额外内存目标 ≤64 MB（模型除外）；功耗策略为 deadline、后台降频、低电量自动降低难度。
- 连续思考超过 5 秒必须提供取消路径，Activity/Compose 生命周期销毁时释放 native handle。

## 11. 量化与硬件加速路线

1. MVP：FP32 ONNX + CPU，建立正确性基线。
2. V1：FP16 权重/执行（设备支持时），比较误差与耗时。
3. V2：静态 INT8，使用真实棋局校准集，优先量化卷积和全连接，保留敏感算子 FP32。
4. V3：按设备能力选择 CPU、NNAPI 或 GPU EP；启动时 benchmark，失败自动回退 CPU。

不能把硬件 EP 可用性当作功能前提。模型文件、EP、线程数均纳入设备 profile 和崩溃日志。

## 12. MVP 与 Mobile ResNet 重训

### MVP：复用现有模型

- 保持原模型输入/输出语义。
- C++ 重写 Board、MCTS、候选生成和战术层。
- FP32 ONNX CPU；32/96/256 simulations。
- 目标是完成端到端本地对弈和一致性验证。

### 后续：Mobile ResNet 重训

- 设计轻量 residual tower，减少通道数和 block 数。
- 训练数据保留原有 policy/value 目标，增加移动端候选分布和战术局面采样。
- 以蒸馏或 self-play 对齐原模型，在同等时间预算下比较棋力、延迟和功耗。
- Mobile 模型必须通过同一 manifest、golden case、对局回归和设备 benchmark 门禁。

## 13. 工程目录

```text
gomoku-ai/
├─ training/
│  ├─ export_onnx.py
│  ├─ validate_onnx.py
│  ├─ generate_golden.py
│  └─ model_manifest.json
├─ android/app/src/main/
│  ├─ assets/models/model.onnx
│  ├─ java/.../AiEngine.kt
│  └─ cpp/
│     ├─ board.{h,cc}
│     ├─ rules.{h,cc}
│     ├─ tactical_solver.{h,cc}
│     ├─ candidates.{h,cc}
│     ├─ mcts.{h,cc}
│     ├─ nn_ort.{h,cc}
│     ├─ engine.{h,cc}
│     └─ jni_bridge.cc
├─ testdata/golden/
├─ tests/python/
├─ tests/native/
└─ docs/benchmark.md
```

## 14. 关键接口与伪代码

```kotlin
interface AiEngine : Closeable {
  fun think(position: IntArray, player: Int, config: SearchConfig,
            callback: (SearchResult) -> Unit): CancelHandle
}
```

```cpp
SearchResult Engine::think(Board& b, SearchConfig cfg) {
  if (auto a = tactical.immediateWin(b)) return direct(a, "WIN");
  auto blocks = tactical.forcedBlocks(b);
  auto candidates = pruner.generate(b, nn.prior(b), blocks);
  if (auto a = tactical.vcf_vct(b, cfg.tacticalBudget)) return direct(a, "TACTICAL");
  root = reuseOrCreate(b, candidates);
  while (!deadline.expired() && root.visits < cfg.simulations)
    mcts.search(root, b, nn, deadline);
  return bestChild(root);
}
```

## 15. 测试计划

- 规则单测：落子、撤销、边界、四方向胜负、满盘和棋、canonical 对称性、action 映射。
- MCTS 单测：终局 value、取反回传、非法 policy mask、PUCT 排序、tree reuse 命中/失效。
- Tactical 单测：一步成五、唯一封堵、双威胁、VCF/VCT 成功和超时 UNKNOWN。
- 三层 golden：PyTorch → 桌面 ONNX Runtime → Android ONNX Runtime。
- 属性测试：随机合法局面 make/unmake 后 hash 和棋盘完全恢复。
- 对局回归：旧引擎/新引擎、FP32/量化、不同候选策略的固定 seed 对局。
- Android 测试：旋转、后台、取消、低内存、低电量、无硬件 EP、模型加载失败。

## 16. Benchmark / KPI

每个设备记录：设备型号、SoC、Android 版本、模型 hash、EP、线程数、候选数、simulation、总耗时、NN 调用次数、节点数、峰值内存、温度/电量变化。

发布门槛：

- 规则和 golden 测试 100% 通过。
- 1000 个随机局面无 crash、无非法落子。
- 普通难度 P95 ≤2 秒，单步内存 ≤64 MB（模型除外）。
- 量化版本不得引入明显战术漏防；固定战术集正确率 100%。
- 连续 20 分钟对局无明显失控、ANR 或 native 泄漏。

## 17. 里程碑

### V0：可运行正确性基线

完成 Board/Rules、JNI、FP32 ONNX、基础 MCTS、Immediate Win/Forced Block、golden pipeline、简单 UI 对弈。

### V1：MVP 可玩

完成候选剪枝、Tree Reuse、96/256 难度、取消和生命周期、设备 benchmark、回归测试和发布包。

### V2：移动端搜索优化

完成 VCF/VCT、对象池、批量叶节点推理、动态预算、FP16/线程调优。

### V3：设备适配与量化

完成 INT8 校准、NNAPI/GPU 可选 EP、设备 profile、自动回退、功耗策略。

### V4：Mobile ResNet

完成轻量模型重训/蒸馏、棋力对比、模型热切换、同一验证门禁下的正式移动版模型。

## 18. 风险与降级方案

| 风险 | 影响 | 降级 |
|---|---|---|
| ONNX 输出不一致 | 选点错误 | 回退 FP32，锁定 opset，扩大 golden 集 |
| Android 推理过慢 | 思考卡顿 | 减少 simulations/候选数，CPU 线程调优 |
| 候选剪枝漏掉关键点 | 棋力下降 | 保留所有战术点，扩大邻域或关闭剪枝 |
| VCF/VCT 超时 | 延迟不可控 | 返回 UNKNOWN，交给 MCTS |
| Tree reuse 状态错配 | 搜索错误 | hash + full-board debug 校验，失配即清树 |
| EP/量化不稳定 | 崩溃或棋力回退 | 启动自检，自动回退 FP32 CPU |
| JNI 生命周期泄漏 | 崩溃 | RAII、close、destroy handle、压力测试 |
| 现有模型移动端棋力不足 | 产品体验差 | 增加战术层；V4 重训 Mobile ResNet |

## 19. 明确开发任务清单

- [ ] 固化规则、action order、canonical、value perspective 和 manifest。
- [ ] 从 PyTorch checkpoint 导出 ONNX，生成并提交 golden cases。
- [ ] 实现 Board make/unmake、局部胜负、Zobrist hash。
- [ ] 实现 Immediate Win、Forced Block、局部威胁评分。
- [ ] 实现候选生成、不可剪枝战术点保护和 debug 原因码。
- [ ] 实现 V0 MCTS/PUCT、合法 mask、value 取反和稳定 tie-break。
- [ ] 实现 Tree Reuse、节点上限和 arena/object pool。
- [ ] 集成 ONNX Runtime C++，完成模型 manifest/hash/shape 校验。
- [ ] 实现 JNI `create/think/cancel/destroy` 和 Kotlin 生命周期绑定。
- [ ] 完成 PyTorch/桌面 ORT/Android ORT 三层一致性测试。
- [ ] 完成 3 类以上 ARM64 设备 benchmark 和普通难度门禁。
- [ ] 加入 VCF/VCT、批量推理、FP16、INT8、NNAPI/GPU 的分阶段实验。
- [ ] 建立固定 seed 对局回归和战术集，作为每次模型/搜索变更的发布门禁。

## 20. 最终验收标准

用户可以在 Android 设备上离线开始一局 15×15 五子棋；AI 能正确处理所有规则和战术必应点；模型加载、搜索、取消、重启和生命周期切换稳定；普通难度达到 1.5 秒目标预算；PyTorch、桌面 ONNX 和 Android ONNX 对 golden cases 一致；任意硬件加速或量化失败时能自动回退；工程目录、测试、benchmark 和任务清单足以支持团队直接进入开发。
