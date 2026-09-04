# -*- coding: utf-8 -*-
"""地图可视化脚本：把 map/N/map.csv 统一渲染为 map/N/map.png。

用法（在项目根目录执行）：
    python scripts/render_maps.py            # 渲染 map 下全部子目录
    python scripts/render_maps.py 3 7        # 只渲染指定编号

配色与图例全局统一：
    空格子 = 白底  |  墙体 = 深灰  |  炸弹 = 红  |  苹果 = 绿  |  终点 = 金色旗
    起点固定在左下角 (0,0)，用蓝色 "S" 标出

依赖 Pillow（已安装则直接运行；否则 pip install pillow）。
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

PROJECT_ROOT = Path(__file__).resolve().parent.parent
MAP_DIR = PROJECT_ROOT / "map"

# ---- 渲染参数（全局统一） ----
CELL = 64          # 单格边长（像素）
GRID_LINE = (160, 160, 160)   # 网格线颜色
BG = (255, 255, 255)          # 空格子 / 画布底色
MARGIN = 20                   # 画布四周留白
LEGEND_WIDTH = 180            # 右侧图例区宽度

COLORS = {
    1: (70, 70, 70),      # 墙体：深灰
    2: (220, 60, 60),     # 炸弹：红
    3: (60, 170, 80),     # 苹果：绿
    4: (240, 190, 40),    # 终点：金
}
START_COLOR = (50, 110, 220)  # 起点 "S"：蓝


def parse_csv(csv_path):
    """读取 CSV 地图为 int 网格，grid[行][列]，行序与文件一致（首行 = 最上排）。"""
    rows = []
    for line in csv_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line:
            rows.append([int(v.strip()) for v in line.split(",")])
    if not rows:
        raise ValueError(f"CSV 地图没有任何有效数据行: {csv_path}")
    width = len(rows[0])
    if any(len(row) != width for row in rows):
        raise ValueError(f"CSV 各行列数不一致（必须为矩形网格）: {csv_path}")
    return rows


def load_font(size):
    """优先取 Windows 常见字体，失败时退回 Pillow 默认字体。"""
    for name in ("arial.ttf", "segoeui.ttf", "consola.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def draw_cell_icon(draw, x, y, size, type_code):
    """在图例区画一个对应类型的示意小图标。"""
    if type_code in (1, 2, 3, 4):
        draw.rectangle([x, y, x + size, y + size], fill=COLORS[type_code])
        draw.rectangle([x, y, x + size, y + size], outline=GRID_LINE)
        if type_code == 4:  # 终点画旗
            draw.polygon([(x + size * 0.35, y + size * 0.2), (x + size * 0.8, y + size * 0.4),
                          (x + size * 0.35, y + size * 0.6)], fill=BG)
    elif type_code == 0:
        draw.rectangle([x, y, x + size, y + size], fill=BG, outline=GRID_LINE)
    elif type_code == "S":
        text = "S"
        font = load_font(int(size * 0.7))
        box = draw.textbbox((0, 0), text, font=font)
        draw.text((x + (size - box[2]) / 2, y + (size - box[3] - box[1]) / 2),
                  text, fill=START_COLOR, font=font)


def render(csv_path, png_path):
    """把单张 CSV 地图渲染为 PNG：左侧网格 + 右侧统一图例。"""
    grid = parse_csv(csv_path)
    height, width = len(grid), len(grid[0])

    canvas_w = MARGIN * 2 + width * CELL + LEGEND_WIDTH
    canvas_h = MARGIN * 2 + height * CELL
    img = Image.new("RGB", (canvas_w, canvas_h), BG)
    draw = ImageDraw.Draw(img)

    ox, oy = MARGIN, MARGIN  # 网格区左上角

    # 逐格填色：CSV 第 row 行对应地图最上排，直接自上而下画即可
    for row in range(height):
        for col in range(width):
            x0 = ox + col * CELL
            y0 = oy + row * CELL
            t = grid[row][col]
            if t in COLORS:
                draw.rectangle([x0, y0, x0 + CELL, y0 + CELL], fill=COLORS[t])
            draw.rectangle([x0, y0, x0 + CELL, y0 + CELL], outline=GRID_LINE)

    # 起点标记：左下角 (0,0) = 网格左下角格子
    sx = ox
    sy = oy + (height - 1) * CELL
    font = load_font(int(CELL * 0.7))
    box = draw.textbbox((0, 0), "S", font=font)
    draw.text((sx + (CELL - box[2]) / 2, sy + (CELL - box[3] - box[1]) / 2),
              "S", fill=START_COLOR, font=font)

    # 右侧图例
    lx = ox + width * CELL + 24
    ly = oy + 8
    items = [(0, "Empty"), (1, "Wall"), (2, "Bomb"), (3, "Apple"),
             (4, "Terminal"), ("S", "Start")]
    icon = 28
    for t, label in items:
        draw_cell_icon(draw, lx, ly, icon, t)
        draw.text((lx + icon + 8, ly + icon / 2 - 8), label, fill=(40, 40, 40),
                  font=load_font(16))
        ly += icon + 10

    # 标注地图尺寸
    draw.text((lx, ly + 8), f"{width} x {height}", fill=(120, 120, 120),
              font=load_font(16))

    img.save(png_path)
    print(f"已渲染: {png_path}")


def main():
    targets = sys.argv[1:]
    if targets:
        csv_list = [MAP_DIR / t / "map.csv" for t in targets]
    else:
        csv_list = sorted(
            (p / "map.csv" for p in MAP_DIR.iterdir()
             if p.is_dir() and (p / "map.csv").exists()),
            key=lambda p: int(p.parent.name) if p.parent.name.isdigit() else 0,
        )
    if not csv_list:
        print("未找到任何 map/N/map.csv，请先运行 python scripts/generate_maps.py")
        return
    for csv_path in csv_list:
        render(csv_path, csv_path.parent / "map.png")


if __name__ == "__main__":
    main()
