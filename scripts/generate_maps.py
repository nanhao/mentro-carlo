# -*- coding: utf-8 -*-
"""地图生成脚本：把内置的 10 张 ASCII 地图布局转换为 CSV，并做合法性校验。

用法（在项目根目录执行）：
    python scripts/generate_maps.py

输出：map/1/map.csv ~ map/10/map.csv

布局图例（与 CSV 数值的对应关系见 Constants）：
    .  空格子   -> 0
    #  墙体     -> 1
    B  炸弹     -> 2
    A  苹果     -> 3
    T  终点     -> 4

约定：
- 全部地图均为 5×5，且至少包含 1 个苹果与 1 个炸弹（校验强制）
- 每张布局按"第一行 = 地图最上排"书写，与 CSV/坐标系映射一致
- 起点固定为左下角 (0,0)，即布局最后一行第一列，必须是空格子
- 校验规则：矩形网格、恰好一个终点、至少一苹果一炸弹、起点为空、从起点出发存在避开墙体的可达路径
"""

from collections import deque
from pathlib import Path

# 图例字符 -> 格子类型编码
LEGEND = {".": 0, "#": 1, "B": 2, "A": 3, "T": 4}

# 10 张内置地图：编号 -> (主题说明, 布局行列表)，均为 5×5，每张至少一苹果(A)一炸弹(B)
MAPS = {
    1: ("入门：少量墙体，先熟悉上下左右", [
        ". . . . .",
        ". # . # .",
        ". B . # .",
        ". . . . A",
        ". # . . T",
    ]),
    2: ("绕行：一堵横墙，学会绕路", [
        ". . A . T",
        ". # # # .",
        ". . . # .",
        "# # . # B",
        ". . . . A",
    ]),
    3: ("苹果之路：顺路收集苹果，当心炸弹", [
        ". . . A T",
        ". # # . .",
        ". B . . .",
        "# . # A .",
        ". A . # .",
    ]),
    4: ("十字迷宫：炸弹卡在缺口前", [
        ". A # A T",
        ". . # . .",
        ". B . . .",
        ". . # . .",
        ". . # . .",
    ]),
    5: ("棋盘：墙格交错，找空隙", [
        ". . # . T",
        ". . # . #",
        ". . . . .",
        "# . # . #",
        ". A # B .",
    ]),
    6: ("房间与门：穿门而过，死角有诱饵炸弹", [
        "# # . . T",
        "# . # . #",
        "# . . . #",
        "# . # . #",
        ". A # . B",
    ]),
    7: ("炸弹警戒：顶路踩雷，右路安全", [
        ". B . B T",
        ". . . . .",
        ". # . # .",
        "A . . . A",
        ". . # . .",
    ]),
    8: ("回形走廊：外圈绕行或进内圈拾苹果", [
        "T . . . .",
        ". # . # .",
        ". # A # .",
        ". # # # B",
        ". . . . .",
    ]),
    9: ("蛇形窄道：横向夹层，炸弹挡中段", [
        ". . . . T",
        ". # # # .",
        ". A . B .",
        ". # # # .",
        ". . . . .",
    ]),
    10: ("综合大关：墙体、炸弹与苹果混合", [
        ". . # . T",
        ". # . # .",
        ". B . . A",
        ". # # # .",
        ". A . . .",
    ]),
}

PROJECT_ROOT = Path(__file__).resolve().parent.parent
MAP_DIR = PROJECT_ROOT / "map"


def parse_layout(layout):
    """把 ASCII 布局解析为 int 网格，grid[行][列]，行序与布局一致。"""
    rows = []
    for line_no, line in enumerate(layout, start=1):
        cells = line.split()
        if not cells:
            raise ValueError(f"布局第 {line_no} 行为空")
        unknown = set(cells) - set(LEGEND)
        if unknown:
            raise ValueError(f"布局第 {line_no} 行存在非法图例字符: {unknown}")
        rows.append([LEGEND[c] for c in cells])
    return rows


def validate(grid, index):
    """校验地图合法性：矩形、唯一终点、起点为空、终点从起点可达。"""
    height = len(grid)
    width = len(grid[0])
    if any(len(row) != width for row in grid):
        raise ValueError(f"地图 {index} 不是矩形网格")

    flat = [v for row in grid for v in row]
    if flat.count(4) != 1:
        raise ValueError(f"地图 {index} 必须恰好有 1 个终点(4)，实际 {flat.count(4)} 个")
    if flat.count(3) < 1 or flat.count(2) < 1:
        raise ValueError(f"地图 {index} 至少要有 1 个苹果(3)和 1 个炸弹(2)，"
                         f"实际苹果 {flat.count(3)} 个、炸弹 {flat.count(2)} 个")
    if grid[height - 1][0] != 0:
        raise ValueError(f"地图 {index} 起点 (0,0) 必须是空格子")

    # BFS：从起点出发，墙体不可通行，其余格子可通行。
    # 布局行序 = CSV 行序（首行 = 最上排），起点左下角 (0,0) 对应布局第 height-1 行第 0 列；
    # 终点位置直接在布局网格上扫描。
    start = (0, height - 1)  # (x, y)：布局坐标下的起点
    target = None
    for y in range(height):
        for x in range(width):
            if grid[y][x] == 4:
                target = (x, y)
    # 连通性在上下翻转下保持不变，直接在布局网格上搜索即可，坐标含义不影响结论
    visited = {start}
    queue = deque([start])
    while queue:
        x, y = queue.popleft()
        if (x, y) == target:
            return
        for dx, dy in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < width and 0 <= ny < height and (nx, ny) not in visited \
                    and grid[ny][nx] != 1:
                visited.add((nx, ny))
                queue.append((nx, ny))
    raise ValueError(f"地图 {index} 的终点从起点不可达")


def main():
    MAP_DIR.mkdir(exist_ok=True)
    for index, (theme, layout) in sorted(MAPS.items()):
        grid = parse_layout(layout)
        validate(grid, index)

        target = MAP_DIR / str(index)
        target.mkdir(exist_ok=True)
        csv_path = target / "map.csv"
        csv_path.write_text(
            "\n".join(",".join(str(v) for v in row) for row in grid) + "\n",
            encoding="utf-8",
        )
        size = f"{len(grid[0])}x{len(grid)}"
        print(f"地图 {index:>2} [{size}] 已生成: {csv_path}  ({theme})")
    print(f"共生成 {len(MAPS)} 张地图，均通过合法性校验")


if __name__ == "__main__":
    main()
