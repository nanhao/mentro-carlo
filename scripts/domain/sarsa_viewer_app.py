# -*- coding: utf-8 -*-
"""Sarsa trace 可视化主窗口（tkinter GUI），按 requirements/10 原型 + design/12 §六实现。

骨架继承 MC 主窗口（domain/viewer_app.py：工具栏/徽章/卡片/状态条/批量推进全复用），
覆盖 sarsa 差异：中心 V̂ 派生徽章 + 贪婪动作描边（图例相应 +2 chip）、参数条含
「α 衰减」胶囊（由行内 td.alpha 直判）、action 详情含 TD 明细（a′ 终点行显示 —）、
过程面板为「最近一个 episode 的逐步 TD 更新」10 列正序单表（在线更新每步刷新、无结算语义）。
"""

import tkinter as tk
from tkinter import ttk, messagebox, filedialog
from pathlib import Path
from typing import Optional

from domain.trace_replay import Episode, StepResult, TraceReplay
from domain.viewer_app import ViewerApp
from domain.sarsa_trace_replay import SarsaTraceReplay, SarsaVisit
from domain.sarsa_map_canvas import SarsaMapCanvas


class SarsaViewerApp(ViewerApp):
    """Sarsa 主窗口：继承 MC 骨架，覆盖加载/图例/参数条/详情/过程面板与状态文案。"""

    # ---- 过程面板说明与公式条（design/12 §六.5：公式条静态展示 ①②③，行内 α 见 td.alpha）----
    PROCESS_HINT: str = "在线更新：每执行一步立即更新该 (s,a) 的 Q（正序 = 执行顺序）："
    FORMULA: str = ("① TD 目标 = r + γ·Q(s′,a′)     ② δ = TD 目标 − Q(s,a)     "
                    "③ Q(s,a) ← Q(s,a) + α·δ")

    # ---- 展示名与过程表列头（子类覆盖以适配同形算法：qlearning 改公式与 na/nq 两列措辞）----
    ALGO_NAME: str = "Sarsa"
    TREE_HEADERS: dict = {"no": "步", "state": "状态 (x,y)", "act": "a", "r": "r",
                          "dest": "到达 s′", "na": "a′", "nq": "Q(s′,a′)",
                          "target": "TD 目标", "delta": "δ", "q": "Q(s,a)：前 → 后"}

    # ================= 界面构建（sarsa 差异覆盖） =================

    def _build_map_card(self, body: tk.Widget) -> None:
        """左列地图卡：网格画布 + 图例（类型 chip + V̂ 派生/贪婪动作 chip + 数值色阶条）。"""
        card, box, _ = self._card(body, "网格世界（坐标与 CSV/World 一致：(0,0) 左下角；墙格不显示数值）")
        card.pack(side=tk.LEFT, fill=tk.Y)
        self._canvas_box: tk.Frame = tk.Frame(box, bg=self.CARD_BG)
        self._canvas_box.pack(fill=tk.X)
        tk.Frame(box, height=1, bg="#dde3ea").pack(fill=tk.X, pady=(4, 8))
        row1: tk.Frame = tk.Frame(box, bg=self.CARD_BG)
        row1.pack(fill=tk.X)
        for kind, name in ((0, "空"), (1, "墙"), (2, "炸弹"), (3, "苹果"), (4, "终点"), ("agent", "agent")):
            self._legend_chip(row1, kind, name)
        self._sarsa_legend_chips(row1)
        row2: tk.Frame = tk.Frame(box, bg=self.CARD_BG)
        row2.pack(fill=tk.X, pady=(6, 0))
        self._build_value_ramp(row2)

    def _sarsa_legend_chips(self, parent: tk.Widget) -> None:
        """sarsa 专属图例：中心 V̂ 派生徽章（虚线框）与贪婪动作描边示意。"""
        size: int = SarsaMapCanvas.legend_chip_size()
        vhat_chip: tk.Frame = tk.Frame(parent, bg=self.CARD_BG)
        vhat_chip.pack(side=tk.LEFT, padx=(0, 12))
        cv: tk.Canvas = tk.Canvas(vhat_chip, width=size, height=size, bg=self.CARD_BG, highlightthickness=0)
        cv.pack(side=tk.LEFT, padx=(0, 5))
        cv.create_rectangle(1, 1, size - 1, size - 1, fill="#ffffff",
                            outline=SarsaMapCanvas.VHAT_BORDER, dash=SarsaMapCanvas.VHAT_DASH)
        cv.create_text(size / 2, size / 2, text="≈", font=self.F_SMALL, fill="#546e7a")
        tk.Label(vhat_chip, text="中心 V̂(s) = max_a Q（派生）", font=self.F_SMALL,
                 bg=self.CARD_BG, fg="#546e7a").pack(side=tk.LEFT)
        greedy_chip: tk.Frame = tk.Frame(parent, bg=self.CARD_BG)
        greedy_chip.pack(side=tk.LEFT, padx=(0, 12))
        cv2: tk.Canvas = tk.Canvas(greedy_chip, width=size, height=size, bg=self.CARD_BG, highlightthickness=0)
        cv2.pack(side=tk.LEFT, padx=(0, 5))
        cv2.create_rectangle(2, 2, size - 2, size - 2, fill="#ffffff",
                             outline=SarsaMapCanvas.GREEDY_OUTLINE, width=SarsaMapCanvas.GREEDY_WIDTH)
        tk.Label(greedy_chip, text="贪婪动作", font=self.F_SMALL,
                 bg=self.CARD_BG, fg="#546e7a").pack(side=tk.LEFT)

    def _build_value_ramp(self, row: tk.Widget) -> None:
        """数值色阶条示意（与 MC 图例一致：正蓝 / 负红，越深 |值| 越大）。"""
        from tkinter import font as tkfont
        ramp_cv: tk.Canvas = tk.Canvas(row, width=176, height=38, bg=self.CARD_BG, highlightthickness=0)
        ramp_cv.pack(side=tk.LEFT)
        SarsaMapCanvas.paint_ramp(ramp_cv, 8, 20, 140, 13,
                                  tkfont.Font(family="Microsoft YaHei UI", size=8))
        tk.Label(row, text="数值颜色：正蓝 / 负红，越深 |值| 越大", font=self.F_SMALL,
                 bg=self.CARD_BG, fg="#546e7a").pack(side=tk.LEFT, padx=(2, 0))

    def _build_status_card(self, info: tk.Widget) -> None:
        """运行状态卡：4 行 kv + α/γ/ε/α 衰减参数条 + 总进度条。"""
        card, box, _ = self._card(info, "运行状态")
        card.pack(fill=tk.X, pady=(0, 12))
        grid: tk.Frame = tk.Frame(box, bg=self.CARD_BG)
        grid.pack(fill=tk.X)
        self._kv_ep = self._kv_row(grid, 0, "当前 episode")
        self._kv_step = self._kv_row(grid, 1, "当前 action")
        self._kv_pos = self._kv_row(grid, 2, "agent 位置")
        self._kv_total = self._kv_row(grid, 3, "回合累计奖励")
        params: tk.Frame = tk.Frame(box, bg=self.CARD_BG)
        params.pack(fill=tk.X, pady=(10, 0))
        self._param_capsules: dict = {}
        for sym, label in (("α", "Q 学习率"), ("γ", "折扣因子"), ("ε", "探索率")):
            cap: tk.Frame = tk.Frame(params, bg="#eef2f7", highlightbackground="#d5dde8",
                                     highlightthickness=1)
            cap.pack(side=tk.LEFT, padx=(0, 8))
            tk.Label(cap, text=sym, font=("Consolas", 10, "bold"), bg="#eef2f7",
                     fg=self.TITLE_FG).pack(side=tk.LEFT, padx=(9, 2), pady=2)
            val_lbl: tk.Label = tk.Label(cap, text="", font=self.F_SMALL, bg="#eef2f7", fg="#37474f")
            val_lbl.pack(side=tk.LEFT, padx=(2, 9), pady=2)
            self._param_capsules[sym] = (val_lbl, label)
        # α 衰减胶囊：状态由行内 td.alpha 直判（design/12 §六.7，无硬编码）
        decay_cap: tk.Frame = tk.Frame(params, bg="#eef2f7", highlightbackground="#d5dde8",
                                       highlightthickness=1)
        decay_cap.pack(side=tk.LEFT, padx=(0, 8))
        tk.Label(decay_cap, text="α 衰减", font=self.F_SMALL, bg="#eef2f7",
                 fg=self.TITLE_FG).pack(side=tk.LEFT, padx=(9, 2), pady=2)
        self._alpha_decay_lbl: tk.Label = tk.Label(decay_cap, text="", font=self.F_SMALL,
                                                   bg="#eef2f7", fg="#90a4ae")
        self._alpha_decay_lbl.pack(side=tk.LEFT, padx=(2, 9), pady=2)
        self._progress: ttk.Progressbar = ttk.Progressbar(box, style="Viewer.Horizontal.TProgressbar",
                                                          maximum=1, value=0)
        self._progress.pack(fill=tk.X, pady=(12, 2))

    def _build_process_card(self, info: tk.Widget) -> None:
        """逐步 TD 更新过程卡：说明 + 公式条 + 10 列正序单表。"""
        card, box, self._proc_tag = self._card(info, "最近一个 episode 的 Q 更新过程（逐步 TD）",
                                               right_tag="")
        card.pack(fill=tk.BOTH, expand=True)
        tk.Label(box, text=self.PROCESS_HINT, font=self.F_SMALL,
                 bg=self.CARD_BG, fg="#90a4ae").pack(anchor="w")
        tk.Label(box, text=self.FORMULA, font=("Consolas", 10), bg="#e8f0fe", fg=self.TITLE_FG,
                 highlightbackground="#c9dcf7", highlightthickness=1).pack(fill=tk.X, pady=(4, 8))
        table_row: tk.Frame = tk.Frame(box, bg=self.CARD_BG)
        table_row.pack(fill=tk.BOTH, expand=True)
        self._tree: ttk.Treeview = self._build_tree(table_row)

    def _build_tree(self, parent: tk.Widget) -> ttk.Treeview:
        """10 列过程表，列 ↔ 字段映射见 design/12 §六.5（正序 = 执行顺序）。"""
        columns: tuple = ("no", "state", "act", "r", "dest", "na", "nq", "target", "delta", "q")
        tree: ttk.Treeview = ttk.Treeview(parent, columns=columns, show="headings", style="Viewer.Treeview")
        widths: dict = {"no": 40, "state": 86, "act": 36, "r": 40, "dest": 86, "na": 36,
                        "nq": 68, "target": 68, "delta": 62, "q": 150}
        for col in columns:
            tree.heading(col, text=self.TREE_HEADERS[col])
            tree.column(col, width=widths[col], anchor="e", stretch=False)
        ybar: ttk.Scrollbar = ttk.Scrollbar(parent, orient="vertical", command=tree.yview)
        tree.configure(yscrollcommand=ybar.set)
        tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        ybar.pack(side=tk.RIGHT, fill=tk.Y)
        return tree

    # ================= trace 加载与切换 =================

    def _new_replay(self, path: Path) -> TraceReplay:
        """构造本算法 trace 的回放引擎（加载校验 algorithm == ALGORITHM；子类覆盖以扩展）。"""
        return SarsaTraceReplay(path)

    def _load_trace(self, path: Path) -> None:
        """加载 trace.json（校验 algorithm == sarsa）：重建回放/画布并全量同步；失败时保持旧状态。"""
        try:
            replay: TraceReplay = self._new_replay(path)
        except Exception as exc:
            messagebox.showerror("trace 加载失败", f"{path}\n{exc}")
            return
        self._root.config(cursor="watch")
        self._root.update_idletasks()
        self._replay: TraceReplay = replay
        for child in self._canvas_box.winfo_children():
            child.destroy()
        self._map_canvas = SarsaMapCanvas(self._canvas_box, replay)
        self._map_canvas.pack()
        self._map_id: str = self._find_map_id(path)
        self._title_lbl.config(text=f"{self.ALGO_NAME} 回放可视化 · {self._map_id}")
        self._root.title(f"{self.ALGO_NAME} TD 控制过程可视化 · {self._map_id} · {path.parent.name}")
        self._map_cb.set(self._map_id if self._map_id in self._maps else "")
        # 参数胶囊取自 trace meta（α 为初始学习率；行内实际 α 见过程面板与详情卡）
        meta: dict = {"α": replay.alpha, "γ": replay.gamma, "ε": replay.epsilon}
        for sym, (val_lbl, label) in self._param_capsules.items():
            val_lbl.config(text=f" = {label} ({meta[sym]:g})")
        self._progress.config(maximum=replay.total_episodes)
        self.sync_all()
        self._set_status(f"就绪：已加载 {path}（共 {replay.total_episodes} 个 episode）", False)
        self._root.config(cursor="")

    # ================= UI 同步（sarsa 差异覆盖） =================

    def _sync_status_card(self) -> None:
        """运行状态卡 kv + α 衰减胶囊（由最近面板行内 td.alpha 直判）。"""
        replay: TraceReplay = self._require_replay()
        self._kv_ep.config(text=f"第 {replay.shown_episode} 个（共 {replay.total_episodes}）")
        self._kv_step.config(text=(f"本 episode 第 {replay.applied_steps} / {replay.total_steps} 个 action"
                                   if replay.active_index is not None else "episode 已结束 / 尚未开始"))
        ax, ay = replay.agent_pos
        self._kv_pos.config(text=f"({ax}, {ay})")
        self._kv_total.config(text=str(self._last_total(replay)))
        decayed: bool = replay.alpha_decayed
        self._alpha_decay_lbl.config(text="（开）" if decayed else "（关）",
                                     fg=self.TITLE_FG if decayed else "#90a4ae")

    def _sync_after_step(self, result: StepResult) -> None:
        """单步/回合结束后的统一同步：在线更新无结算阶段，过程面板每步都刷新。"""
        self._map_canvas.refresh()
        self._sync_badges()
        self._sync_status_card()
        self._fill_action_detail(result)
        self._fill_process_panel()
        self._progress.config(value=self._require_replay().shown_episode)

    def _fill_process_panel(self) -> None:
        """过程面板：按执行正序填充面板数据源行（进行中 = 已应用前缀；完成后 = 全部行）。"""
        for item in self._tree.get_children():
            self._tree.delete(item)
        state: Optional[tuple] = self._require_replay().panel_state
        if state is None:
            self._proc_tag.config(text="")
            return
        ep: Episode
        rows: list
        ep, rows = state
        replay: TraceReplay = self._require_replay()
        if replay.active_index is not None:      # 进行中：累计奖励取最近一步后的值
            reward: int = rows[-1].total_reward if rows else 0
            self._proc_tag.config(
                text=f"episode #{ep.index}（进行中）· 已更新 {len(rows)} 步 · 累计奖励 {reward:+d}")
        else:
            self._proc_tag.config(
                text=f"episode #{ep.index} · 共 {len(ep.rows)} 步 · 累计奖励 {ep.total_reward:+d}")
        for v in rows:                           # 文件序 = 执行正序（design/12 §六.4/§六.5）
            arrow: str = self.ACTION_ARROW[v.action]
            na: str = ("—" if v.to_type == SarsaTraceReplay.TYPE_TERMINAL
                       else self.ACTION_ARROW[v.td.next_action])
            self._tree.insert("", "end", values=(
                v.no, f"({v.x}, {v.y})", arrow, v.reward,
                f"({v.next_x}, {v.next_y})", na,
                f"{v.td.next_q:.2f}", f"{v.td.td_target:.2f}", f"{v.td.delta:.2f}",
                f"{v.q_old:.2f} → {v.q_new:.2f}"))

    def _bootstrap_line(self, visit: SarsaVisit) -> str:
        """详情卡「自举动作」行文本（design/12 §六.5；终点行如实记录但显示 —；
        子类覆盖为 off-policy 措辞，见 qlearning_viewer_app）。"""
        if visit.to_type == SarsaTraceReplay.TYPE_TERMINAL:
            na_text: str = "—（已进入终点，Q(s′,·) 恒为 0）"
        else:
            na_text = f"{self.ACTION_ARROW[visit.td.next_action]}（自举项）"
        return f"下一动作 a′ {na_text} · Q(s′,a′) = {visit.td.next_q:.2f}"

    def _fill_action_detail(self, result: Optional[StepResult]) -> None:
        """重建详情卡：无动作时提示；有则展示步号/起终点/奖励/TD 明细/动作说明。"""
        box: tk.Frame = self._act_box
        for child in box.winfo_children():
            child.destroy()
        if result is None:
            tk.Label(box, text="（尚无动作——点击「执行一次 action」开始回放）", font=self.F_SMALL,
                     bg=self.CARD_BG, fg="#b0bec5").pack(pady=10)
            return
        v = result.visit
        sign_r: str = "+" if v.reward > 0 else ""
        sign_t: str = "+" if v.total_reward > 0 else ""
        tk.Label(box, text=f"第 {v.no} / {result.total_steps} 步 · episode {result.ep_no}",
                 font=self._f_big, bg=self.CARD_BG, fg=self.TITLE_FG).pack(anchor="w")
        arrow: str = self.ACTION_ARROW[v.action]
        tk.Label(box, text=f"从 ({v.x}, {v.y}) 向 {arrow} 到达 ({v.next_x}, {v.next_y})",
                 font=self.F_UI, bg=self.CARD_BG, fg=self.TEXT_FG).pack(anchor="w", pady=2)
        color: str = self.REWARD_POS if v.reward > 0 else (self.REWARD_NEG if v.reward < 0 else self.TEXT_FG)
        tk.Label(box, text=f"即时奖励 {sign_r}{v.reward} · 累计奖励 {sign_t}{v.total_reward}",
                 font=self.F_UI, bg=self.CARD_BG, fg=color).pack(anchor="w", pady=(0, 4))
        # ---- TD 明细两行（design/12 §六.5；自举动作行措辞见 _bootstrap_line，子类按算法覆盖）----
        tk.Label(box, text=self._bootstrap_line(v),
                 font=self.F_MONO, bg=self.CARD_BG, fg=self.TEXT_FG).pack(anchor="w")
        tk.Label(box, text=(f"TD 目标 {v.td.td_target:.2f} · δ = {SarsaMapCanvas.fmt_value(v.td.delta)}"
                            f" · Q(s,a)： {v.q_old:.2f} → {v.q_new:.2f}"),
                 font=self.F_MONO, bg=self.CARD_BG, fg=self.TEXT_FG).pack(anchor="w")
        tk.Label(box, text=result.note, font=self.F_SMALL, bg=self.NOTE_BG,
                 fg="#5d4037").pack(anchor="w", fill=tk.X)

    # ================= 按钮事件（无结算语义的文案覆盖） =================

    def _on_step(self) -> None:
        """执行一次 action：应用下一行并立即展示其在线 Q 更新。"""
        if self._guard_finished():
            return
        result: StepResult = self._require_replay().apply_one()
        self._sync_after_step(result)
        msg: str = (f"episode {result.ep_no} 结束（共 {result.total_steps} 步）："
                    f"累计奖励 {result.total_reward:+d}，本回合 Q 已全部更新"
                    if result.settled else
                    f"episode {result.ep_no} 执行中：已完成 "
                    f"{result.visit.no} / {result.total_steps} 个 action（Q 已逐步更新）")
        self._set_status(msg, True)

    def _on_finish_episode(self) -> None:
        """执行完当前 episode：连续应用至回合行取尽（无结算步骤，更新已逐步完成）。"""
        if self._guard_finished():
            return
        self._set_status(f"episode {self._require_replay().shown_episode} 快进执行中……", True)
        result: StepResult = self._run_until_settled()
        self._sync_after_step(result)
        self._set_status(f"episode {result.ep_no} 已执行完毕：累计奖励 {result.total_reward:+d}"
                         f"（每步 TD 更新已完成），过程面板已刷新", True)

    def _run_batch(self, n: int) -> None:
        """同步快进 n 个回合（批量中不逐帧刷新，结束后一次性同步；过程面板只留最近一个）。"""
        replay: TraceReplay = self._require_replay()
        count: int = min(n, replay.total_episodes - replay.finished_eps)
        self._root.config(cursor="watch")
        self._set_status(f"正在快进 {count} 个 episode（自第 {replay.shown_episode} 个起）…", True)
        result: Optional[StepResult] = None
        for _ in range(count):
            result = self._run_until_settled()
        self._root.config(cursor="")
        if result is None:      # 理论上不会发生（_guard_finished 已保证 count >= 1）
            return
        self._sync_after_step(result)
        tail: str = "（已到文件末尾）" if count < n else ""
        self._set_status(f"已执行 {count} 个 episode（至第 {replay.finished_eps} 个结束）："
                         f"过程面板展示最近一个 episode 的逐步更新{tail}", True)

    def _on_reset(self) -> None:
        """reset：Q 表清零、计数归零、agent 回起点、地图还原、面板清空。"""
        replay: TraceReplay = self._require_replay()
        replay.reset()
        self.sync_all()
        self._set_status(f"已 reset：Q 清零、计数归零、agent 回到起点 ({replay.start_x}, {replay.start_y})", False)
