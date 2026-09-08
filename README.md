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

核心链路已实现：网格世界（`world` 包：`World` + `CsvParser`）→ 决策策略（`agent` 包：`RandomPolicy`、`EpsilonGreedyPolicy`）→ 蒙特卡洛评估与控制（`montecarlo` 包）→ trace 导出与 Python GUI 回放 → 地图 1-10 的策略对比实验与可视化。

```bash
# 编译并运行主类：跑一轮完整蒙特卡洛，并把 trace 导出到 data/trace（供 montecarlo_viewer.py 回放）
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
├── scripts/                         # Python 入口脚本：地图生成/渲染、trace 回放查看器、策略对比可视化（详见 scripts/manual.md）
├── data/                            # 运行产物（已 gitignore，可一键再生成）：trace/ 回合轨迹，compare/ 策略对比结果
└── src/
    ├── main/java/com/metrocarlo/
    │   ├── Main.java                # 入口一：跑一轮完整蒙特卡洛并导出 trace（mvn compile exec:java）
    │   ├── world/                   # 网格世界模型：World、CsvParser 与 history（trace 数据结构与导出）
    │   ├── agent/                   # 决策策略：Policy、RandomPolicy、EpsilonGreedyPolicy
    │   ├── montecarlo/              # 蒙特卡洛评估与控制：MonteCarlo、QAndSBuilder
    │   ├── experiment/              # 策略对比实验：PolicyComparison 入口与 results.json 写盘
    │   └── common/                  # 常量与 JSON 拼接工具（Constants、JsonAppender）
    └── test/java/com/metrocarlo/    # 单元测试（WorldTest、CsvParserTest）
```

## 蒙特卡洛思路

- **一局游戏**（从 (0,0) 出发直至进入终点）视为一次随机样本
- 对同一移动策略重复大量对局，用**平均累计奖励**估计该策略的期望表现，作为评估与改进策略的依据

## 策略对比实验（intention-7）

在 10 张内置地图上比较 **RandomPolicy** 与 **EpsilonGreedyPolicy（ε = 0.1 ~ 0.9）** 的学习效果：每种配置独立重复 50 次「从零训练 1000 个 episode → 用学到的 Q 表按全程贪婪走一遍」，统计贪婪回合总奖励的均值（rri' / rei'-ε）与标准差。

```bash
# 第一步：Java 端跑实验，结果写入 data/compare/run-<时间戳>/results.json（可加 --episodes/--repetitions 缩小规模）
mvn compile exec:java "-Dexec.mainClass=com.metrocarlo.experiment.PolicyComparison"

# 第二步：Python 端出图：compare_per_map.png（每图一子图对比）与 compare_summary.png（全地图 ε 趋势）
python scripts/compare_policies.py
```

实验结论：10 张地图上 **ε=0.1 均取得最优**平均贪婪奖励；ε 越大，Q 表被随机动作污染越重，效果单调下降；RandomPolicy 接近最差。数据格式与常见问题见 `scripts/manual.md` 末尾章节。

## 开发约定

- 中文注释与文档；随机实验支持固定种子，保证结果可复现、可对比
