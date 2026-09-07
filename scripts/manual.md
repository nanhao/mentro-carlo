# 地图脚本使用手册

`scripts/` 目录下的 Python 脚本承担两类工作：**地图资产的生成与渲染**（本手册前半部分）与 **trace 回放可视化查看器**（见文末「montecarlo_viewer.py」章节）。

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
- **启动报错 `No module named 'viewer_app'`**：必须以仓库根目录为工作目录执行 `python scripts/montecarlo_viewer.py`（脚本间相对导入依赖 scripts 目录在搜索路径中）
