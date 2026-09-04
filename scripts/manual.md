# 地图脚本使用手册

`scripts/` 目录下的两个 Python 脚本负责本项目地图资产的生成与可视化：

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
