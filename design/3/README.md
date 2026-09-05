# requirements/3 · trace 数据生成 —— 格式与文件组织设计

> 需求来源：[intention.md](../../intention/intention.md) 的 intention-3（第 29~30 行），承接 [requirements/2/README.md](../2/README.md)（原型 + §四 trace 草案）。
> 本次范围：**trace 数据的格式定稿与文件组织设计**（T2 的数据约定），**不含实现**（Java 采集/导出、Python GUI 属后续任务）。
> 与草案的关系：§四草案的**字段命名与语义全部保留**；本文档在其上**补全文件组织、增补少量字段、明确回放/重建契约**。冲突处以本文档为准。

---

## 一、设计目标与关键决策

| # | 决策点 | 结论 | 理由 |
|---|---|---|---|
| 1 | 数据格式 | **单文件 JSON 文档**（`meta` 头 + `episodes` 数组） | 与 §四草案一致；GUI 需顺序重放全部 episode 并可从 0 重建 S/Q 表，单文件一次加载最简（体积见 §六，约数 MB，可接受）；无需 JSONL/分片 |
| 2 | episode 粒度 | 每个 episode 一个对象，内含**全量 visits 行**（每行 = 一次状态访问，含 G 计算与 S/Q 更新前后值） | 原型「过程面板」只展示最近一个 episode，但「100/全部快进」需连续应用每个 episode 的更新，全量是必要开销 |
| 3 | visits 行序 | 按**结算循环顺序**（回合倒序）存放；行内 `no` 为 episode 内正序步号 | 与 §四草案 4.1.3 一致：GUI 回放按 `no` 升序取行，过程面板按文件序直出「处理序」 |
| 4 | 动作方向 | 字符串 `up / right / down / left`（对应 `Constants.DIRECTION_*` 0/1/2/3） | 与 §四草案 4.1.4 一致，Python 端免编码映射歧义 |
| 5 | 初始地图 | `meta.initialMap` 按 **CSV 行序**（第一行 = 最上排 = y = height-1） | 与 §四草案 4.1.5 一致，Python 端按行绘制零转换 |
| 6 | 重复访问 | 同一格多次访问逐行记录（无 first-visit 覆盖） | 对齐 `MonteCarlo.java` 当前倒序单循环实现与原型行为 |
| 7 | 数值精度 | 原样输出 double（不四舍五入）；`reward/totalReward/坐标/序号` 保持整数 | GUI 展示自行 toFixed；S/Q 重建采用**覆盖写 `sNew`/`qNew`**（非重算），精度不产生漂移 |
| 8 | 文件组织 | `data/trace/map-<N>/run-<时间戳>/trace.json`（详见 §二） | 支持 10 张地图与多次实验（不同 α/γ/ε/seed/回合数）并存对比，呼应 README「固定种子、结果可复现可对比」的开发约定 |
| 9 | schema 演进 | `meta.version` 标识格式版本（当前 1） | 便于未来字段扩展时旧文件可识别 |
| 10 | 地图变化 | 每行记录 `fromType` / `toType`（动作前后 agent 所在格类型）；GUI 只做数据写回，不做领域推演 | trace 是 GUI 的唯一事实来源：`World.move` 的消耗规则只存在于 Java 一处，GUI 不复制该逻辑（原型演示引擎正因复制规则而把「吃到苹果」错挂在错误的步上）；信息完备且代价仅每行 2 个 int（一次移动至多改变 agent 所在格一个格子，见 §4.4） |

---

## 二、文件组织方式

### 2.1 目录树

```text
data/
└── trace/                                # trace 根目录（运行产物，建议不入库，见 2.4）
    └── map-1/                            # 按源地图分组：源地图 map/1/map.csv → map-1
        └── run-20260905-103000/          # 一次运行一个子目录
            └── trace.json                # 单文件全量 trace（meta + 全部 episodes）
```

### 2.2 命名规则

| 层级 | 规则 | 示例 | 说明 |
|---|---|---|---|
| 地图分组 | `map-<N>`，N 为源目录名 | `map/1/map.csv` → `map-1` | 与源地图一目了然对应；加 `map-` 前缀避免与 `map/<N>` 纯数字目录混淆 |
| 运行目录 | `run-<yyyyMMdd-HHmmss>`；同一秒冲突时追加 `-2`、`-3`… | `run-20260905-103000` | 本地时间、到秒；**字典序 = 时间序**，GUI 取最新运行无需索引文件 |
| 数据文件 | 固定名 `trace.json` | `trace.json` | 参数等元信息内聚于文件头部 `meta`，目录名保持简短；一次运行的全部文件（未来可扩展 sidecar）收拢在 run 目录内 |

### 2.3 生成与生命周期

- 目录逐级自动创建（`data/trace/` → `map-<N>/` → `run-<时间戳>/`），不存在则建。
- **每次运行生成新 run 目录，不覆盖旧运行**——多次实验（不同 ε/α/γ、固定种子）可保留对比；旧目录由用户手动清理。
- GUI 定位规则：给定地图分组目录（如 `data/trace/map-1/`），按名称取**最新** `run-*` 子目录加载；T4 亦可提供文件选择对话框支持任意 `trace.json`。

### 2.4 版本控制建议

- `data/trace/` 为**运行产物**：体积数 MB、可由 T3 入口一键再生成。
- 建议在 `.gitignore` 追加 `data/trace/`（T3 落地时一并提交该行），仓库只保留源地图（`map/`）与设计文档。

---

## 三、trace 数据格式定稿

### 3.1 字段字典

**顶层 / `meta`**

| 字段 | 类型 | 说明 |
|---|---|---|
| `meta.version` | int | 格式版本号，当前 `1`（本次新增，草案无） |
| `meta.mapPath` | string | 源地图相对路径，如 `map/1/map.csv` |
| `meta.width` / `meta.height` | int | 网格尺寸（列数 / 行数） |
| `meta.initialMap` | int[][] | 初始类型网格，**行序 = CSV 行序**（首行 = y = height-1）；取值见 `Constants.CELL_TYPE_*` |
| `meta.startX` / `meta.startY` | int | agent 出发点（当前恒为 0/0；本次新增，草案无——GUI 起始渲染与校验需要） |
| `meta.alpha` / `meta.gamma` / `meta.epsilon` | double | 算法参数（实际取值） |
| `meta.maxEpisode` / `meta.maxStepsPerEpisode` | int | 回合数上限 / 单回合步数上限 |
| `meta.seed` | int \| null | 随机种子（`EpsilonGreedyPolicy` 支持固定种子后填入；未固定为 null） |
| `meta.exportedAt` | string | 导出时间，ISO-8601，如 `2026-09-05T10:30:00` |

**`episodes[]`（数组元素）**

| 字段 | 类型 | 说明 |
|---|---|---|
| `index` | int | episode 序号，从 1 起（与 UI 展示一致） |
| `totalReward` | int | 该回合累计奖励（= 最后一行 `totalReward`，冗余便于直读） |
| `endedBy` | string | 结束原因：`terminal`（进入终点）\| `maxSteps`（达步数上限） |
| `visits` | array | **结算循环顺序**（回合倒序，第 1 行 = 最后一步）；每行 = 一次状态访问 |

**`visits[]`（数组元素，每行 = 一次状态访问）**

| 字段 | 类型 | 说明 |
|---|---|---|
| `no` | int | episode 内正序步号（1 起；GUI 回放顺序 = 按 `no` 升序） |
| `x` / `y` | int | 动作前所在格（World 坐标系） |
| `action` | string | `up \| right \| down \| left` |
| `reward` | int | 即时奖励（撞墙/越界 = -1、炸弹 = -5、苹果 = +2、空/终点 = 0） |
| `totalReward` | int | 该步后的累计奖励 |
| `nextX` / `nextY` | int | 到达格（撞墙/越界时为 (x, y) 原地） |
| `fromType` | int | **移动前**所在格 (x, y) 的类型（该步开始时的格子状态，含此前各步的消耗结果） |
| `toType` | int | **移动后**所在格 (nextX, nextY) 的类型（道具被本步消耗后已为 0；进入终点为 4；撞墙/越界原地时 == `fromType`） |
| `discountedNextG` | double | γ × G(后继)，即 `gamma * lastG`（回合最后一步为 0） |
| `G` | double | 本行回报 = `reward + discountedNextG` |
| `sOld` / `sNew` | double | 该格 stateValue 更新前 / 后 |
| `qOld` / `qNew` | double | 该 (状态, 动作) 的 qValue 更新前 / 后 |

### 3.2 规范示例（map/1，第 1 个 episode 全量，其余省略）

示例路径：`(0,0)→(0,1)→(1,1)→(2,1)→(3,1)→(4,1)苹果(+2)→(4,0)终点`，共 6 步，α=0.5、γ=0.9。
以下数值可手算复核（G 自最后一步回传，S/Q 由 0 起增量），供 T2 单测与 T4 校验对照。
示例同时演示地图变化的数据化：第 5 步吃苹果的行 `toType` 为 0（`(4,1)` 被置空），第 6 步从该格出发的行 `fromType` 已为 0——GUI 逐行写回即可复现，无需任何领域规则：

```jsonc
{
  "meta": {
    "version": 1,
    "mapPath": "map/1/map.csv",
    "width": 5, "height": 5,
    "initialMap": [                     // CSV 行序：首行 = y=4
      [0, 0, 0, 0, 0],                  // y=4
      [0, 1, 0, 1, 0],                  // y=3
      [0, 2, 0, 1, 0],                  // y=2
      [0, 0, 0, 0, 3],                  // y=1（(4,1) 苹果）
      [0, 1, 0, 0, 4]                   // y=0（(4,0) 终点）
    ],
    "startX": 0, "startY": 0,
    "alpha": 0.5, "gamma": 0.9, "epsilon": 0.1,
    "maxEpisode": 1000, "maxStepsPerEpisode": 1000,
    "seed": null,
    "exportedAt": "2026-09-05T10:30:00"
  },
  "episodes": [
    {
      "index": 1,
      "totalReward": 2,
      "endedBy": "terminal",
      "visits": [                       // 结算循环顺序（回合倒序；第 1 行 = 最后一步）
        {                               // 处理序 1
          "no": 6, "x": 4, "y": 1, "action": "down",
          "reward": 0, "totalReward": 2, "nextX": 4, "nextY": 0,
          "fromType": 0, "toType": 4,        // (4,1) 已在上一步被置空（fromType=0）；本步进入终点 (4,0)
          "discountedNextG": 0.0, "G": 0.0,
          "sOld": 0.0, "sNew": 0.0, "qOld": 0.0, "qNew": 0.0
        },
        {                               // 处理序 2
          "no": 5, "x": 3, "y": 1, "action": "right",
          "reward": 2, "totalReward": 2, "nextX": 4, "nextY": 1,
          "fromType": 0, "toType": 0,        // (4,1) 苹果被本步消耗 → 置空
          "discountedNextG": 0.0, "G": 2.0,
          "sOld": 0.0, "sNew": 1.0, "qOld": 0.0, "qNew": 1.0
        },
        {                               // 处理序 3
          "no": 4, "x": 2, "y": 1, "action": "right",
          "reward": 0, "totalReward": 0, "nextX": 3, "nextY": 1,
          "fromType": 0, "toType": 0,
          "discountedNextG": 1.8, "G": 1.8,
          "sOld": 0.0, "sNew": 0.9, "qOld": 0.0, "qNew": 0.9
        },
        {                               // 处理序 4
          "no": 3, "x": 1, "y": 1, "action": "right",
          "reward": 0, "totalReward": 0, "nextX": 2, "nextY": 1,
          "fromType": 0, "toType": 0,
          "discountedNextG": 1.62, "G": 1.62,
          "sOld": 0.0, "sNew": 0.81, "qOld": 0.0, "qNew": 0.81
        },
        {                               // 处理序 5
          "no": 2, "x": 0, "y": 1, "action": "right",
          "reward": 0, "totalReward": 0, "nextX": 1, "nextY": 1,
          "fromType": 0, "toType": 0,
          "discountedNextG": 1.458, "G": 1.458,
          "sOld": 0.0, "sNew": 0.729, "qOld": 0.0, "qNew": 0.729
        },
        {                               // 处理序 6（回合第一步，最后被处理）
          "no": 1, "x": 0, "y": 0, "action": "up",
          "reward": 0, "totalReward": 0, "nextX": 0, "nextY": 1,
          "fromType": 0, "toType": 0,
          "discountedNextG": 1.3122, "G": 1.3122,
          "sOld": 0.0, "sNew": 0.6561, "qOld": 0.0, "qNew": 0.6561
        }
      ]
    }
    // …… 实际文件含全部 1000 个 episode
  ]
}
```

> 说明：示例 episode 为真实可达路径（对齐原型 P1 的后半段），数值为第一回合（S/Q 初始全 0）的真实结算结果，可作为 T2 手算单测的基准之一；实际运行中该路径是否出现由 ε-贪婪策略决定，**内容以真实运行为准**。

---

## 四、GUI 回放与 S/Q 重建契约（T4 依据）

### 4.1 加载与状态初始化

- 读 `trace.json` 一次入库；`initialMap` 按行序直接使用（第 row 行 ↔ y = height-1-row）。
- S/Q 内存表从全 0 出发（墙格不显示数值，规则同原型 §2.3）。
- 动态地图 = `initialMap` 深拷贝；每开始回放一个 episode 时**还原**（道具复原，与 Java 每回合 `reset` 一致）。
- 回放中每应用一行（按 `no` 升序）执行一次**纯数据写回**：`map[nextY][nextX] = toType`（一次移动至多改变 agent 所在格这一个格子；撞墙/越界原地行写回同值，幂等）——**GUI 不复制任何 `World.move` 领域规则**。

### 4.2 五个按钮 × trace 数据操作

| 按钮 | 数据操作 |
|---|---|
| 执行一次 action | 取当前 episode `visits` 中 `no` 升序的下一行 → 应用该行：agent 位置 ← `nextX/nextY`，地图写回 `map[nextY][nextX] = toType`，详情卡填行字段与 note（note 由行字段直判，见 §4.4）；若该行 `toType == 4`（进入终点）或 `endedBy == maxSteps` 且行已取尽 → 进入结算 |
| 执行完当前 episode | 重复单步至该 episode 结束 → 结算（见 4.3）→ 过程面板展示该 episode 的 visits 行（文件序 = 处理序） |
| 执行 100 个 episode | 循环执行上述「执行完当前 episode」n 次（n ≤ 剩余）；过程面板只留最近一个 episode |
| 执行到所有 episode 结束 | 处理至 `episodes` 末尾（进度条上限 = `meta.maxEpisode`，但**以文件实际 episode 数为准**） |
| reset | 清空内存 S/Q、agent 回 `meta.startX/startY`、动态地图还原、面板清空——**全部由已加载的 trace 数据可重放，无需外部状态** |

### 4.3 回合结束结算（S/Q 表重建规则）

1. 该 episode 结算前，内存 S/Q 表**不因单步而变化**（回合制语义，与原型/Java 一致）；
2. 结算时按文件序遍历该 episode 的 `visits`，对每行**覆盖写**：`sMap[(x,y)] = sNew`、`qMap[(x,y,action)] = qNew`；
3. 全部 episode 处理完，内存表 == Java 端最终 `stateValues`/`qValues`（覆盖写等价于逐步更新，中间行 `sOld` 仅服务过程面板展示，不参与重建）。

### 4.4 地图变化：数据驱动，GUI 零推演

`World.move` 的消耗规则（进入苹果/炸弹格即置空；撞墙/越界原地不动、不消耗脚下格）**只存在于 Java 一侧**。trace 每行用 `fromType`/`toType` 直接给出该步前后 agent 所在格的状态，GUI 应用行数据时不判断、不推演：

- **信息完备性**：一次移动至多使一个格子变化，且只可能是 agent 所在格（真实进入才消耗）。因此逐行写回 `map[nextY][nextX] = toType` 即可完整复现任意时刻地图，无需整图快照；
- **行间衔接（天然自检）**：回放序相邻两行满足上行 `(nextX, nextY) ==` 下行 `(x, y)`，且上行 `toType ==` 下行 `fromType`——agent 连续移动，出发格即上一行到达格；该约束保证不存在未被记录在案的格子变化；
- **撞墙/越界行**（`reward == -1`）：`nextX/nextY == x/y`、`toType == fromType`，写回幂等；
- `note` 文案（进入空格 / 吃到苹果 / 踩中炸弹 / 撞墙 / 进入终点）由行字段**直判**：`toType == 4` → 进入终点；`reward == -1` → 撞墙；`reward == +2` → 吃到苹果；`reward == -5` → 踩中炸弹；其余 → 进入空格。不再需要“查该格初始是不是道具”等外部状态。

---

## 五、Java 采集点映射（T2 实现提示）

| trace 字段 | `MonteCarlo.buildQValuesAndSValues()` 现有来源 |
|---|---|
| `no` | `i + 1`（history 正序位置；结算循环 `i` 自 size-1 递减到 0） |
| `x/y`、`action`、`reward`、`totalReward`、`nextX/nextY` | `history.get(i)`（`ActionRecord`：cell、action、reward、totalReward、nextCell） |
| `fromType` | `actionRecord.getCell().getType()`——动作前所在格快照（`World.move` 入历史前不修改该快照，类型即该步开始时状态） |
| `toType` | `actionRecord.getNextCell().getType()`——`World.move` 在入历史前已把被消耗的道具格置 0，`nextCell` 类型天然即“移动后”状态；撞墙/越界时 `nextCell == cell`（同一快照），两字段相等 |
| `discountedNextG` | 循环内 `gamma * lastG`（现有局部量） |
| `G` | 现有局部量（`reward + gamma * lastG`） |
| `sOld/sNew`、`qOld/qNew` | 现有局部量（`originalSValue/newSValue`、`originalQValue/newQValue`） |
| episode `totalReward` | 该回合最后一条 history 的 `totalReward`（或 `tempWorld.getTotalReward()`） |
| `endedBy` | 回合循环退出后判 `tempWorld.isTerminated()` → `terminal`，否则 `maxSteps` |

采集**只读不改**：不触碰 qValues/stateValues 内容与循环顺序，算法行为零变化（T2 验收点）。

---

## 六、数据量估算与文件格式约束

- 规模：1000 episode × 平均 ~10~40 步（ε-贪婪绕行），约 1~4 万行 visits；每行约 200 字节 → 单文件约 2~8 MB，Python `json.load` 一次读取无压力。
- 编码：UTF-8 无 BOM、LF 换行；JSON 缩进 2 空格（便于人工查看与 diff），体积影响可忽略。
- 数值：double 原样输出；整数语义字段（坐标/序号/奖励）保持整数书写（不带 `.0`）。
- 序列化实现（T3 决策，本文件给倾向）：结构固定且字符串内容几乎无转义需求，**建议手写轻量 writer（零新依赖）**；若引入 Jackson 仅需 `jackson-databind`。T3 测试断言「文件存在、可解析、字段齐全」据此调整。

---

## 七、一致性自检（建议，测试/工具可选执行）

1. `meta.width/height` == `initialMap` 列数/行数；`version == 1`；
2. `episodes` 的 `index` 从 1 连续递增，无跳号；
3. 每 episode 首行（处理序第 1 行）`x/y` 为该回合**最后动作前**所在格，`no == visits 长度`；末行 `no == 1` 且 `x/y == (startX, startY)`；
4. 每行校验：`G == reward + discountedNextG`（±1e-9）；`sNew == sOld + alpha*(G - sOld)`、`qNew == qOld + alpha*(G - qOld)`；
5. `action` 取值 ∈ {up, right, down, left}；`endedBy` ∈ {terminal, maxSteps}；
6. **地图变化一致性**：回放序（`no` 升序）相邻两行满足上行 `(nextX, nextY) ==` 下行 `(x, y)`，且上行 `toType ==` 下行 `fromType`（撞墙原地行亦然：上下行同格同类型）；每 episode 首行（`no == 1`）`fromType` == (startX, startY) 的初始类型（空 0）；
7. **消耗对应**：`reward ∈ {+2, -5}` 的行其 `toType == 0`，且同一道具格在整份 trace 中至多对应一个这样的行（此后到访行 reward 为 0、toType 为 0）；`reward == -1` 的行满足 `nextX/nextY == x/y` 且 `toType == fromType`；
8. 若 `meta.seed` 非空，同 seed 重跑应产出逐字节一致的 trace（T3c 落地后验证）。

---

## 八、任务影响与验收（对 requirements/2 §一 的增量更新）

| 编号 | 任务 | 以本文档为准的更新 |
|---|---|---|
| T2 | trace 数据模型 + 采集 | 数据类字段与 §三字段一一对应；采集点见 §五；**无 first-visit 覆盖，逐行记录**；单测手算期望值可参考 §3.2 示例；版本号写 `meta.version=1` |
| T3 | 入口与导出 | 输出目录改落 **`data/trace/`**（草案原「data/」收敛于此）；目录组织按 §2.2；`meta.startX/startY` 从 World 起点取值；`.gitignore` 追加 `data/trace/`；序列化实现按 §六倾向决策 |
| T4 | Python GUI | 按 §四契约消费 trace（加载定位见 §2.3）；渲染规则沿用 requirements/2 §2.3 |
| T5 | 集成验收 | 端到端：运行 map/1 → 产出 `data/trace/map-1/run-*/trace.json` → GUI 逐按钮回放与 Java 运行一致（S/Q 表、过程面板逐行比对 §3.2 手算基准） |

验收要点（对照 intention-2 原文）：trace 需支撑 GUI 的全部展示项（§三字段 ↔ 原型 §2.3 各展示规则一一覆盖，无外部硬编码数据依赖）。

---

## 九、本文档与相关文件的关系

| 文件 | 关系 |
|---|---|
| `intention/intention.md` | intention-3 为本次需求来源；intention-2 为 GUI 总需求 |
| `requirements/2/README.md` | §四为 trace 草案（字段命名基线）；§2.3 为 GUI 展示规则 |
| `requirements/2/prototype.html` | GUI 高保真原型（字段消费与回放语义已验证） |
| `requirements/3/README.md` | 本文件：格式定稿 + 文件组织 + 回放/重建契约 |
