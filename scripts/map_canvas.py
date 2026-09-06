# -*- coding: utf-8 -*-
"""tkinter 网格世界画布组件：把 TraceReplay 的实时状态渲染为一张可视化地图。

与 requirements/2 README §2.3 展示规则逐条对应：
- 格底色 = 格子类型（墙=深灰斜纹且不显示数值；炸弹/苹果/终点=浅色底 + 类型小图标）；
- 格中心 S 徽章（道具图标内嵌于徽章左侧）；四角 Q 徽章（左上← 右上↑ 右下→ 左下↓，
  箭头方向即动作方向，与 Constants.DIRECTION_* 对应）；
- 数值正蓝 (#1565c0) / 负红 (#c62828)，颜色深度按 |v|/M 线性映射（M 为该表内最大
  绝对值），0 值显示灰色；徽章底色与格底色混色模拟半透明，保证浅色道具底上可读；
- agent 用橙色双层环标出（近似原型的 inset box-shadow）；
- 网格外围 y 轴 / 底部 x 轴坐标（World 坐标系，(0,0) 在左下角）。
"""

import tkinter as tk
from tkinter import font as tkfont
from typing import Optional, Tuple, Union

from trace_replay import TraceReplay


class MapCanvas(tk.Canvas):
    """地图画布：每次 refresh() 全量重绘（格子数少，重建成本可忽略）。"""

    # ---- 布局尺寸（像素）----
    CELL: int = 92            # 单格边长
    GAP: int = 3              # 格间隙（露出画布底色形成网格线）
    AXIS_LEFT: int = 34       # 左侧 y 轴标签区宽度
    PAD_TOP: int = 8          # 网格顶部留白
    AXIS_BOTTOM: int = 22     # 底部 x 轴标签区高度

    # ---- 格子类型 → (底色, 边框色, 图标)（与 requirements/2 §2.3 一致）----
    TYPE_STYLE: dict = {
        TraceReplay.TYPE_EMPTY: ("#ffffff", "#e0e0e0", None),
        TraceReplay.TYPE_WALL: ("#546e7a", "#37474f", None),
        TraceReplay.TYPE_BOMB: ("#ffcdd2", "#e57373", "bomb"),
        TraceReplay.TYPE_APPLE: ("#c8e6c9", "#81c784", "apple"),
        TraceReplay.TYPE_TERMINAL: ("#fff9c4", "#f9a825", "flag"),
    }
    WALL_STRIPE: str = "#455a64"      # 墙体斜纹色
    BADGE_BORDER: str = "#c6ced6"     # 徽章统一细边框
    ZERO_BG: str = "#eceff1"          # 0 值徽章底色
    ZERO_FG: str = "#78909c"
    POS_BASE: Tuple[int, int, int] = (21, 101, 192)    # 正数蓝 #1565c0
    NEG_BASE: Tuple[int, int, int] = (198, 40, 40)     # 负数红 #c62828
    FG_DARK: str = "#1c2833"          # 浅底深字
    FG_LIGHT: str = "#ffffff"         # 深底白字
    AGENT_RING: str = "#ff8f00"       # agent 主环橙
    AGENT_HALO: str = "#ffcfa8"       # agent 外环浅橙（模拟半透明）
    AXIS_FG: str = "#90a4ae"

    # ---- 方向 → (Q 徽章方位, 箭头)（左上← 右上↑ 右下→ 左下↓）----
    Q_CORNERS: dict = {
        TraceReplay.ACTION_LEFT: ("tl", "\u2190"),
        TraceReplay.ACTION_UP: ("tr", "\u2191"),
        TraceReplay.ACTION_RIGHT: ("br", "\u2192"),
        TraceReplay.ACTION_DOWN: ("bl", "\u2193"),
    }
    S_BADGE_H: int = 21       # S 徽章高
    Q_BADGE_H: int = 16       # Q 徽章高

    def __init__(self, master: tk.Widget, replay: TraceReplay) -> None:
        """按 replay 的尺寸创建画布，并缓存用到的字体。"""
        self._replay: TraceReplay = replay
        grid_w: int = replay.width * self.CELL + (replay.width - 1) * self.GAP
        grid_h: int = replay.height * self.CELL + (replay.height - 1) * self.GAP
        width: int = self.AXIS_LEFT + grid_w + 6
        height: int = self.PAD_TOP + grid_h + self.AXIS_BOTTOM
        super().__init__(master, width=width, height=height, bg="#ffffff",
                         highlightthickness=0, bd=0)
        self._s_font: tkfont.Font = tkfont.Font(family="Consolas", size=10)
        self._q_font: tkfont.Font = tkfont.Font(family="Consolas", size=8)
        self._axis_font: tkfont.Font = tkfont.Font(
            family="Microsoft YaHei UI", size=9)
        self.refresh()

    # ================= 坐标换算 =================

    def _grid_left(self) -> int:
        """网格区左边缘（画布坐标）。"""
        return self.AXIS_LEFT

    def _grid_top(self) -> int:
        """网格区上边缘（画布坐标）。"""
        return self.PAD_TOP

    def _cell_xy(self, x: int, y: int) -> Tuple[float, float]:
        """world 坐标 (x, y) → 该格左上角画布坐标（y 自下而上）。"""
        step: float = self.CELL + self.GAP
        px: float = self._grid_left() + x * step
        py: float = self._grid_top() + (self._replay.height - 1 - y) * step
        return px, py

    # ================= 全量重绘 =================

    def refresh(self) -> None:
        """按 replay 当前状态全量重绘：格底 → S/Q 徽章 → agent 环 → 坐标轴。"""
        self.delete("all")
        replay: TraceReplay = self._replay
        max_s: float = replay.max_abs_s()
        max_q: float = replay.max_abs_q()
        for y in range(replay.height - 1, -1, -1):       # 自上而下绘制
            for x in range(replay.width):
                self._draw_cell(x, y)
        for y in range(replay.height - 1, -1, -1):
            for x in range(replay.width):
                self._draw_s_badge(x, y, max_s)
                self._draw_q_badges(x, y, max_q)
        self._draw_agent()
        self._draw_axes()

    def _draw_cell(self, x: int, y: int) -> None:
        """绘制单格格底（含墙体斜纹）。"""
        px, py = self._cell_xy(x, y)
        kind: int = self._replay.cell_type(x, y)
        self.paint_cell_style(self, px, py, self.CELL, kind)

    def _draw_s_badge(self, x: int, y: int, max_abs: float) -> None:
        """绘制格子中心的 S 徽章；道具/终点格把类型小图标内嵌在徽章左侧。"""
        replay: TraceReplay = self._replay
        kind: int = replay.cell_type(x, y)
        if kind == replay.TYPE_WALL:
            return
        px, py = self._cell_xy(x, y)
        value: float = replay.s_value(x, y)
        bg, fg = self.badge_colors(value, max_abs, self.TYPE_STYLE[kind][0])
        text: str = self.fmt_value(value)
        icon: Optional[str] = self.TYPE_STYLE[kind][2]
        cx: float = px + self.CELL / 2
        cy: float = py + self.CELL / 2
        self._draw_badge(cx, cy, text, bg, fg, self._s_font,
                         self.S_BADGE_H, icon, kind)

    def _draw_q_badges(self, x: int, y: int, max_abs: float) -> None:
        """绘制格子四角的 Q 徽章（左上← 右上↑ 右下→ 左下↓）。"""
        replay: TraceReplay = self._replay
        kind: int = replay.cell_type(x, y)
        if kind == replay.TYPE_WALL:
            return
        px, py = self._cell_xy(x, y)
        x0: float = px
        y0: float = py
        x1: float = px + self.CELL
        y1: float = py + self.CELL
        for action, (corner, arrow) in self.Q_CORNERS.items():
            value: float = replay.q_value(x, y, action)
            bg, fg = self.badge_colors(value, max_abs, self.TYPE_STYLE[kind][0])
            text: str = arrow + self.fmt_value(value)
            tw: int = self._q_font.measure(text)
            total: float = tw + 8
            if corner == "tl":
                rect: Tuple[float, float, float, float] = (
                    x0 + 2, y0 + 2, x0 + 2 + total, y0 + 2 + self.Q_BADGE_H)
            elif corner == "tr":
                rect = (x1 - 2 - total, y0 + 2, x1 - 2, y0 + 2 + self.Q_BADGE_H)
            elif corner == "br":
                rect = (x1 - 2 - total, y1 - 2 - self.Q_BADGE_H,
                        x1 - 2, y1 - 2)
            else:  # bl
                rect = (x0 + 2, y1 - 2 - self.Q_BADGE_H,
                        x0 + 2 + total, y1 - 2)
            self._badge_frame(rect, bg)
            self.create_text(rect[0] + 4, (rect[1] + rect[3]) / 2,
                             text=text, font=self._q_font, fill=fg, anchor="w")

    def _draw_badge(self, cx: float, cy: float, text: str, bg: str, fg: str,
                    font: tkfont.Font, height: int,
                    icon: Optional[str], kind: int) -> None:
        """以 (cx, cy) 为中心绘制一个胶囊状数值徽章（图标内嵌文本左侧）。"""
        tw: int = font.measure(text)
        icon_w: float = 0.0
        if icon is not None:
            icon_w = height - 6          # 图标区宽度与徽章高度联动
        total: float = tw + icon_w + 12
        rect: Tuple[float, float, float, float] = (
            cx - total / 2, cy - height / 2, cx + total / 2, cy + height / 2)
        self._badge_frame(rect, bg)
        tx: float = rect[0] + 6 + icon_w
        if icon is not None:
            self.draw_cell_icon(self, rect[0] + 6 + icon_w / 2, cy,
                                height * 0.30, kind)
        self.create_text(tx, cy, text=text, font=font, fill=fg, anchor="w")

    def _badge_frame(self, rect: Tuple[float, float, float, float],
                     bg: str) -> None:
        """绘制徽章底框（细边框 + 填充色）。"""
        self.create_rectangle(rect[0], rect[1], rect[2], rect[3],
                              fill=bg, outline=self.BADGE_BORDER, width=1)

    def _draw_agent(self) -> None:
        """在 agent 当前所在格绘制橙色双层环（环状阴影近似 box-shadow）。"""
        replay: TraceReplay = self._replay
        ax, ay = replay.agent_pos
        if replay.cell_type(ax, ay) == replay.TYPE_WALL:
            return
        px, py = self._cell_xy(ax, ay)
        self.draw_agent_ring(self, px, py, self.CELL)

    def _draw_axes(self) -> None:
        """绘制外围 y 轴（左）与底部 x 轴（下）坐标。"""
        replay: TraceReplay = self._replay
        step: float = self.CELL + self.GAP
        for y in range(replay.height):
            py: float = self._grid_top() + (replay.height - 1 - y) * step + self.CELL / 2
            self.create_text(self._grid_left() - 8, py, text=f"y={y}",
                             font=self._axis_font, fill=self.AXIS_FG, anchor="e")
        bottom: float = self._grid_top() + replay.height * step - self.GAP
        for x in range(replay.width):
            px: float = self._grid_left() + x * step + self.CELL / 2
            self.create_text(px, bottom + 9, text=f"x={x}",
                             font=self._axis_font, fill=self.AXIS_FG, anchor="center")

    # ================= 颜色与数值工具 =================

    @staticmethod
    def fmt_value(value: float) -> str:
        """徽章数值文本：正数带 +（0 与负数不带符号），保留两位小数。"""
        if value > 0:
            return f"+{value:.2f}"
        return f"{value:.2f}"

    @staticmethod
    def hex_to_rgb(hex_color: str) -> Tuple[int, int, int]:
        """#rrggbb → (r, g, b)。"""
        hex_color = hex_color.lstrip("#")
        return int(hex_color[0:2], 16), int(hex_color[2:4], 16), int(hex_color[4:6], 16)

    @staticmethod
    def mix_rgb(front: Tuple[int, int, int], back: Tuple[int, int, int],
                alpha: float) -> str:
        """前景色按 alpha 透明度与背景色混色 → #rrggbb（模拟 rgba 叠加）。"""
        mixed: list = [round(f * alpha + b * (1 - alpha))
                       for f, b in zip(front, back)]
        return "#{:02x}{:02x}{:02x}".format(*mixed)

    @classmethod
    def badge_colors(cls, value: float, max_abs: float,
                     cell_bg: str) -> Tuple[str, str]:
        """数值徽章配色：正蓝/负红按 |v|/M 定深浅，0 值灰色；底色与格底色混色。

        返回 (徽章底色, 文字色)，保证任何格子底色上数字都清晰。
        """
        t: float = min(1.0, abs(value) / max_abs) if max_abs > 0 else 0.0
        if t < 0.02:
            return cls.ZERO_BG, cls.ZERO_FG
        base: Tuple[int, int, int] = cls.POS_BASE if value > 0 else cls.NEG_BASE
        bg: str = cls.mix_rgb(base, cls.hex_to_rgb(cell_bg), 0.35 + 0.65 * t)
        fg: str = cls.FG_LIGHT if t > 0.5 else cls.FG_DARK
        return bg, fg

    # ================= 图例 / 静态绘制工具 =================

    @classmethod
    def paint_cell_style(cls, canvas: tk.Canvas, x0: float, y0: float,
                         size: float, kind: Union[int, str]) -> None:
        """画一个格子底（含墙体斜纹），供地图与图例 chip 复用。

        kind 为格子类型编码（0~4）或 "agent"（图例用：白底 + 橙环示意）。
        """
        x1: float = x0 + size
        y1: float = y0 + size
        if kind == "agent":
            canvas.create_rectangle(x0, y0, x1, y1, fill="#ffffff",
                                    outline="#e0e0e0", width=1)
            cls.draw_agent_ring(canvas, x0, y0, size)
            return
        bg, border, _ = cls.TYPE_STYLE[int(kind)]
        canvas.create_rectangle(x0, y0, x1, y1, fill=bg, outline=border, width=1)
        if int(kind) == TraceReplay.TYPE_WALL:
            cls._paint_wall_hatch(canvas, x0, y0, size)

    @staticmethod
    def _paint_wall_hatch(canvas: tk.Canvas, x0: float, y0: float,
                          size: float) -> None:
        """墙体 45° 斜纹（两色交替，与原型 repeating-linear-gradient 45deg 近似）。

        斜线段两端各内缩 5px，避免粗线溢出到相邻格子。
        """
        step: int = 16
        width: int = 8
        for offset in range(0, int(size * 2), step):
            if offset < size:
                ax, ay = x0 + offset, y0
                run: float = size - offset
            else:
                ax, ay = x0, y0 + offset - size
                run: float = 2 * size - offset
            if run < 12:
                continue
            canvas.create_line(ax + 4, ay + 4, ax + run - 4, ay + run - 4,
                               fill=MapCanvas.WALL_STRIPE, width=width)

    @classmethod
    def draw_cell_icon(cls, canvas: tk.Canvas, cx: float, cy: float,
                       radius: float, kind: int) -> None:
        """按类型画一个小图标（炸弹/苹果/终点旗），用于徽章内嵌与图例。"""
        if kind == TraceReplay.TYPE_BOMB:
            # 黑色球体 + 高光 + 引线（画在徽章高度内）
            canvas.create_oval(cx - radius, cy - radius, cx + radius, cy + radius,
                               fill="#37474f", outline="#263238", width=0)
            canvas.create_oval(cx - radius * 0.5, cy - radius * 0.5,
                               cx - radius * 0.1, cy - radius * 0.1,
                               fill="#eceff1", outline="")
            canvas.create_line(cx, cy - radius, cx + radius * 0.2,
                               cy - radius * 1.15, fill="#5d4037", width=1)
            canvas.create_oval(cx + radius * 0.15, cy - radius * 1.3,
                               cx + radius * 0.45, cy - radius * 1.0,
                               fill="#ff8f00", outline="")
        elif kind == TraceReplay.TYPE_APPLE:
            # 红苹果 + 茎 + 绿叶
            canvas.create_oval(cx - radius, cy - radius * 0.75,
                               cx + radius, cy + radius * 0.95,
                               fill="#e53935", outline="#b71c1c", width=0)
            canvas.create_line(cx, cy - radius * 0.6, cx + radius * 0.15,
                               cy - radius * 1.1, fill="#5d4037", width=1)
            canvas.create_oval(cx + radius * 0.05, cy - radius * 1.25,
                               cx + radius * 0.55, cy - radius * 0.8,
                               fill="#66bb6a", outline="")
        elif kind == TraceReplay.TYPE_TERMINAL:
            # 旗杆 + 金色三角旗
            canvas.create_line(cx, cy - radius * 0.8, cx, cy + radius * 0.8,
                               fill="#78909c", width=1.2)
            canvas.create_polygon(cx, cy - radius * 0.7,
                                  cx + radius * 1.1, cy - radius * 0.2,
                                  cx, cy + radius * 0.3,
                                  fill="#f9a825", outline="")

    @staticmethod
    def draw_agent_ring(canvas: tk.Canvas, x0: float, y0: float,
                        size: float) -> None:
        """agent 橙色双层环：内环实橙 3px，外环 1.5px 浅橙（近似半透明光晕）。

        网格格调用 size≈92；图例小 chip（size≈20）按比例自动降级。
        """
        x1: float = x0 + size
        y1: float = y0 + size
        if size >= 50:
            canvas.create_rectangle(x0 + 1.5, y0 + 1.5, x1 - 1.5, y1 - 1.5,
                                    outline=MapCanvas.AGENT_HALO, width=1.5)
            canvas.create_rectangle(x0 + 3, y0 + 3, x1 - 3, y1 - 3,
                                    outline=MapCanvas.AGENT_RING, width=3)
        else:
            canvas.create_rectangle(x0 + 1, y0 + 1, x1 - 1, y1 - 1,
                                    outline=MapCanvas.AGENT_RING, width=1.5)

    @staticmethod
    def paint_ramp(canvas: tk.Canvas, x0: float, y0: float, w: int, h: int,
                   label_font: tkfont.Font) -> None:
        """画「负·红 → 0 → 正·蓝」的数值色阶示意条，并标注两端与 0。"""
        stops: list = [(0.0, "#ffcdd2"), (0.3, "#ffebee"), (0.5, "#eceff1"),
                       (0.7, "#e3f2fd"), (1.0, "#1565c0")]
        for px in range(w):
            frac: float = px / (w - 1)
            for idx in range(len(stops) - 1):
                pos_a, hex_a = stops[idx]
                pos_b, hex_b = stops[idx + 1]
                if pos_a <= frac <= pos_b:
                    span: float = pos_b - pos_a
                    alpha: float = (frac - pos_a) / span if span > 0 else 1.0
                    color: str = MapCanvas.mix_rgb(
                        MapCanvas.hex_to_rgb(hex_b),
                        MapCanvas.hex_to_rgb(hex_a), alpha)
                    break
            canvas.create_rectangle(x0 + px, y0, x0 + px + 1, y0 + h,
                                    fill=color, outline="")
        canvas.create_rectangle(x0, y0, x0 + w, y0 + h, outline="#b0bec5",
                                width=1)
        canvas.create_text(x0, y0 - 5, text="负·深", font=label_font,
                           fill="#c62828", anchor="w")
        canvas.create_text(x0 + w / 2, y0 - 5, text="0", font=label_font,
                           fill="#90a4ae", anchor="center")
        canvas.create_text(x0 + w, y0 - 5, text="正·深", font=label_font,
                           fill="#1565c0", anchor="e")

    @staticmethod
    def legend_chip_size() -> int:
        """图例小 chip 的边长（像素）。"""
        return 22
