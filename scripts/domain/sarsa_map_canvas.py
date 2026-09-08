# -*- coding: utf-8 -*-
"""Sarsa 网格世界画布：在 MC 画布（domain/map_canvas.py）的渲染规则之上叠加两处
sarsa 专属展示（requirements/10 原型 + design/12 §六.6）：
- 格中心徽章 = V̂(s) = max_a Q(s,a)：GUI 派生量（非算法量），虚线边框 + ≈ 前缀区别于 MC 的 S 徽章；
- 四角 Q 徽章对当前贪婪动作加粗描边（四值并列取其一、全 0 不标注，同原型）。
其余（格底/墙斜纹/agent 橙环/坐标轴/数值配色）全部继承 MapCanvas，零重复。
"""

import tkinter as tk
from typing import Optional

from domain.map_canvas import MapCanvas
from domain.trace_replay import TraceReplay


class SarsaMapCanvas(MapCanvas):
    """Sarsa 地图画布：refresh() 骨架与坐标换算全部继承，仅重写两类数值徽章。"""

    # ---- V̂ 派生徽章外观（原型 .s-badge：1px dashed #78909c，文本 ≈ 前缀）----
    VHAT_PREFIX: str = "≈ "
    VHAT_BORDER: str = "#78909c"
    VHAT_DASH: tuple = (4, 2)
    # ---- 贪婪动作描边（原型 hasBest 分支：2px solid #263238）----
    GREEDY_OUTLINE: str = "#263238"
    GREEDY_WIDTH: int = 2

    def _greedy_action(self, x: int, y: int) -> Optional[str]:
        """格子 (x, y) 当前贪婪动作（Q 最大者；并列取遍历顺序首个；四值并列含全 0 不标注）。"""
        actions: tuple = self._replay.ACTIONS
        values: list = [self._replay.q_value(x, y, action) for action in actions]
        if max(values) == min(values):
            return None
        return actions[values.index(max(values))]

    def _draw_s_badge(self, x: int, y: int, max_abs: float) -> None:
        """格中心 V̂(s) = max_a Q(s,a) 派生徽章：虚线边框 + ≈ 前缀 + 类型图标内嵌左侧。"""
        replay: TraceReplay = self._replay
        kind: int = replay.cell_type(x, y)
        if kind == replay.TYPE_WALL:
            return
        px, py = self._cell_xy(x, y)
        value: float = replay.vhat_value(x, y)
        bg, fg = self.badge_colors(value, max_abs, self.TYPE_STYLE[kind][0])
        text: str = self.VHAT_PREFIX + self.fmt_value(value)
        icon: Optional[str] = self.TYPE_STYLE[kind][2]
        icon_w: float = (self.S_BADGE_H - 6) if icon is not None else 0.0
        total: float = self._s_font.measure(text) + icon_w + 12
        cx: float = px + self.CELL / 2
        cy: float = py + self.CELL / 2
        rect: tuple = (cx - total / 2, cy - self.S_BADGE_H / 2,
                       cx + total / 2, cy + self.S_BADGE_H / 2)
        # 与 MC S 徽章的唯一外观差异：虚线边框（原型 .s-badge border: 1px dashed）
        self.create_rectangle(rect[0], rect[1], rect[2], rect[3], fill=bg,
                              outline=self.VHAT_BORDER, width=1, dash=self.VHAT_DASH)
        if icon is not None:
            self.draw_cell_icon(self, rect[0] + 6 + icon_w / 2, cy,
                                self.S_BADGE_H * 0.30, kind)
        self.create_text(rect[0] + 6 + icon_w, cy, text=text,
                         font=self._s_font, fill=fg, anchor="w")

    def _draw_q_badges(self, x: int, y: int, max_abs: float) -> None:
        """四角 Q 徽章（左上← 右上↑ 右下→ 左下↓，几何与 MC 一致），当前贪婪动作加粗描边。"""
        replay: TraceReplay = self._replay
        kind: int = replay.cell_type(x, y)
        if kind == replay.TYPE_WALL:
            return
        px, py = self._cell_xy(x, y)
        x0: float = px
        y0: float = py
        x1: float = px + self.CELL
        y1: float = py + self.CELL
        greedy: Optional[str] = self._greedy_action(x, y)
        for action, (corner, arrow) in self.Q_CORNERS.items():
            value: float = replay.q_value(x, y, action)
            bg, fg = self.badge_colors(value, max_abs, self.TYPE_STYLE[kind][0])
            text: str = arrow + self.fmt_value(value)
            tw: int = self._q_font.measure(text)
            total: float = tw + 8
            if corner == "tl":
                rect: tuple = (x0 + 2, y0 + 2, x0 + 2 + total, y0 + 2 + self.Q_BADGE_H)
            elif corner == "tr":
                rect = (x1 - 2 - total, y0 + 2, x1 - 2, y0 + 2 + self.Q_BADGE_H)
            elif corner == "br":
                rect = (x1 - 2 - total, y1 - 2 - self.Q_BADGE_H,
                        x1 - 2, y1 - 2)
            else:  # bl
                rect = (x0 + 2, y1 - 2 - self.Q_BADGE_H,
                        x0 + 2 + total, y1 - 2)
            if action == greedy:
                # 贪婪动作：2px 深色描边（原型 hasBest 分支）
                self.create_rectangle(rect[0], rect[1], rect[2], rect[3], fill=bg,
                                      outline=self.GREEDY_OUTLINE, width=self.GREEDY_WIDTH)
            else:
                self._badge_frame(rect, bg)
            self.create_text(rect[0] + 4, (rect[1] + rect[3]) / 2,
                             text=text, font=self._q_font, fill=fg, anchor="w")
