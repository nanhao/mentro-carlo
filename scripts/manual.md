# 地图脚本使用手册

`scripts/` 目录下的 Python 脚本承担三类工作：**地图资产的生成与渲染**（本手册前半部分）、**trace 回放可视化查看器**（见「montecarlo_viewer.py」「sarsa_viewer.py」与「qlearning_viewer.py」章节）与**策略对比实验可视化**（见文末「compare_policies.py」章节）。

目录约定：`scripts/` 只保留可执行的入口脚本；被入口引用的业务模块位于 `scripts/domain/` 子目录（回放引擎、地图画布、主窗口按算法成组：蒙特卡洛 `trace_replay.py` / `map_canvas.py` / `viewer_app.py`；Sarsa 与 Q-learning 以 `sarsa_*` / `qlearning_*` 前缀继承上一算法组），不直接运行。

```
map/1/map.csv  ~ map/10/map.csv   # 地图源文件（Java World 直接加载）
map/1/map.png  ~ map/10/map.png   # 统一样式的可视化图片
```

两个脚本的职责分工：

| 脚本 | 职责 | 输入 | 输出 |
|---|---|---|---|
| `generate_maps.py` | 把内置的 ASCII 布局转换为 CSV 并校验合法性 | 脚本内 `MAPS` 字典 | `map/N/map.csv` |
| `render_maps.py` | 把 CSV 地图渲染为统一风格的 PNG 图片 | `map/N/map.csv` | `map/N/map.png` |

## 环境要求

- Python 3.10+
- Pillow（渲染脚本依赖；未安装时执行 `pip install pillow`）

在项目根目录 `d:\projects\metro-carlo` 下执行所有命令。

## generate_maps.py · 生成地图

```powershell
python scripts/generate_maps.py
```

每次运行都会把脚本内置的 10 张地图重新写入 `map/1 ~ map/10`（覆盖已有 CSV）。

### 布局图例

地图在脚本中以 ASCII 布局书写，每行对应地图的一排，**第一行是地图最上排**（与 CSV、`World` 坐标系一致：CSV 最后一行是 y=0）：

| 字符 | 含义 | CSV 数值 |
|---|---|---|
| `.` | 空格子 | 0 |
| `#` | 墙体 | 1 |
| `B` | 炸弹 | 2 |
| `A` | 苹果 | 3 |
| `T` | 终点 | 4 |

### 强制约定（写入前自动校验）

1. 全部地图均为 **5×5**，且为矩形网格
2. 每张图**恰好 1 个终点**、**至少 1 个苹果**、**至少 1 个炸弹**
3. 起点固定为左下角 `(0,0)`（布局最后一行第一列），必须是空格子
4. 从起点出发存在**避开墙体**抵达终点的路径（BFS 校验）

任一校验失败将抛出 `ValueError` 并中止生成，不会写出不合法的 CSV。

### 如何修改 / 新增地图

- **修改**：编辑脚本中 `MAPS` 字典对应编号的布局行，重新运行即可；主题说明字符串仅用于控制台输出，可一并更新
- **新增**：在 `MAPS` 字典中添加新编号（如 `11: (...)`），运行后自动创建 `map/11/` 子目录；渲染脚本会自动发现并渲染新目录

## render_maps.py · 渲染 PNG

```powershell
# 渲染 map 下全部子目录
python scripts/render_maps.py

# 只渲染指定编号（可传多个）
python scripts/render_maps.py 3 7
```

渲染规则（全部地图统一）：

- 每格 64px，白色网格底 + 灰色网格线
- 配色：墙体=深灰、炸弹=红、苹果=绿、终点=金（带旗帜缺口）
- 起点 `S` 以蓝字标注在左下角格子（`(0,0)`，即网格最下排最左格）
- 右侧固定图例区：Empty / Wall / Bomb / Apple / Terminal / Start 与地图尺寸标注

## 常见问题

- **控制台中文乱码**：PowerShell 默认代码页导致打印的中文提示显示为乱码，仅影响显示，不影响生成的 CSV/PNG 文件内容（均为 UTF-8）
- **`PermissionError`**：确认当前目录是项目根目录，且 `map/N/map.csv` 没有被其他程序（如图片查看器占用的目录）锁定
- **Java 侧加载失败**：CSV 必须为整数矩形网格；本脚本生成的文件已通过项目 `World`/`CsvParser` 的加载验证，若手工改动 CSV 导致行列不齐或非法整数，`CsvParser` 会抛出异常

---

# montecarlo_viewer.py · trace 回放可视化查看器

`montecarlo_viewer.py`（tkinter 图形界面，仅依赖 Python 标准库）把一次蒙特卡洛运行的 `trace.json` 按 design/3 README §四 契约逐步回放：展示每步动作、动态地图变化、回合结算的 G 计算与 S/Q 更新全过程。trace 由 Java 端采集产出（`data/trace/map-N/run-*/trace.json`，见 requirements/2 T2/T3；该目录已被 .gitignore 排除，需先运行 Java 端生成）。

## 启动

```powershell
# 自动发现 data/trace 下各 map-N 的最新一次运行
python scripts/montecarlo_viewer.py

# 初始加载指定地图的最新运行（同 --map map-1）
python scripts/montecarlo_viewer.py --map 1

# 直接加载任意 trace.json（优先于 --map）
python scripts/montecarlo_viewer.py --trace data/trace/map-2/run-20260901-120000/trace.json
```

## 界面与操作

- **顶部工具栏（两行，对照原型）**：第 1 行 = 标题 + 地图下拉（在已发现运行的 map 分组间切换）+「浏览…」（手动加载任意 trace）+ 五个控制按钮；第 2 行（靠右）徽章组实时显示 episode / action / 位置 / 累计奖励
- **窗口缩放**：窗口支持自由缩放与最大化——右列信息卡与过程表随窗口拉伸，地图卡保持固定尺寸（与原型一致）
- **五个控制按钮**：
  - `执行一次 action`：回放一步；若恰为该回合最后一步则立即结算 S/Q（回合制，与 Java 端一致）
  - `执行完当前 episode`：连续步进到回合结束并结算
  - `执行 100 个 episode` / `执行到所有 episode 结束`：批量快进（不足时到文件末尾；过程面板只保留最近一个回合）
  - `reset`：S/Q 清零、动态地图还原、agent 回起点、面板清空
- **左列地图卡**：格底色表示类型（墙 = 深灰斜纹不显示数值；炸弹 / 苹果 / 终点 = 浅色底 + 自绘小图标）；格中心为 S 徽章，四角为 Q 徽章（左上 ←、右上 ↑、右下 →、左下 ↓，箭头即动作方向）；数值正蓝 #1565c0 / 负红 #c62828，颜色深度按 |值|/M 线性映射，0 值为灰色；agent 用橙色双层环标出；下方附类型图例与数值色阶条，外围带 x/y 坐标轴（(0,0) 在左下角）
- **右列信息卡**：① 运行状态——当前 episode / action / 位置 / 回合累计奖励 + α/γ/ε 参数胶囊（取值来自 trace meta）+ 总进度条；② 最近一个 action 详情——步号、起终点、即时/累计奖励与动作文案（撞墙 / 吃苹果 / 踩炸弹 / 进终点等，由行字段直判，GUI 不做领域推演）；③ 最近一个 episode 的 G 计算与 S/Q 更新过程表——倒序逐行（= Java 端结算循环序），每行展示 处理序 / 状态 / 动作 / r / γ·G(后继) / G / S 更新前→后 / Q 更新前→后（episode 内步号不单列：结算序下 no ≡ N+1−处理序，纯镜像冗余，步号见 action 详情卡）
- **底部状态条**：状态圆点（绿 = 执行中 / 灰 = 空闲）+ 操作日志

## 常见问题

- **窗口空白无地图**：`data/trace` 下尚无任何 trace，先运行 Java 端采集；确认目录结构为 `data/trace/map-N/run-<时间戳>/trace.json`
- **控制台中文乱码**：PowerShell 默认代码页显示问题，仅影响终端打印，不影响 GUI 中文显示
- **启动报错 `No module named 'domain'`**：业务模块位于 `scripts/domain/` 子目录，请始终用 `python scripts/montecarlo_viewer.py` 方式启动（脚本所在 scripts 目录会自动加入模块搜索路径）；不要直接运行 `scripts/domain/` 下的模块
- **加载 sarsa / qlearning 的 trace**：算法 trace 与查看器一一对应——sarsa trace（`data/trace/sarsa/`）请用下节 `sarsa_viewer.py`；qlearning trace（`data/trace/qlearning/`）请用「qlearning_viewer.py」章节的 `qlearning_viewer.py`

---

# sarsa_viewer.py · Sarsa trace 回放可视化查看器

`sarsa_viewer.py`（tkinter 图形界面，仅依赖 Python 标准库）把一次 Sarsa 运行的 `trace.json` 按 design/12 README §六 契约逐步回放：Sarsa 为在线 TD 更新（每走一步立即更新该 (s,a) 的 Q，无回合末结算），因此过程面板展示的是「最近一个 episode 的逐步 TD 更新」正序单表。trace 由 Java 端采集产出（`data/trace/sarsa/map-N/run-*/trace.json`，design/12 §二；已被 .gitignore 排除，需先运行 Java 端 `Main.runSarsaOnOneMap` 生成）。

## 启动

```powershell
# 自动发现 data/trace/sarsa 下各 map-N 的最新一次运行
python scripts/sarsa_viewer.py

# 初始加载指定地图的最新运行（同 --map map-1）
python scripts/sarsa_viewer.py --map 1

# 直接加载任意 trace.json（优先于 --map）
python scripts/sarsa_viewer.py --trace data/trace/sarsa/map-1/run-20260908-153618/trace.json
```

加载时校验 `meta.algorithm == "sarsa"`（design/12 §六.1 加载分发）：误选其它算法的 trace 会弹窗提示换用对应查看器（montecarlo → `montecarlo_viewer.py`、qlearning → `qlearning_viewer.py`）。

## 界面与操作

工具栏五按钮、徽章组、左列地图卡与右列信息卡的布局与 `montecarlo_viewer.py` 一致，sarsa 专属差异：

- **地图卡中心徽章 = V̂(s) = max_a Q(s,a)**：Sarsa 无 stateValue，中心值为 GUI 派生量，以虚线边框 + `≈` 前缀区别于 MC 的 S 徽章；图例多两个 chip（「中心 V̂(s) = max_a Q（派生）」与「贪婪动作」）
- **贪婪动作描边**：格内四角 Q 徽章中值最大者以深色加粗描边（四值并列取其一；全 0 不标注）
- **参数条**：α（Q 学习率）/ γ / ε 取自 trace meta，另含「α 衰减」胶囊——由行内 `td.alpha` 是否随行变化直判（无硬编码）
- **最近一个 action 详情**：在 MC 版基础上增加 TD 明细两行——下一动作 a′（终点行显示 `—`，Q(s′,·) 恒为 0）、Q(s′,a′)、TD 目标、δ 与 Q(s,a) 前 → 后
- **过程面板（10 列正序单表）**：步 / 状态 (x,y) / a / r / 到达 s′ / a′ / Q(s′,a′) / TD 目标 / δ / Q(s,a) 前 → 后，按执行顺序逐行追加（文件序 = 执行正序，design/12 §六.5）；公式条静态展示 ①②③
- **无结算语义**：「执行一次 action」每步即时更新 Q；「执行完当前 episode」连续应用至行取尽即完成（不存在回合末 S/Q 结算步骤）

## 常见问题

- **窗口空白无地图**：`data/trace/sarsa` 下尚无任何 trace，先运行 Java 端 `mvn compile exec:java -Dexec.mainClass=com.metrocarlo.Main`（`runSarsaOnOneMap`）采集；确认目录结构为 `data/trace/sarsa/map-N/run-<时间戳>/trace.json`
- **弹窗「算法为 …（非 sarsa）」**：--trace 或「浏览…」选到了其它算法的 trace（弹窗会引导至对应 viewer：montecarlo → `montecarlo_viewer.py`、qlearning → `qlearning_viewer.py`），两者目录与 viewer 互不影响
- **控制台中文乱码 / `No module named 'domain'`**：同 montecarlo_viewer 章节说明

---

# qlearning_viewer.py · Q-learning trace 回放可视化查看器

`qlearning_viewer.py`（tkinter 图形界面，仅依赖 Python 标准库）把一次 Q-learning 运行的 `trace.json` 按 design/14 README §3.3 逐步回放（GUI 回放契约沿用 design/12 §六）。Q-learning 与 Sarsa 同为在线 TD 更新（走一步更新一次、无回合末结算），且 trace 行结构与 sarsa 逐字段同形，因此引擎与界面几乎全部复用 sarsa 版：`QLearningTraceReplay` 继承 `SarsaTraceReplay`（仅覆盖算法标识）、`QLearningViewerApp` 继承 `SarsaViewerApp`、地图画布直接复用 `SarsaMapCanvas`，仅按 off-policy 语义改展示措辞。trace 由 Java 端采集产出（`data/trace/qlearning/map-N/run-*/trace.json`，design/14 §二；已被 .gitignore 排除，需先运行 Java 端 `Main.runQLearningOnOneMap` 生成）。

## 启动

```powershell
# 自动发现 data/trace/qlearning 下各 map-N 的最新一次运行
python scripts/qlearning_viewer.py

# 初始加载指定地图的最新运行（同 --map map-1）
python scripts/qlearning_viewer.py --map 1

# 直接加载任意 trace.json（优先于 --map）
python scripts/qlearning_viewer.py --trace data/trace/qlearning/map-1/run-20260908-182623/trace.json
```

加载时校验 `meta.algorithm == "qlearning"`（design/12 §六.1 加载分发）：误选其它算法的 trace 会弹窗提示换用对应查看器（montecarlo → `montecarlo_viewer.py`、sarsa → `sarsa_viewer.py`）。

## 界面与操作

工具栏五按钮、徽章组、左列地图卡与右列信息卡的布局与 `sarsa_viewer.py` 一致（其「界面与操作」章节通用），qlearning 专属差异（off-policy 自举，design/14 §一）：

- **公式条与过程面板说明**：① TD 目标 = r + γ·max_a′ Q(s′,a′)——a* = argmax_a′ Q(s′,a′)，与行为动作解耦；过程表第 3 列的 a（行为动作）仅用于移动，不参与本步更新
- **过程表两列头**：自举两列 a′ / Q(s′,a′) 改为 **a\* / Q(s′,a\*)**（即 trace 的 `td.nextAction` / `td.nextQ` 字段，后者 = max_a′ Q(s′,a′)）
- **最近一个 action 详情**：自举动作行措辞为「自举动作 a\*：↑（argmax）· max Q(s′,·) = …」，终点行显示 —（Q(s′,·) 恒为 0）
- **on-policy 链不适用**：a* 是 s′ 上的 argmax 动作，与下一步实际执行的动作无必然关系（design/14 §六.6），本 viewer 不做该关联展示
- 中心 V̂(s) = max_a Q(s,a) 派生徽章（虚线 ≈）、贪婪动作描边、α 衰减胶囊等与 sarsa 版完全一致

## 常见问题

- **窗口空白无地图**：`data/trace/qlearning` 下尚无任何 trace，先运行 Java 端 `mvn compile exec:java`（Main 默认入口即 `runQLearningOnOneMap`）采集；确认目录结构为 `data/trace/qlearning/map-N/run-<时间戳>/trace.json`
- **弹窗「算法为 …（非 qlearning）」**：--trace 或「浏览…」选到了其它算法的 trace，请按弹窗提示换用对应 viewer（sarsa / montecarlo 目录与 viewer 互不影响）
- **控制台中文乱码 / `No module named 'domain'`**：同 montecarlo_viewer 章节说明

---

# compare_policies.py · 策略对比实验可视化

`compare_policies.py`（matplotlib，依赖 `pip install matplotlib`）读取 Java 端 `PolicyComparison` 导出的实验结果 `data/compare/run-<时间戳>/results.json`，对每张地图比较 RandomPolicy 与不同 ε（0.1~0.9）的 EpsilonGreedyPolicy 效果：每种配置重复 50 次「从零训练 1000 episode → 用学到的 Q 表全程贪婪走一遍」，记录贪婪回合总奖励的均值/标准差。

## 第一步：运行 Java 端实验（产出数据）

```powershell
mvn compile exec:java -Dexec.mainClass=com.metrocarlo.experiment.PolicyComparison
```

可选参数 `--episodes N`、`--repetitions N` 可缩小规模冒烟（如 `"--episodes" "50" "--repetitions" "3"`）。默认全量运行（10 图 × 10 配置 × 50 次 × 1000 episode）约需数分钟，期间逐配置打印进度。结果写入新目录 `data/compare/run-<时间戳>/results.json`（运行产物，已被 .gitignore 排除），不覆盖旧运行。

## 第二步：出图

```powershell
python scripts/compare_policies.py                                   # 自动取 data/compare 下最新一次运行
python scripts/compare_policies.py --results data/compare/run-<时间戳>/results.json
```

图表输出到 results.json 所在的 run 目录：

| 文件 | 内容 |
|---|---|
| `compare_per_map.png` | 每张地图一个子图：x 轴为策略配置（Random、ε=0.1~0.9），y 轴为贪婪回合总奖励；蓝线+误差棒 = 50 次重复的均值 ± 标准差，灰点 = 每次重复的原始奖励 |
| `compare_summary.png` | 全部地图的趋势对比：每张地图一条细线，加粗深蓝线为全地图平均 |

控制台同时打印每张地图按平均贪婪奖励排序的最优策略配置（含抵达终点次数，低于重复次数说明学到的贪婪策略会成环）。

## 常见问题

- **未找到 results.json**：先运行第一步的 Java 端实验；确认目录结构为 `data/compare/run-<时间戳>/results.json`
- **图中中文显示为方块**：本机缺少微软雅黑/黑体字体时的显示问题，不影响数据本身
