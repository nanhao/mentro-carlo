# intention-14 · Q-learning trace 数据生成 —— 算法实现与校验设计

> 需求来源：[intention.md](../../intention/intention.md) 的 intention-14。
> 基线要求：尽可能复用 Sarsa 的 trace 机制（格式定稿见 [design/12/README.md](../12/README.md)，下称「sarsa 设计」；其追溯的 MC 基线为 [design/3/README.md](../3/README.md)，下称「MC 设计」）。
> 范围：Q-learning 算法实现、trace 采集与导出、校验脚本。不含回放 GUI（后续 intention-15）与测试编写（用户约定）。

---

## 一、算法差异与关键决策

Q-learning 与 Sarsa 的唯一差异：**TD 目标取下一状态的最大 Q，而非策略实际选择的动作 Q**（off-policy 自举）：

```text
① TD 目标 = r + γ·max_a′ Q(s′,a′)    （a* = argmax_a′ Q(s′,a′)，与行为动作解耦）
② δ = TD 目标 − Q(s,a)
③ Q(s,a) ← Q(s,a) + α·δ
```

行为动作（ε-贪婪在 s′ 的选择）仍照常产生，仅用于 agent 下一步移动，不参与更新。

| # | 决策点 | 结论 | 理由 |
|---|---|---|---|
| 1 | 类结构 | **独立 `QLearning` 类**（montecarlo 包，方案 A），Sarsa 零改动 | 两算法主循环体已不同（max vs a′），独立类避免条件分支侵蚀 Sarsa；结构镜像 Sarsa 保持可读 |
| 2 | 行语义与行序 | 每行 = 一步在线 TD 更新，**执行正序**（文件序 = 回放序） | 更新粒度与 sarsa 相同；GUI 契约一致（intention-15 直接复用） |
| 3 | 行结构 | 与 sarsa 行**同形**：公共 12 字段 + `td{...}` + `qOld/qNew`，`version` 保持 2 | 无新增/删减字段；仅 `td` 两字段的语义按算法区分（§3.3） |
| 4 | `td.nextAction` | = **argmax 动作 a\***（确定性平局：按上→右→下→左遍历，严格 `>` 保留首个） | off-policy 的自举动作需可复算校验；固定遍历序保证平局确定性 |
| 5 | `td.nextQ` | = **max_a′ Q(s′,a′)**（更新前读值） | 与 ① 对应；终点行恒 0（Q(终点,·) 永不更新） |
| 6 | 终点行 | `nextAction` = `"up"`（Q(终点,·) 全 0 平局取首个）、`nextQ` = 0 | 全 0 平局按遍历序天然落在 up；GUI 显示 "—" 归 viewer（intention-15，按 toType==4） |
| 7 | 撞墙 s′==s | max/argmax 读的是**本步更新前**的表（含 Q(s,a) 旧值） | max 在 `qValues.put` 之前计算的标准语义；校验脚本按同口径回放 |
| 8 | 行为动作 | move 后 `policy.determineAction()` 保留，仅作下一步行为动作 | off-policy：行为与更新解耦；其结果不再进入 Q 更新的 StateActionPair |
| 9 | 算法标识 | `meta.algorithm = "qlearning"`（无连字符，与 montecarlo/sarsa 风格一致） | TraceFileWriter 按 algorithm 分段，天然支持 |
| 10 | 文件组织 | `data/trace/qlearning/map-<N>/run-<ts>/trace.json`；MC/sarsa 路径冻结不变 | 算法产物隔离；既有 viewer 发现规则不受影响 |
| 11 | 复用边界 | `Trace/EpisodeTrace/TraceMeta/TraceFileWriter/TraceJsonWriter/JsonAppender/TraceFactory` 零结构改动；`VisitRecord/TdUpdateRecord` 仅 javadoc 泛化 | qlearning 行 JSON 与 sarsa 行逐字段同形，序列化分派（`td != null`）天然支持 |
| 12 | 采集只读 | 同 sarsa 设计 #10：只读引用既有局部量；等价改写为提取 `maxNextQ/bestDirection/alphaUsed/tdTarget/delta/newQ` 局部变量 | 算法行为零变化 |

---

## 二、文件组织方式

### 2.1 目录树

```text
data/
└── trace/
    ├── map-1/                            # montecarlo（既有布局，冻结不动）
    │   └── run-<yyyyMMdd-HHmmss>/trace.json
    ├── sarsa/                            # sarsa（既有布局，冻结不动）
    │   └── map-1/
    │       └── run-<yyyyMMdd-HHmmss>/trace.json
    └── qlearning/                        # 算法段：meta.algorithm 值命名
        └── map-1/
            └── run-20260908-160000/
                └── trace.json
```

### 2.2 规则

- 命名规则同 sarsa 设计 §2.2：`map-<N>` 分组（源地图 `map/1/map.csv` → `map-1`）、`run-<yyyyMMdd-HHmmss>` 运行目录（同秒冲突追加 `-2`、`-3`…）、固定文件名 `trace.json`。
- `TraceFileWriter` **零改动**：`meta.algorithm` 非 `montecarlo` 即以该值作算法段，`qlearning` 自动落位。
- `.gitignore` 已含 `data/trace/`，运行产物自动排除入库。

---

## 三、trace 数据格式定稿

### 3.1 `meta`（无字段增量）

字段与语义完全沿用 sarsa 设计 §3.1：`version` 保持 **2**；`algorithm` 取值域扩展为 `"montecarlo" | "sarsa" | "qlearning"`；`alpha` 为初始 α，实际每行 α 见 `td.alpha`。

### 3.2 `episodes[]`（沿用）

`index / totalReward / endedBy`（`terminal` | `maxSteps`）语义与 sarsa 完全一致；visits 行序为执行正序。

### 3.3 qlearning `visits[]` 行字段字典与 td 语义区分

行结构、JSON 字段顺序与 sarsa 行**逐字段同形**（sarsa 设计 §3.3）：`no → x → y → action → reward → totalReward → nextX → nextY → fromType → toType → td{nextAction, nextQ, tdTarget, delta, alpha} → qOld → qNew`。公共字段（含奖励净额：撞墙 −2、炸弹 −51、苹果 +19、空/终点 −1）与 `qOld/qNew` 均与 sarsa 同义。

`td` 对象字段语义按算法区分（本设计的核心差异点）：

| 字段 | sarsa（design/12 §3.3） | qlearning（本文档） |
|---|---|---|
| `nextAction` | 策略在 s′ **实际选择**的 a′（on-policy 自举；终点行也如实记录） | s′ 上 **argmax_a′ Q(s′,a′) 的 a\***（平局按上→右→下→左取首个；终点行全 0 平局记 `"up"`） |
| `nextQ` | Q(s′,a′) | **max_a′ Q(s′,a′)**（更新前读值；终点行恒 0） |
| `tdTarget` | ① `reward + γ × nextQ`（自举 Q(s′,a′)） | ① `reward + γ × nextQ`（自举 max Q）——公式形式相同，nextQ 语义不同 |
| `delta` | ② `tdTarget − qOld` | 同左 |
| `alpha` | ③ 本行实际使用的学习率（DECAY_ALPHA 时为衰减后值） | 同左 |

### 3.4 与 montecarlo / sarsa 行字段对照

| 字段组 | montecarlo | sarsa | qlearning |
|---|---|---|---|
| 公共 10 字段（`no` ~ `toType`） | ✓ | ✓ | ✓ |
| `discountedNextG` / `G` / `sOld` / `sNew` | ✓ | — | — |
| `td{...}` | —（不输出） | ✓ | ✓（语义按 §3.3 区分） |
| `qOld` / `qNew` | ✓ | ✓ | ✓ |

### 3.5 规范示例（map/1，α=0.5、γ=0.9，Q 初始全 0；数值可手算复核）

路径取自原型 P1 示意（实际运行因探索随机而异，内容以真实运行为准）。

**第 1 回合第 1 步**（(0,0) up → (0,1) 空格；Q 全 0，max = 0、argmax 平局取 up）：

```jsonc
{
  "no": 1, "x": 0, "y": 0, "action": "up",
  "reward": -1, "totalReward": -1, "nextX": 0, "nextY": 1,
  "fromType": 0, "toType": 0,
  "td": {
    "nextAction": "up",        // max Q((0,1),·) = 0，四方向全 0 平局取首个
    "nextQ": 0.0,
    "tdTarget": -1.0,          // -1 + 0.9 × 0
    "delta": -1.0,             // -1.0 - 0
    "alpha": 0.5
  },
  "qOld": 0.0,
  "qNew": -0.5                 // 0 + 0.5 × (-1)
}
```

**第 2 回合重访 (0,0) up**（假设第 1 回合已把 Q((0,1),up) 更新为 −0.5、其余方向仍 0）：

```jsonc
{
  "no": 1, "x": 0, "y": 0, "action": "up",
  "reward": -1, "totalReward": -1, "nextX": 0, "nextY": 1,
  "fromType": 0, "toType": 0,
  "td": {
    "nextAction": "right",   // max Q((0,1),·) = 0（right/down/left 平局）；up = -0.5 非最大
    "nextQ": 0.0,
    "tdTarget": -1.0,        // -1 + 0.9 × 0
    "delta": -0.5,           // -1.0 - (-0.5)
    "alpha": 0.5
  },
  "qOld": -0.5,
  "qNew": -0.75              // -0.5 + 0.5 × (-0.5)
}
```

本例同时体现 off-policy：即便策略在 (0,1) 实际选了 up，更新自举的仍是 max（0），`td.nextAction` 记录的是 argmax 动作而非行为动作。

**撞墙行**（(0,0) down 越界 → 原地，净 −2；qOld 沿用上例 −0.75，(0,0) 其余方向仍 0）：

```jsonc
{
  "no": 2, "x": 0, "y": 0, "action": "down",
  "reward": -2, "totalReward": -3, "nextX": 0, "nextY": 0,
  "fromType": 0, "toType": 0,     // 原地：toType == fromType
  "td": {
    "nextAction": "up",      // s′==s：max 读本步更新前的表，Q((0,0),up/right/left) 平局 0
    "nextQ": 0.0,            // Q((0,0),down) 本步旧值 -0.75 参与比较但非最大
    "tdTarget": -2.0,        // -2 + 0.9 × 0
    "delta": -1.25,          // -2.0 - (-0.75)
    "alpha": 0.5
  },
  "qOld": -0.75,
  "qNew": -1.375             // -0.75 + 0.5 × (-1.25)
}
```

注：若 Q(s,a) 旧值恰为 s′（==s）四方向最大，max 会读到它自己——max 在 `qValues.put` 之前计算，读到的是含 Q(s,a) 旧值在内的更新前表，标准 off-policy 自举语义。

**终点行**（第 1 回合最后一步 (4,1) down → (4,0) 终点；agent 前一步已吃掉 (4,1) 苹果，故 fromType=0；P1 全程 12 步累计 +8）：

```jsonc
{
  "no": 12, "x": 4, "y": 1, "action": "down",
  "reward": -1, "totalReward": 8, "nextX": 4, "nextY": 0,
  "fromType": 0, "toType": 4,
  "td": {
    "nextAction": "up",      // Q(终点,·) 永不更新、全 0 平局，argmax 按遍历序落在 up（GUI 显示 —）
    "nextQ": 0.0,            // max Q(终点,·) = 0
    "tdTarget": -1.0,
    "delta": -1.0,           // 第一回合 qOld = 0
    "alpha": 0.5
  },
  "qOld": 0.0,
  "qNew": -0.5
}
```

---

## 四、Java 采集点映射（`QLearning.buildQValues()`）

结构**镜像 Sarsa**：`collectTrace` 开关与 `trace` 字段（`@Getter/@Setter`）、`TraceFactory.createEmptyTrace(..., TraceMeta.ALGORITHM_QLEARNING)`、每回合正序收集 `List<VisitRecord>` 后构建 `EpisodeTrace`、回合末 `policy.setQValues(qValues)` 刷新。主循环差异仅在更新表达式与 td 采集值：

```java
tempWorld.move(direction);
int reward = tempWorld.getLastReward();
// off-policy 自举：对下一状态 s′ 四方向查表取 max 与 argmax（Q 表已全初始化，无 null）；
// 严格 > 保留首个，天然实现上→右→下→左平局规则；
// 必须在 qValues.put 之前计算：撞墙 s′==s 时读到本步更新前的表（含 Q(s,a) 旧值，§3.5）
Cell nextCell = tempWorld.getCurrentCell();
double maxNextQ = Double.NEGATIVE_INFINITY;
int bestDirection = Constants.DIRECTION_UP;
for (int d = 0; d < Constants.DIRECTION_COUNT; d++) {
    double candidate = qValues.get(new StateActionPair(nextCell, d));
    if (candidate > maxNextQ) {
        maxNextQ = candidate;
        bestDirection = d;
    }
}
// 等价改写：提取本行实际使用的 α 与更新结果（值与求值顺序不变），供更新与 trace 采集共用
double alphaUsed = qAlphas.get(stateActionPair);
double tdTarget = reward + gamma * maxNextQ;
double delta = tdTarget - q;
double newQ = q + alphaUsed * delta;
qValues.put(stateActionPair, newQ);
...
// off-policy：行为策略选下一步动作，仅用于移动，不参与本步更新
direction = policy.determineAction();
```

| trace 字段 | 来源 |
|---|---|
| 公共 12 字段 + `qOld/qNew` | 与 sarsa 设计 §四映射完全一致（fromCell 快照 / tempWorld 既有调用 / `VisitRecord.actionName`） |
| `td.nextAction` | `VisitRecord.actionName(bestDirection)`（argmax 动作，非行为动作） |
| `td.nextQ` | `maxNextQ`（argmax 循环同轮求出） |
| `td.tdTarget` | `reward + gamma * maxNextQ` |
| `td.delta` | `tdTarget - q` |
| `td.alpha` | `qAlphas.get(stateActionPair)`（更新前取值，即 `alphaUsed`） |

采集只读不改：`determineAction()` 在 move 后照常调用（行为动作），其结果不再进入 Q 更新；所有字段取自既有局部量与格子快照。

---

## 五、序列化与导出（零改动）

- `TraceJsonWriter.appendVisit` 按 `getTd() != null` 分派：qlearning 行与 sarsa 行同走 `td` 嵌套分支，输出逐字段同构（仅字段语义按 §3.3 区分）；
- `TraceFileWriter`：`algorithm == "qlearning"` 自动落 `data/trace/qlearning/` 算法段（§二）；
- MC / sarsa 输出不受任何影响（无代码路径变化）。

---

## 六、校验链（`temp/verify_qlearning_trace.py`，仿 sarsa 数值链脚本）

1. **meta**：`meta.algorithm == "qlearning"`、`version == 2`、`width/height` 与 `initialMap` 一致；`episodes[].index` 从 1 连续递增且数量 == `maxEpisode`；
2. **数值链**：`td.tdTarget == reward + γ × td.nextQ`、`td.delta == tdTarget − qOld`、`qNew == qOld + td.alpha × td.delta`（均 ±1e-9）；
3. **argmax/max 复算**：按文件序逐行回放重建 Q 表（初值全 0），每行应用前：
   - `td.nextQ == max_a′ Q_重建(s′,a′)`（±1e-9）；
   - `td.nextAction == argmax`（按上→右→下→左严格 `>` 取首个，与 Java 平局规则一致）；
   - 应用 `Q_重建(x,y,action) ← qNew`；
   - 撞墙行 `s′ == s`：max 读的是含 Q(s,a) 本步旧值的更新前表，与 Java 实现同口径；
4. **位置/类型衔接链**：同 sarsa 设计 §七.4——相邻行上行 `(nextX,nextY) ==` 下行 `(x,y)`、上行 `toType ==` 下行 `fromType`；每 episode 首行 `x/y == (startX,startY)`；`reward ∈ {+19, −51}` 的行 `toType == 0`；`reward == −2` 的行原地且 `toType == fromType`；末行 `toType == 4` ↔ `endedBy == "terminal"`；
5. **终点行**：`toType == 4` 的行 `td.nextQ == 0`；
6. **on-policy 链不适用**：sarsa 校验中的 `td.nextAction(k) == action(k+1)` 对 qlearning **不成立**——`td.nextAction` 是 argmax 而非行为动作，与下一步实际执行的 `action` 无必然关系；脚本不检查，GUI 消费时亦不可误用。

---

## 七、实现任务分解与验收

| 步骤 | 内容 |
|---|---|
| S1 | 本设计文档 |
| S2 | history 包最小改动：`TraceMeta` +`ALGORITHM_QLEARNING`；`TdUpdateRecord/VisitRecord` javadoc 泛化；`TraceFactory` javadoc 提及 QLearning |
| S3 | `com.metrocarlo.montecarlo.QLearning`：镜像 Sarsa 结构 + §四差异点 |
| S4 | `Main` +`runQLearningOnOneMap`（map/1 + EpsilonGreedyPolicy）→ `data/trace/qlearning/map-1/run-*/trace.json` |
| S5 | 验证：`mvn test` 既有用例全绿；运行 Main 导出 trace；`temp/verify_qlearning_trace.py` 全链校验；确认 sarsa/montecarlo 侧零改动 |

验收要点（本任务不新增测试，用户约定）：

- `mvn test` 既有用例全部通过；
- 运行 Main 产出 qlearning trace：`meta.algorithm=="qlearning"`、`version==2`、行结构符合 §3.3、数值满足 §六全部校验链；
- sarsa / montecarlo 侧零行为变化：既有 trace 数据与 viewer 不受影响。

---

## 八、本文档与相关文件的关系

| 文件 | 关系 |
|---|---|
| `intention/intention.md` | intention-14 为本次需求来源 |
| `design/12/README.md` | sarsa 基线：本文档复用其行结构（§3.3）、文件组织（§二）、衔接链（§七.4）与采集模式（§四） |
| `design/3/README.md` | MC trace 格式基线（sarsa 设计的上游） |
| `src/main/java/com/metrocarlo/history/` | `TraceMeta/VisitRecord/TdUpdateRecord` javadoc 泛化；其余零改动 |
| `src/main/java/com/metrocarlo/montecarlo/QLearning.java` | 本设计新增：采集宿主 |
| `temp/verify_qlearning_trace.py` | §六校验链的实现（临时脚本，不入库） |
