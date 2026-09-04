# requirements/2 · 蒙特卡洛可视化 GUI —— 需求分解与界面原型设计

> 需求来源：[intention.md](../intention/intention.md) 的 intention-2（第 5~27 行）。
> 本次范围：**把完整需求分解为小任务**，并**为 GUI 界面做出原型设计**（T1）。
> 原型的完整实现（Java trace 采集、导出、Python GUI）属后续任务，按本文件约束推进。

---

## 一、需求 → 小任务分解

| 编号 | 任务 | 交付物 / 落点 | 说明 |
|---|---|---|---|
| **T1** | **界面原型设计**（本次） | `requirements/2/prototype.html` + 本文件 | 高保真静态原型，验证布局与交互语义；含 trace 格式草案，供 T2 落地 |
| T2 | trace 数据模型 + MonteCarlo 采集 | `montecarlo` 包新增 trace 数据类；`MonteCarlo.buildQValuesAndSValues()` 内采集 | 不改动算法行为；格式按 §四 草案；**TDD**：先写 trace 构造/计算测试再实现 |
| T3 | 入口与导出 | `Main.runMonteCarloOnOneMap(String mapPath)` 实现；执行 `map/1/map.csv` 后导出 trace 到 `data/` 目录 | Java 不做 GUI；JSON 序列化建议新增 Jackson 依赖或手写轻量 writer（pom.xml 当前无相关依赖，需决策） |
| T4 | Python GUI 可视化 | `scripts/montecarlo_viewer.py` | 读取 `data/` 下 trace，tkinter（标准库）实现；§二 的布局与按钮语义为准 |
| T5 | 集成验收与手册 | `mvn test` 全绿、端到端截图、`scripts/manual.md` 追加 viewer 手册 | 完成后提交（commit 信息遵循仓库既有规范） |

**T2/T3 内部分解（后续执行时再细化）：**

- T2a 设计并实现 trace 记录结构（episode 粒度：visits 单表，见 §四）
- T2b 在 `MonteCarlo` 回合结束后倒序单循环中采集：每行一次状态访问，记录 G 与该行 S/Q 更新前后值（重复访问逐行记录）
- T2c 单测：固定一条路径断言 visits 数值（γ、α 已知，可手算期望值）
- T3a 实现 `runMonteCarloOnOneMap(mapPath)`：加载 World → 构建 MonteCarlo → 运行 → 写出 trace
- T3b 单测：跑少量 episode 后文件存在、JSON 可解析、字段齐全
- T3c 可选优化：`EpsilonGreedyPolicy` 的 `Random` 支持固定种子，保证 trace 可复现（README 开发约定有此条，当前实现尚未满足）

---

## 二、GUI 布局设计（本次原型所依据）

### 2.1 总体布局（1340 宽，三区）

```
┌──────────────────────────────────────────────────────────────────┐
│ 工具栏第 1 行：标题 + 5 个按钮（步进/回合/100 回合/全部/reset）        │
│ 工具栏第 2 行（靠右）：episode / action / agent 位置 / 累计奖励徽章   │
├─────────────────────────────┬────────────────────────────────────┤
│ 左：网格世界地图（5×5）        │ 右：① 运行状态卡（含 α/γ/ε 统一参数行） │
│   · 格底色 = 格子类型（浅色）   │     ② 最近一个 action 详情卡         │
│   · 格中心徽章 = stateValue   │     ③ G 计算 + S/Q 更新（合并单表）    │
│   · 四角徽章 = 四方向 Q        │        （每行 = 一次状态访问）        │
│     （左上← 右上↑ 右下→ 左下↓） │                                    │
│   · 道具/终点图标内嵌 S 徽章    │                                    │
│   · agent = 橙色高亮环         │                                    │
│   · 数值颜色：正蓝/负红，越深越大│                                    │
│   · 外围 y 轴 / 底部 x 轴坐标   │                                    │
├─────────────────────────────┴────────────────────────────────────┤
│ 状态条：最近一条过程日志                                            │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 五个按钮的语义（与 intention-2 原文一致）

| 按钮 | 语义 |
|---|---|
| 执行一次 action | agent 按策略走一步并立即展示：位置、动作详情、奖励、累计奖励、action 序号 |
| 执行完当前 episode | 从当前位置连续步进直到进入终点；结束后展示 G 计算与 S/Q 更新过程 |
| 执行 100 个 episode | 以当前 episode 为第 1 个，连续执行 100 个；过程面板只保留最近一个 episode 的结算 |
| 执行到所有 episode 结束 | 以当前 episode 为第 1 个，快进到第 1000 个（`DEFAULT_MAX_EPISODE`）结束 |
| reset | S/Q 清零、episode/action 计数归零、agent 回起点、地图类型还原、面板清空 |

### 2.3 关键展示规则

1. **数值颜色**：正 = 蓝（#1565c0），负 = 红（#c62828）；颜色深度按 `|v| / M` 线性映射（M 为当前 S/Q 各自表内最大绝对值），0 显示灰色，避免误导性色阶。徽章底色按深浅取半透明→实色、深底白字/浅底深字，保证任何格子底色上数字都清晰。
2. **布局防重叠**：**S 徽章独占格中心**（墙格不显示）；**Q 徽章分布在格子四角**（左上← / 右上↑ / 右下→ / 左下↓），箭头即动作方向，与 `Constants.DIRECTION_*` 一一对应，与中心 S 无水平重叠。
3. **道具格用浅色底**（炸弹浅红 / 苹果浅绿 / 终点浅黄），类型图标（emoji）内嵌在 S 徽章左侧——既表达格子类型又不干扰数值阅读；墙格保持深灰斜纹（不显示数值）。
4. **道具动态消耗**：agent 进入苹果/炸弹格后，该格底色与 S 徽章内图标立即消失（原型复刻 `World.move` 行为；每回合开始还原）。
5. **单步播放中 S/Q 不变**，只在回合结束后更新——符合蒙特卡洛回合制更新语义。
6. **过程面板合并为单表**，只保留最近一个已结束 episode：**G 计算与 S/Q 更新不再分表**（Java 实现已改为一个倒序循环同步完成），每行 = 一次状态访问，列 = 处理序 / episode 内步号 / 状态 (x,y) / 动作 / r / γ·G(后继) / G / S 前→后 / Q 前→后；同一格多次访问会连续出现多行，直观展示逐次更新（无 first-visit 覆盖）。
7. **表格上方有公式条**，与每行数据一一对照：G = r + γ·G(后继)；S ← S + α(G − S)；Q(s,a) ← Q(s,a) + α(G − Q(s,a))。
8. **算法参数统一展示**：α/γ/ε 不再散落在工具栏，集中显示在「运行状态」卡内的参数条（取值由常量注入，避免多处维护）。

### 2.4 后续 tkinter 实现映射建议（T4）

| 原型区域 | tkinter 实现建议 |
|---|---|
| 地图网格 | `Canvas` 绘制（格 = create_rectangle + create_text），逐帧重绘 |
| 按钮/徽章 | `ttk.Button` / `tk.Label` |
| G + S/Q 更新合并表 | `ttk.Treeview`（单表 9 列，与原型一致）或 Text 排版 |
| 播放节奏 | 单步由按钮事件驱动（`mainloop` 无阻塞）；100/全部 快进为同步循环 + 末尾一次刷新 |

---

## 三、prototype.html 使用说明

- 双击用浏览器打开即可，**零依赖、无网络请求**。
- 页面自动预演 300 个 episode（内置演示路径）使 S/Q 色阶可见，之后可从第 301 个 episode 起逐按钮体验。
- 内置 3 条演示路径：P1 吃到苹果（+2）、P2 撞墙两次（0）、P3 踩炸弹（-3），按固定模式循环，**完整复刻 `MonteCarlo.java` 当前实现的回合后倒序单循环**：每行先算 G = r + γ·G(后继)，随即更新该格 S 与该 (s,a) 的 Q（α=0.5，γ=0.9）；P2 路径中 (0,0) 被访问 3 次，过程表会连续出现 3 行，直观展示逐次更新（无 first-visit 覆盖）。
- ⚠️ 数值为排版演示数据，不代表真实策略统计结果；真实数值由 T2/T3 产出的 trace 提供。

---

## 四、trace 数据格式草案（T2 的输入约定）

### 4.1 设计原则

1. **单表记录**：每个 episode 的 `visits` 数组与 Java 回合结束后结算循环**逐行对应**（每行 = 一次状态访问：算 G → 更新该格 S → 更新该 (s,a) 的 Q），GUI 展示与数据一一对应，不拆分 G 行与更新行；
2. **回放与重建同源**：GUI 逐 action 回放取行内 `x/y/action/nextX/nextY/reward/totalReward`；S/Q 当前表则从全 0 出发，把已结束 episode 各行的 `sNew`/`qNew` 应用到内存表即可（与 Java 端演进完全一致），**无需全表快照**，导出体积小；
3. `visits` 按**结算循环顺序**存放（回合倒序，第 1 行 = 最后一步），行内 `no` 为 episode 内正序步号，GUI 如需正序回放按 `no` 重排即可；
4. 动作方向在 JSON 中用字符串 `up / right / down / left`（与 `Constants`：0↑ 1→ 2↓ 3← 对应），Python 端免去编码映射歧义；
5. `initialMap` 保持 **CSV 行序**（第一行 = 最上排 = y = height-1），Python 端可直接按行绘制；坐标字段一律为 World 坐标系 (x, y)。

### 4.2 结构草案（JSON，导出到 `data/`）

```jsonc
{
  "meta": {
    "mapPath": "map/1/map.csv",
    "width": 5, "height": 5,
    "initialMap": [[0,1,0,0,4], [0,0,0,0,3], [0,2,0,1,0], [0,1,0,1,0], [0,0,0,0,0]],  // 行序=CSV 行序
    "alpha": 0.5, "gamma": 0.9, "epsilon": 0.1,
    "maxEpisode": 1000, "maxStepsPerEpisode": 1000,
    "seed": null,                    // 若 T3c 落地则为固定种子
    "exportedAt": "2026-09-04T10:00:00"
  },
  "episodes": [
    {
      "index": 1,                    // 从 1 起，与 UI 展示一致
      "totalReward": 2,              // 该回合累计奖励
      "endedBy": "terminal",         // terminal | maxSteps
      "visits": [                    // 结算循环顺序（回合倒序）；每行 = 一次状态访问
        {
          "no": 12,                  // episode 内正序步号（回放顺序）
          "x": 4, "y": 1,            // 动作前所在格
          "action": "down",          // up | right | down | left
          "reward": 0,               // 即时奖励
          "totalReward": 2,          // 该步后的累计奖励
          "nextX": 4, "nextY": 0,    // 到达格（道具被消耗时 GUI 自行置空该格）
          "discountedNextG": 0.0,    // γ × G(后继)
          "G": 0.0,                  // 本行回报 = reward + discountedNextG
          "sOld": 0.0, "sNew": 0.0,  // 该格 stateValue 更新前后
          "qOld": 0.0, "qNew": 0.0   // 该 (状态, 动作) qValue 更新前后
        }
      ]
    }
  ]
}
```

> 注：`consumed`（道具消耗）不显式记录——GUI 重放时按 `nextX/nextY` 处类型是否为苹果/炸弹自行推演置空，与 `World.move` 语义一致。

### 4.3 数据量估算与约定

- 每回合 `visits` 行数 = 回合步数（重复访问也逐行记录），1000 episode × 平均 ~30 行 ≈ 3 万行；每行含 S/Q 更新前后值，总体积约数 MB，可接受。
- 初始 S/Q 全为 0（Java `initQAndSValues`），GUI 无需额外初始化数据；墙格不渲染数值（与 §2.3 规则一致）。
- `x/y` 为**动作发生前所在格**（`ActionRecord.cell` 语义），与过程表「状态 (x,y)」列一致。

---

## 五、验收清单（对照 intention-2 原文）

| # | 需求点 | 原型落点（prototype.html） | 状态 |
|---|---|---|---|
| 1 | GUI 界面 | 单页桌面式窗口 | ✅ |
| 2 | 执行一次 action | 工具栏按钮 1 | ✅ |
| 3 | 执行完当前 episode | 工具栏按钮 2 | ✅ |
| 4 | 执行 100 个 episode（当前为第 1 个） | 工具栏按钮 3 | ✅ |
| 5 | 执行到所有 episode 结束 | 工具栏按钮 4 | ✅ |
| 6 | reset | 工具栏按钮 5 | ✅ |
| 7 | 地图格子类型 | 左区网格底色 + emoji | ✅ |
| 8 | agent 当前位置 | 橙色环 + 徽章组位置 | ✅ |
| 9 | stateValues / qValues 正蓝负红、越深越大 | 格中心 S / 四角 Q 徽章 + 图例色阶 | ✅ |
| 10 | 最近 action 详情（奖励、累计奖励） | 右侧 ② 卡 | ✅ |
| 11 | 最近 episode 每格 G 计算过程 | 过程面板合并单表的「G」列 | ✅ |
| 12 | G 计算后的 S/Q 更新过程 | 过程面板合并单表的「S / Q 前→后」列 | ✅ |
| 13 | 当前 action 序号 | 工具栏 action 徽章 + ① 卡 | ✅ |
| 14 | 当前 episode 序号 | 工具栏 episode 徽章 + ① 卡 | ✅ |
| 15 | trace 数据承载 | §四 草案（T2 落地） | 📋 |
| 16 | 入口 `runMonteCarloOnOneMap` + 导出 data/ | T3 | 📋 |
| 17 | Python GUI 脚本（Java 不做 GUI） | T4 | 📋 |

✅ = 原型已覆盖　📋 = 后续任务

---

## 六、本目录内容

| 文件 | 说明 |
|---|---|
| `prototype.html` | 界面高保真原型（可交互演示，浏览器直接打开） |
| `README.md` | 本文件：任务分解 + 设计说明 + trace 草案 |
