# metro-carlo · 蒙特卡洛方法学习项目

基于 **5×5 网格世界游戏** 的蒙特卡洛（Monte Carlo）方法学习项目，使用 **Java 21** 实现。
项目以本 README 中定义的游戏为统一载体，用随机模拟与统计平均研究 agent 的移动策略。

## 游戏设定

### 网格世界

- 5×5 网格，坐标 \((x, y)\)：\(x\) 从左到右取值 0~4，\(y\) 从下到上取值 0~4
- 例：(0,0) 是最左下方格子，(4,4) 是最右上方格子
- 每个格子要么为空，要么为墙体，要么**有且仅有一个内容物**；
  内容物取自以下列表：**苹果、终点、炸弹**

### Agent 与移动

- agent 每局总是从 (0,0) 出发
- 每次移动一格，移向相邻格子（上下左右）
- 一次移动的结果只有两种：**撞墙**，或**到达相邻格子并与之交互一次**

### 交互与奖励

| 移动结果 | 奖励 | 状态变化 |
|---|---|---|
| 进入空格子 | -1（每步惩罚） | 留在该格，继续游戏 |
| 试图进入墙体格子（撞墙） | -1（每步） + -1（撞墙） = **-2** | 停留在原地，继续游戏 |
| 移动方向超出 5×5 边界（撞墙） | -2（同撞墙） | 停留在原地，继续游戏 |
| 进入带苹果的格子 | -1（每步） + +20（苹果） = **+19** | 留在该格，继续游戏 |
| 进入带炸弹的格子 | -1（每步） + -50（炸弹） = **-51** | 留在该格，继续游戏 |
| 进入带终点的格子 | -1（仅每步惩罚，无终点奖励） | **游戏结束**（累计奖励截至本步） |

> 为避免贪婪策略原地打转成环（intention-8 设定）：每采取一个行动扣 1 分，同时加大苹果奖励（+20）与炸弹惩罚（-50）。

### 目标

agent 的目标，是在**一局游戏**中获得最高的**累计奖励**。

### 地图文件格式

- 地图以 CSV 文件描述（项目内置 10 张：`map/1/map.csv` ~ `map/10/map.csv`，由 `scripts/generate_maps.py` 生成）
- 每行对应一排格子，**第一行为最上方一排**（对应坐标系：最后一行是 \(y=0\)，第一行是 \(y=height-1\)）
- 每个单元格为整数，取值与 `Constants` 中的格子类型编码一致：

| 数值 | 含义 |
|---|---|
| 0 | 空格子 |
| 1 | 墙体 |
| 2 | 炸弹 |
| 3 | 苹果 |
| 4 | 终点 |

- 所有行列数必须一致（矩形网格）；宽度 = 列数，高度 = 行数
- 单元格两侧空格、空行与 UTF-8 BOM 会被容忍；文件为空、行宽不一致或出现非法整数时将报错

## 环境要求

| 工具  | 版本       | 说明                          |
|-------|------------|-------------------------------|
| JDK   | 21 (LTS)   | 构建与运行环境（`pom.xml` 已锁定） |
| Maven | 3.9+       | 构建工具                      |

## 当前状态与快速开始

核心链路已实现：网格世界（`world` 包：`World` + `CsvParser`）→ 决策策略（`agent` 包：`RandomPolicy`、`EpsilonGreedyPolicy`）→ 蒙特卡洛评估与控制 / Sarsa / Q-learning 在线 TD 控制（`montecarlo` 包）→ trace 导出与 Python GUI 回放（`montecarlo_viewer.py` / `sarsa_viewer.py` / `qlearning_viewer.py`）→ 地图 1-10 的策略对比实验与可视化（策略 ε 扫描 + montecarlo/sarsa/q-learning 三算法对比）。

```bash
# 编译并运行主类：跑一轮 Q-learning 在线 TD 控制，并把 trace 导出到 data/trace/qlearning（供 qlearning_viewer.py 回放；Main 内可切换 runSarsaOnOneMap / runMonteCarloOnOneMap → sarsa / 蒙特卡洛）
mvn compile exec:java

# 仅编译主代码
mvn compile

# 运行单元测试
mvn test
```

其余入口（地图生成/渲染、trace 回放查看器、策略对比实验）见下文与 `scripts/manual.md`。

## 目录结构

```
metro-carlo/
├── pom.xml                          # Maven 构建配置（JDK 21 + JUnit 5 + exec 运行插件）
├── README.md                        # 项目说明（本文件，含游戏设定）
├── map/                             # 内置 10 张地图（generate_maps.py 生成 CSV，render_maps.py 渲染 PNG）
├── scripts/                         # Python 入口脚本：地图生成/渲染、trace 回放查看器、策略/算法对比可视化（详见 scripts/manual.md）
├── data/                            # 运行产物（已 gitignore，可一键再生成）：trace/ 回合轨迹，compare/ 策略对比结果，algorithm-compare/ 算法对比结果
└── src/
    ├── main/java/com/metrocarlo/
    │   ├── Main.java                # 入口一：跑一轮 Q-learning 并导出 trace（mvn compile exec:java；可切换 Sarsa/蒙特卡洛）
    │   ├── world/                   # 网格世界模型：World、CsvParser
    │   ├── history/                 # trace 数据结构与导出：Trace/VisitRecord/TdUpdateRecord 与各 Writer
    │   ├── agent/                   # 决策策略：Policy、RandomPolicy、EpsilonGreedyPolicy
    │   ├── montecarlo/              # 蒙特卡洛评估与控制 / Sarsa / Q-learning：MonteCarlo、Sarsa、QLearning、TraceFactory
    │   ├── experiment/              # 批量对比实验：PolicyComparison（策略 ε 扫描，intention-7）/ AlgorithmComparison（三算法对比，intention-16）入口与 results.json 写盘
    │   └── common/                  # 常量与 JSON 拼接工具（Constants、JsonAppender）
    └── test/java/com/metrocarlo/    # 单元测试（WorldTest、CsvParserTest）
```

## 蒙特卡洛思路

- **一局游戏**（从 (0,0) 出发直至进入终点）视为一次随机样本
- 对同一移动策略重复大量对局，用**平均累计奖励**估计该策略的期望表现，作为评估与改进策略的依据

## Sarsa 在线 TD 控制（intention-12/13）

- 与蒙特卡洛「回合末统一结算」不同：**每走一步立即做一次 TD 更新** Q(s,a) ← Q(s,a) + α·(r + γ·Q(s′,a′) − Q(s,a))，其中 a′ 为策略在 s′ 实际选择的下一动作（on-policy 自举）
- trace 逐行记录 TD 明细（`td{nextAction,nextQ,tdTarget,delta,alpha}`），文件序 = 执行正序；`python scripts/sarsa_viewer.py` 可视化回放（中心 V̂(s) = max_a Q(s,a) 为 GUI 派生量），格式定稿见 `design/12/README.md`

## Q-learning 离线 TD 控制（intention-14/15）

- 与 Sarsa 的唯一差异：TD 目标取下一状态的 **max Q** 而非行为动作 Q——Q(s,a) ← Q(s,a) + α·(r + γ·max_a′ Q(s′,a′) − Q(s,a))（off-policy：a\* = argmax 只用于自举，行为动作仍由 ε-贪婪产生并仅用于移动）
- trace 行结构与 sarsa 逐字段同形（`td.nextAction` = a\*、`td.nextQ` = max Q），因此回放引擎/界面继承 sarsa 版仅改展示措辞：`python scripts/qlearning_viewer.py` 可视化回放，格式定稿见 `design/14/README.md`

## 策略对比实验（intention-7）

在 10 张内置地图上比较 **RandomPolicy** 与 **EpsilonGreedyPolicy（ε = 0.1 ~ 0.9）** 的学习效果：每种配置独立重复 50 次「从零训练 1000 个 episode → 用学到的 Q 表按全程贪婪走一遍」，统计贪婪回合总奖励的均值（rri' / rei'-ε）与标准差。

```bash
# 第一步：Java 端跑实验，结果写入 data/compare/run-<时间戳>/results.json（可加 --episodes/--repetitions 缩小规模）
mvn compile exec:java "-Dexec.mainClass=com.metrocarlo.experiment.PolicyComparison"

# 第二步：Python 端出图：compare_per_map.png（每图一子图对比）与 compare_summary.png（全地图 ε 趋势）
python scripts/compare_policies.py
```

实验结论：10 张地图上 **ε=0.1 均取得最优**平均贪婪奖励；ε 越大，Q 表被随机动作污染越重，效果单调下降；RandomPolicy 接近最差。数据格式与常见问题见 `scripts/manual.md` 末尾章节。

## 算法对比实验（intention-16）

在 10 张内置地图上比较 **MonteCarlo / Sarsa / Q-learning** 三种算法在 **ε = 1**（行为策略全程随机探索，训练回合行为分布三种算法完全一致）下的学习效果：每种算法独立重复 50 次「从零训练 1000 个 episode → 用学到的 Q 表按全程贪婪走一遍」，统计贪婪回合总奖励的均值与标准差。结果格式与策略对比实验同构但分属独立目录（`data/algorithm-compare/run-<时间戳>/results.json`），两套 schema 互不影响。

```bash
# 第一步：Java 端跑实验（可加 --episodes/--repetitions 缩小规模）
mvn compile exec:java "-Dexec.mainClass=com.metrocarlo.experiment.AlgorithmComparison"

# 第二步：Python 端出图：compare_per_map.png（每图一子图对比）与 compare_summary.png（全地图三算法均值对比）
python scripts/compare_algorithms.py
```

实验结论：ε=1 时训练回合行为等价于全随机（同 intention-7 的 RandomPolicy），三种算法的差异仅来自更新机制——回合末回报结算的蒙特卡洛几乎学不到可贪婪利用的 Q 表（500 次贪婪评估仅 52 次抵达终点，全地图平均 -1376）；每步即时更新、能利用后继状态信息的 TD 类算法明显更好：Sarsa 全地图平均 -1148、Q-learning -1265。按单图胜场 Q-learning 5 张最优（地图 1/4/5/7/9）、Sarsa 4 张（2/3/8/10）、蒙特卡洛仅地图 6——Sarsa 的均值领先主要来自地图 8 的突出表现（平均 -123.68，47/50 抵达终点）。与 intention-7「ε 越大效果越差」的结论一致，纯随机行为下无论哪种算法都无法稳定复现 ε=0.1 时的贪婪表现。

## 开发约定

- 中文注释与文档；随机实验支持固定种子，保证结果可复现、可对比
