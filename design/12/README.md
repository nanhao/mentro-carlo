# intention-12 · Sarsa trace 数据生成 —— 格式定稿与采集设计

> 需求来源：[intention.md](../../intention/intention.md) 的 intention-12（63~65 行）；原型：[requirements/10/prototype.html](../../requirements/10/prototype.html)（intention-10 SARSA 回放可视化）。
> 基线要求：尽可能复用 Monte Carlo 的 trace 机制（格式定稿见 [design/3/README.md](../3/README.md)，下称「MC 设计」）。
> 范围：sarsa trace 的格式定稿、文件组织、Java 采集与导出、GUI 消费契约。不含 GUI 实现（后续任务）与测试编写（用户约定）。

---

## 一、算法差异与关键决策

Sarsa 与 Monte Carlo 的本质差异：**在线 TD 更新**——走一步更新一次，无回合末结算：

```text
① TD 目标 = r + γ·Q(s′,a′)      （a′ 为策略在 s′ 实际选择的下一动作，on-policy 自举）
② δ = TD 目标 − Q(s,a)
③ Q(s,a) ← Q(s,a) + α·δ
```

Sarsa 只有 Q 表：无 G、无 stateValues（原型中心 V̂(s) = max_a Q(s,a) 为 GUI 派生量）。

| # | 决策点 | 结论 | 理由 |
|---|---|---|---|
| 1 | 行语义 | 每行 = **一步在线 TD 更新**（非 MC 的"状态访问回合末结算"） | 与算法更新粒度一致；原型过程面板即逐行展示 TD 更新 |
| 2 | visits 行序 | **执行正序**（`no` 升序 = 文件序），GUI 直接顺序播放 | 在线更新天然正序；MC 倒序源于结算循环，Sarsa 无此环节；GUI 免按 `no` 重排 |
| 3 | 字段增删 | 去掉 `discountedNextG/G/sOld/sNew`；保留公共 12 字段与 `qOld/qNew` | 无 G 与 stateValue；中心 V̂ 由 GUI 派生，不落 trace |
| 4 | TD 明细 | 新增**嵌套 `td` 对象** `{nextAction, nextQ, tdTarget, delta, alpha}`（新类 `history.TdUpdateRecord`） | 与原型公式条 ①②③ 一一对应；嵌套对象把"算法专属扩展"模式化——MC 行不含 `td`，JSON 字段缺失即非 TD 算法 |
| 5 | 行内 alpha | `td.alpha` = 该行实际使用的学习率（`qAlphas.get(pair)` 更新前取值） | Sarsa 的 Q 更新用 `qAlphas.get(pair)`（`DECAY_ALPHA` 开启时按 (s,a) 衰减），`meta.alpha` 只是初始值；一致性校验与"α 衰减"展示需要行内值 |
| 6 | 终点行 a′ | **如实记录**策略在终点格实际选择的动作（Java 循环在最后一步确实执行了 `determineAction`，且 Q(终点,·) 恒 0）；GUI 按 `toType==4` 显示 "—" | 数据保真，显示规则归 GUI（与 MC 设计 §4.4 note 直判同一原则） |
| 7 | 复用边界 | `Trace / EpisodeTrace / TraceMeta / TraceFileWriter / TraceJsonWriter / JsonAppender` 全部复用；`VisitRecord` 增可空 `td` 字段 | 共享主干算法无关；避免平行数据类与重复序列化代码 |
| 8 | 算法标识 | `meta` 新增 `algorithm`（`"montecarlo"` \| `"sarsa"`），`version` 1→2；旧文件无 `algorithm` 视为 montecarlo | 自描述：GUI 文件对话框加载任意 trace 时可分发；v2 的 MC 行与 v1 逐字段一致（仅 meta 多 `algorithm`），向后兼容 |
| 9 | 文件组织 | sarsa 写 `data/trace/sarsa/map-<N>/run-<ts>/trace.json`；**MC 路径冻结不变** | 算法产物隔离：既有 MC viewer 的发现规则（`data/trace/map-*/run-*/trace.json`）与已验收数据不受影响 |
| 10 | 采集只读 | 全部字段取自循环既有局部量与格子快照；唯一等价改写：Q 更新表达式提取局部变量 `newQ`（与 `alphaUsed`） | 算法行为零变化（同 MC 设计 §五验收点） |

---

## 二、文件组织方式

### 2.1 目录树

```text
data/
└── trace/
    ├── map-1/                            # montecarlo（既有布局，冻结不动）
    │   └── run-<yyyyMMdd-HHmmss>/trace.json
    └── sarsa/                            # 算法段：非 montecarlo 算法以 meta.algorithm 值命名
        └── map-1/
            └── run-20260908-130000/
                └── trace.json
```

### 2.2 规则

- 命名规则沿用 MC 设计 §2.2：`map-<N>` 分组（源地图 `map/1/map.csv` → `map-1`）、`run-<yyyyMMdd-HHmmss>` 运行目录（同秒冲突追加 `-2`、`-3`…）、固定文件名 `trace.json`。
- `TraceFileWriter` 按 `meta.algorithm` 决定算法段：`montecarlo` **省略**（兼容既有布局与 viewer 发现规则），其余算法取 `algorithm` 值作段名。
- `.gitignore` 已含 `data/trace/`，sarsa 运行产物自动排除入库。

---

## 三、trace 数据格式定稿

### 3.1 `meta`（增量）

| 字段 | 变化 | 说明 |
|---|---|---|
| `version` | 1 → **2** | v2 = 新增 `algorithm`；visits 行字段集按 `algorithm` 区分 |
| `algorithm` | **新增** | `"montecarlo"`（默认）\| `"sarsa"`；位于 `version` 之后 |

其余字段全部沿用 MC 设计 §3.1（`alpha/gamma/epsilon/maxEpisode/maxStepsPerEpisode` 语义不变；`alpha` 为初始 α，实际每行 α 见 `td.alpha`）。

### 3.2 `episodes[]`（沿用）

`index / totalReward / endedBy`（`terminal` \| `maxSteps`）语义与 MC 完全一致。唯一差异：**visits 行序按算法**——montecarlo 为结算倒序，sarsa 为执行正序（`EpisodeTrace` javadoc 已同步说明）。

### 3.3 sarsa `visits[]` 行字段字典

每行 = 一步在线 TD 更新（执行序第 `no` 步：在 (x,y) 选 a → 移动到 s′ 得 r → 策略在 s′ 选 a′ → Q(s,a) 更新）。

| 字段 | 类型 | 说明 |
|---|---|---|
| `no` | int | episode 内正序步号（1 起）；**文件序 = 执行序** |
| `x` / `y` | int | 动作前所在格（World 坐标系） |
| `action` | string | `up \| right \| down \| left` |
| `reward` | int | 即时奖励净额（撞墙/越界 = −2、炸弹 = −51、苹果 = +19、空/终点 = −1；与现 `Constants` 一致） |
| `totalReward` | int | 该步后的累计奖励 |
| `nextX` / `nextY` | int | 到达格（撞墙/越界时 == x/y 原地） |
| `fromType` | int | 移动前所在格类型（该步开始时状态，含此前各步消耗结果） |
| `toType` | int | 移动后所在格类型（道具被本步消耗后已为 0；进入终点为 4；原地 == `fromType`） |
| `td` | object | **仅 sarsa 行存在**：本步 TD 更新明细（见下） |
| `qOld` | double | 该 (状态, 动作) 的 Q 更新前取值 |
| `qNew` | double | Q 更新后取值 = `qOld + td.alpha × td.delta` |

**`td` 对象字段**（与原型公式条 ①②③ 一一对应）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `nextAction` | string | 策略在 s′ 实际选择的 a′（`up\|right\|down\|left`；终点行也如实记录，GUI 显示 —） |
| `nextQ` | double | Q(s′,a′)，更新前读值（终点行恒 0；撞墙且 a′==a 时读到的是尚未更新的旧值 == `qOld`，如实记录） |
| `tdTarget` | double | ① TD 目标 = `reward + γ × nextQ` |
| `delta` | double | ② δ = `tdTarget − qOld` |
| `alpha` | double | ③ 本行实际使用的学习率（`DECAY_ALPHA` 开启时随该 (s,a) 衰减后的值） |

JSON 字段顺序：`no → x → y → action → reward → totalReward → nextX → nextY → fromType → toType → td{nextAction, nextQ, tdTarget, delta, alpha} → qOld → qNew`。

MC 行不输出 `td`（其后仍是 `discountedNextG/G/sOld/sNew → qOld/qNew`，与 v1 逐字节一致）。

### 3.4 与 montecarlo 行字段对照

| 字段组 | montecarlo | sarsa |
|---|---|---|
| 公共 10 字段（`no` ~ `toType`） | ✓ | ✓ |
| `discountedNextG` / `G` | ✓ | —（无 G） |
| `sOld` / `sNew` | ✓ | —（无 stateValue，V̂ 由 GUI 派生） |
| `qOld` / `qNew` | ✓ | ✓ |
| `td{...}` | —（不输出） | ✓ |

### 3.5 规范示例（map/1，路径取自原型 P1；内容以真实运行为准）

α=0.5、γ=0.9、Q 初始全 0。以下数值可手算复核，供回放校验对照。

**第 1 回合第 1 步**（(0,0) up → (0,1) 空格；a′ 假设为 up）：

```jsonc
{
  "no": 1, "x": 0, "y": 0, "action": "up",
  "reward": -1, "totalReward": -1, "nextX": 0, "nextY": 1,
  "fromType": 0, "toType": 0,
  "td": {
    "nextAction": "up",
    "nextQ": 0.0,          // Q((0,1),up) 初始为 0
    "tdTarget": -1.0,      // -1 + 0.9 × 0
    "delta": -1.0,         // -1.0 - 0
    "alpha": 0.5
  },
  "qOld": 0.0,
  "qNew": -0.5             // 0 + 0.5 × (-1)
}
```

**第 2 回合重访 (0,0) up**（`qOld` 沿用第 1 回合更新结果，`nextQ` 为 Q((0,1),up) 更新后的 −0.5）：

```jsonc
{
  "no": 1, "x": 0, "y": 0, "action": "up",
  "reward": -1, "totalReward": -1, "nextX": 0, "nextY": 1,
  "fromType": 0, "toType": 0,
  "td": {
    "nextAction": "up",
    "nextQ": -0.5,
    "tdTarget": -1.45,     // -1 + 0.9 × (-0.5)
    "delta": -0.95,        // -1.45 - (-0.5)
    "alpha": 0.5
  },
  "qOld": -0.5,
  "qNew": -0.975           // -0.5 + 0.5 × (-0.95)
}
```

**撞墙行**（(0,0) down 越界/撞墙 → 原地，净 −2；假设 a′ 恰为 down，s′==s 且 a′==a）：

```jsonc
{
  "no": 2, "x": 0, "y": 0, "action": "down",
  "reward": -2, "totalReward": -3, "nextX": 0, "nextY": 0,
  "fromType": 0, "toType": 0,     // 原地：toType == fromType
  "td": {
    "nextAction": "down",
    "nextQ": -0.975,       // 与 (s,a) 同键：读到尚未更新的旧值（== qOld）
    "tdTarget": -2.8775,   // -2 + 0.9 × (-0.975)
    "delta": -1.9025,      // -2.8775 - (-0.975)
    "alpha": 0.5
  },
  "qOld": -0.975,
  "qNew": -1.92625         // -0.975 + 0.5 × (-1.9025)
}
```

**终点行**（第 1 回合最后一步 (4,1) down → (4,0) 终点；P1 全程 12 步累计 +8）：

```jsonc
{
  "no": 12, "x": 4, "y": 1, "action": "down",
  "reward": -1, "totalReward": 8, "nextX": 4, "nextY": 0,
  "fromType": 0, "toType": 4,
  "td": {
    "nextAction": "right",  // 策略在终点格的"实际选择"（Q 全 0，语义上无后继动作；GUI 显示 —）
    "nextQ": 0.0,           // Q(终点, ·) 永不更新，恒 0
    "tdTarget": -1.0,
    "delta": -1.0,          // 第一回合 qOld = 0
    "alpha": 0.5
  },
  "qOld": 0.0,
  "qNew": -0.5
}
```

---

## 四、Java 采集点映射（`Sarsa.buildQValues()`）

采集**只读不改**：所有字段取自循环既有局部量与格子快照，不触碰 qValues/qAlphas 内容与循环顺序。唯一等价改写：`qValues.put(stateActionPair, q + qAlphas.get(stateActionPair) * (reward + gamma * nextQ - q))` 提取为局部变量 `alphaUsed` 与 `newQ`（值与求值顺序不变），Q 更新与 trace 采集共用。

| trace 字段 | 来源 |
|---|---|
| `no` | `steps`（while 条件 `steps++` 步进后即 1 起正序） |
| `x/y`、`fromType` | `stateActionPair.getState()`——`getCurrentCell()` 在 move 前返回的快照（move 只改到达格，快照类型即该步开始时状态） |
| `action` | `VisitRecord.actionName(stateActionPair.getAction())`（move 前的方向；`direction` 变量在 move 后已被重新赋值为 a′，不能再用） |
| `reward` / `totalReward` | `tempWorld.getLastReward()` / `tempWorld.getTotalReward()`（既有调用） |
| `nextX/nextY`、`toType` | 移动后 `tempWorld.getCurrentCell()`——道具已置 0、终点为 4、原地 == fromType |
| `td.nextAction` | `VisitRecord.actionName(direction)`（move 后 `policy.determineAction()` 的结果，既有变量） |
| `td.nextQ` | `nextQ`（既有局部量，更新前读值） |
| `td.tdTarget` / `td.delta` | `reward + gamma * nextQ` / `tdTarget - q` |
| `td.alpha` | `qAlphas.get(stateActionPair)`（更新前取值，即 `alphaUsed`） |
| `qOld` / `qNew` | `q`（既有） / `newQ`（提取的局部量） |
| episode `totalReward` / `endedBy` | `tempWorld.getTotalReward()` / `tempWorld.isTerminated()`（同 MC） |

结构安排（镜像 MonteCarlo 采集模式）：

- `collectTrace` 开关（默认 `true`，`@Setter` 供批量实验整体关闭）与 `trace` 字段（`@Getter`），镜像 MC；
- `trace = collectTrace ? TraceFactory.createEmptyTrace(world, alpha, gamma, policy, maxEpisode, maxStepsPerEpisode, TraceMeta.ALGORITHM_SARSA) : null`；
- 每回合：局部 `List<VisitRecord>` 按执行正序收集行 → 回合循环结束后构建 `EpisodeTrace`（index / totalReward / endedBy）加入 `trace.episodes`；
- `actionName` 从 MonteCarlo 移至 `VisitRecord.actionName(int)`（`ACTION_*` 常量本就定义在 VisitRecord），MC 委托复用；
- **TraceFactory 置于 `com.metrocarlo.montecarlo` 包**：唯一消费方 MonteCarlo/Sarsa 均在该包；若放 history 包会引入 history→agent 反向依赖（agent→world→history 已成链，避免成环）。

---

## 五、序列化与导出

- `TraceJsonWriter.appendMeta`：`version` 之后新增 `algorithm`（可空字符串字段）；
- `TraceJsonWriter.appendVisit`：按 `getTd() != null` 分派——sarsa 行输出 `td` 嵌套对象（5 字段）后接 `qOld/qNew`；否则维持 v1 字段集（`discountedNextG/G/sOld/sNew → qOld/qNew`），**MC 输出与 v1 逐字节一致**（除 `meta.version/algorithm`）；
- `TraceFileWriter.write`：`meta.algorithm` 非 `montecarlo` 时在 `data/trace/` 下插入算法段（§二）；
- 文件格式约束同 MC 设计 §六：UTF-8 无 BOM、LF 换行、2 空格缩进、无尾随逗号、double 原样输出、整数语义字段不带 `.0`。

---

## 六、GUI 回放与 Q 表重建契约（后续 T4 依据，对照 requirements/10 原型）

1. **加载分发**：`meta.algorithm == "sarsa"` → sarsa 回放；`"montecarlo"` 或缺失（v1 旧文件）→ MC 回放，互不影响。
2. **初始化**：同 MC 设计 §4.1——`initialMap` 按行序直用、每 episode 开始还原地图（道具复原）；**只有 Q 表**（从全 0 出发），无 S 表。
3. **逐行应用（文件序）**：agent 位置 ← `nextX/nextY`；地图写回 `map[nextY][nextX] = toType`（同 MC 设计 §4.4，GUI 零推演）；**Q 表覆盖写 `qMap[(x,y,action)] = qNew`**——在线更新无回合末结算阶段，行应用完即表状态正确。
4. **五按钮语义**：执行一次 action = 应用下一行（详情卡按行字段渲染；a′ 显示规则：`toType==4` → "—"）；执行完当前 episode = 连续应用至该 episode 行取尽（**无结算步骤**，过程面板直接展示该 episode 全部行）；执行 100 个 / 全部 / reset 同 MC 设计 §4.2 循环语义。
5. **过程面板列 ↔ 字段映射**：步=`no`、状态=`x,y`、a=`action`、r=`reward`、到达 s′=`nextX,nextY`、a′=`td.nextAction`、Q(s′,a′)=`td.nextQ`、TD 目标=`td.tdTarget`、δ=`td.delta`、Q(s,a) 前→后=`qOld,qNew`；公式条静态展示 ①②③（行内 α 取 `td.alpha`）。
6. **中心 V̂(s) = max_a Q(s,a)**：GUI 派生（≈ 虚线徽章；四值并列取其一、全 0 不标注，同原型），不落 trace；贪婪动作描边规则同原型。
7. **参数条**：α/γ/ε ← `meta`；"α 衰减"状态由行内 `td.alpha` 是否随行变化直判（无硬编码）。
8. **note 直判（现奖励值）**：`toType==4` → 进入终点；`nextX/nextY == x/y` → 撞墙/越界（净 −2）；`reward==+19` → 吃到苹果（净 +19）；`reward==−51` → 踩中炸弹（净 −51）；其余 → 进入空格（净 −1）。

---

## 七、一致性自检（建议，工具可选执行）

1. `meta.width/height` == `initialMap` 列数/行数；`version == 2`、`algorithm == "sarsa"`；`episodes` 的 `index` 从 1 连续递增；
2. **数值链**：`td.tdTarget == reward + gamma × td.nextQ`、`td.delta == tdTarget − qOld`、`qNew == qOld + td.alpha × td.delta`（均 ±1e-9）；
3. **on-policy 链**：回放序相邻行 `td.nextAction(k) == action(k+1)`（k < 行数；行序即执行序，无需重排）；
4. 位置/类型衔接与消耗对应同 MC 设计 §七.6/.7：相邻行上行 `(nextX,nextY) ==` 下行 `(x,y)`、上行 `toType ==` 下行 `fromType`；每 episode 首行 `x/y == (startX,startY)`；`reward ∈ {+19, −51}` 的行 `toType == 0`；`reward == −2` 的行原地且 `toType == fromType`；
5. 末行：`toType == 4` ↔ `endedBy == "terminal"`，否则 `"maxSteps"`；
6. `action` / `endedBy` / `td.nextAction` 取值域 ∈ {up, right, down, left} / {terminal, maxSteps}。

---

## 八、实现任务分解与验收

| 步骤 | 内容 |
|---|---|
| S1 | history 包：`TraceMeta` +`algorithm` / VERSION→2；新类 `TdUpdateRecord`；`VisitRecord` +`td` 字段与静态 `actionName`；`EpisodeTrace` javadoc 行序修正 |
| S2 | `com.metrocarlo.montecarlo.TraceFactory`（createEmptyTrace + resolveEpsilon）；MonteCarlo 委托去重（删私有 createEmptyTrace/resolveEpsilon/actionName） |
| S3 | `Sarsa`：`collectTrace` 开关 + `getTrace()` + 逐行采集（§四映射） |
| S4 | `TraceJsonWriter` 输出 `algorithm` 与 `td` 块；`TraceFileWriter` 算法段 |
| S5 | `Main` 增 `runSarsaOnOneMap`（map/1 + EpsilonGreedyPolicy）→ 导出 `data/trace/sarsa/map-1/run-*/trace.json` |

验收要点（本任务不新增测试，用户约定）：

- `mvn test` 既有用例全部通过；
- 运行 Main 产出 sarsa trace：`meta.algorithm=="sarsa"`、`version==2`、行字段集符合 §3.3、行数值满足 §七.2 数值链；
- MC 侧零行为变化：既有 MC trace 数据与 viewer 不受影响；MC 重新导出时行字段与 v1 逐字节一致（仅 meta 多 `algorithm`、`version` 为 2）。

---

## 九、本文档与相关文件的关系

| 文件 | 关系 |
|---|---|
| `intention/intention.md` | intention-12 为本次需求来源；intention-10 原型为 GUI 展示依据 |
| `design/3/README.md` | MC trace 格式基线：本文档大量复用其字段语义（§3.1）、文件组织（§2.2）、地图写回契约（§4.4）、自检框架（§七） |
| `requirements/10/prototype.html` | SARSA 回放原型：过程面板列、公式条 ①②③、V̂ 派生徽章等展示规则的来源 |
| `src/main/java/com/metrocarlo/history/` | `TdUpdateRecord` 为本设计新增；`VisitRecord/TraceMeta/EpisodeTrace` 按本文档扩展 |
| `src/main/java/com/metrocarlo/montecarlo/Sarsa.java` | 采集宿主：buildQValues() 主循环按 §四映射同步采集 |
