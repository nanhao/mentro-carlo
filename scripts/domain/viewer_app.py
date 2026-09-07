# -*- coding: utf-8 -*-
"""蒙特卡洛 trace 可视化主窗口（tkinter GUI），按 requirements/2 README §二 布局实现。

布局：顶部工具栏两行（第 1 行 = 标题 + 地图 + 5 控制按钮；第 2 行靠右 = 徽章组，对照原型）；
主体左列地图卡（图例 + 色阶条），
右列 ① 运行状态卡（α/γ/ε 参数 + 进度条）② 最近 action 详情卡 ③ 最近 episode 的 G 计算
与 S/Q 更新过程卡（公式条 + 8 列表）；底部状态条。回放遵循 design/3 §四 契约：数据操作
全部委托 TraceReplay——单步不改 S/Q（回合制），结算后重建 S/Q 表；GUI 零领域推演。
"""

import tkinter as tk
from tkinter import ttk, messagebox, filedialog
from tkinter import font as tkfont
from pathlib import Path
from typing import Dict, Optional

from domain.trace_replay import Episode, StepResult, TraceReplay, Visit
from domain.map_canvas import MapCanvas


class ViewerApp:
    """主窗口：组装控件、绑定按钮事件，并单向读取 TraceReplay 状态做全量同步。"""

    # ---- 窗口外观（与 prototype.html 卡片/配色约定一致）----
    WINDOW_W: int = 1480
    WINDOW_H: int = 900
    MIN_W: int = 1120             # 缩放下限（保证工具栏/卡片不被过度裁剪）
    MIN_H: int = 800
    BG: str = "#e9edf3"            # 窗口底（卡片间空隙色）
    TOOLBAR_BG: str = "#eef1f6"    # 工具栏底色
    CARD_BG: str = "#ffffff"       # 卡片底色
    CARD_BORDER: str = "#dde3ea"   # 卡片边框
    ACCENT: str = "#1e88e5"        # 标题竖条/进度条蓝
    TITLE_FG: str = "#0d47a1"
    TEXT_FG: str = "#263238"
    H3_FG: str = "#455a64"
    KV_KEY_FG: str = "#78909c"
    POS_FG: str = "#e65100"
    REWARD_POS: str = "#1b5e20"
    REWARD_NEG: str = "#b71c1c"
    NOTE_BG: str = "#fff8e1"
    STATUS_BG: str = "#fafbfc"
    SEP_FG: str = "#cfd8dc"
    F_UI: tuple = ("Microsoft YaHei UI", 10)
    F_SMALL: tuple = ("Microsoft YaHei UI", 9)
    F_MONO: tuple = ("Consolas", 10)
    ACTION_ARROW: dict = {"up": "↑", "right": "→",
                          "down": "↓", "left": "←"}    # 动作字符串 → 箭头字符

    def __init__(self, root: tk.Tk, trace_maps: Dict[str, Path],
                 initial_path: Path) -> None:
        """构建窗口骨架并加载首个 trace；trace_maps = {map-id: trace.json}。"""
        self._root: tk.Tk = root
        self._maps: Dict[str, Path] = trace_maps
        self._replay: Optional[TraceReplay] = None
        self._map_id: str = ""
        root.title("蒙特卡洛评估与控制过程可视化")
        root.configure(bg=self.BG)
        # 初始尺寸按屏幕钳制（小屏不溢出）；允许缩放/最大化，右列与过程表随窗口自适应
        win_w: int = min(self.WINDOW_W, root.winfo_screenwidth() - 40)
        win_h: int = min(self.WINDOW_H, root.winfo_screenheight() - 80)
        x: int = max(0, (root.winfo_screenwidth() - win_w) // 2)
        y: int = max(0, (root.winfo_screenheight() - win_h) // 3)
        root.geometry(f"{win_w}x{win_h}+{x}+{y}")
        root.minsize(self.MIN_W, self.MIN_H)
        root.resizable(True, True)
        # ---- 集中定义字体与 ttk 样式 ----
        self._f_title: tuple = ("Microsoft YaHei UI", 13, "bold")
        self._f_big: tuple = ("Microsoft YaHei UI", 14, "bold")
        self._f_kv: tuple = ("Consolas", 11)
        style: ttk.Style = ttk.Style(root)
        style.configure("Viewer.Horizontal.TProgressbar", background=self.ACCENT,
                        troughcolor="#eceff1", borderwidth=0, thickness=7)
        style.configure("Viewer.Treeview", font=("Consolas", 9), rowheight=19,
                        background="#ffffff", fieldbackground="#ffffff")
        style.configure("Viewer.Treeview.Heading", font=("Microsoft YaHei UI", 9, "bold"),
                        background="#eceff1")
        self._build_toolbar()
        self._build_body()
        self._build_statusbar()
        self._load_trace(initial_path)

    # ================= 界面构建 =================

    def _build_toolbar(self) -> None:
        """顶部工具栏（对照原型两行）：第 1 行 = 标题/地图/按钮，第 2 行靠右 = 徽章组。"""
        bar: tk.Frame = tk.Frame(self._root, bg=self.TOOLBAR_BG)
        bar.pack(fill=tk.X)
        tk.Frame(self._root, height=1, bg=self.SEP_FG).pack(fill=tk.X)
        # 第 1 行：标题 + 地图选择 + 五个控制按钮
        row1: tk.Frame = tk.Frame(bar, bg=self.TOOLBAR_BG)
        row1.pack(fill=tk.X, padx=14, pady=(8, 2))
        self._title_lbl: tk.Label = tk.Label(row1, text="蒙特卡洛可视化",
                                             font=self._f_title, bg=self.TOOLBAR_BG, fg=self.TITLE_FG)
        self._title_lbl.pack(side=tk.LEFT)
        tk.Frame(row1, width=1, bg=self.SEP_FG).pack(side=tk.LEFT, padx=10, fill=tk.Y)
        self._map_cb: ttk.Combobox = ttk.Combobox(row1, values=list(self._maps.keys()),
                                                  state="readonly", width=7, font=self.F_SMALL)
        self._map_cb.pack(side=tk.LEFT)
        self._map_cb.bind("<<ComboboxSelected>>", self._on_map_selected)
        tk.Button(row1, text="浏览…", command=self._on_open_file, font=self.F_SMALL,
                  relief=tk.FLAT, bg="#ffffff", activebackground="#e3f2fd", padx=6).pack(side=tk.LEFT, padx=(4, 10))
        self._mk_button(row1, "执行一次 action", self._on_step, primary=True)
        self._mk_button(row1, "执行完当前 episode", self._on_finish_episode)
        self._mk_button(row1, "执行 100 个 episode", self._on_run_100)
        self._mk_button(row1, "执行到所有 episode 结束", self._on_run_all)
        self._mk_button(row1, "reset", self._on_reset, danger=True)
        # 第 2 行（靠右）：episode / action / 位置 / 累计奖励徽章（原型 .badges 行）
        row2: tk.Frame = tk.Frame(bar, bg=self.TOOLBAR_BG)
        row2.pack(fill=tk.X, padx=(0, 12), pady=(2, 8))
        badges: tk.Frame = tk.Frame(row2, bg=self.TOOLBAR_BG)
        badges.pack(side=tk.RIGHT)
        tk.Frame(badges, width=1, bg=self.SEP_FG).pack(side=tk.LEFT, fill=tk.Y, padx=(0, 10))
        self._b_ep = self._mk_badge(badges, "episode", "0")
        self._b_act = self._mk_badge(badges, "action", "— / —")
        self._b_pos = self._mk_badge(badges, "位置", "(0, 0)", value_fg=self.POS_FG)
        self._b_total = self._mk_badge(badges, "累计奖励", "0")

    def _mk_badge(self, parent: tk.Widget, caption: str, value: str,
                  value_fg: str = TITLE_FG) -> tk.Label:
        """胶囊徽章：灰 caption + 彩色等宽值；返回数值 Label 供更新。"""
        box: tk.Frame = tk.Frame(parent, bg=self.CARD_BG, highlightbackground=self.SEP_FG,
                                 highlightthickness=1)
        box.pack(side=tk.LEFT, padx=(0, 8))
        tk.Label(box, text=caption, font=self.F_SMALL, bg=self.CARD_BG,
                 fg=self.KV_KEY_FG).pack(side=tk.LEFT, padx=(8, 4), pady=3)
        val: tk.Label = tk.Label(box, text=value, font=self.F_MONO, bg=self.CARD_BG, fg=value_fg)
        val.pack(side=tk.LEFT, pady=3)
        return val

    def _mk_button(self, parent: tk.Widget, text: str, command,
                   primary: bool = False, danger: bool = False) -> tk.Button:
        """统一风格按钮：primary 蓝底 / danger 红底，其余白底。"""
        bg: str = "#e3f2fd" if primary else ("#fdecea" if danger else "#ffffff")
        fg: str = "#0d47a1" if primary else ("#b71c1c" if danger else "#37474f")
        act: str = "#bbdefb" if primary else ("#ffcdd2" if danger else "#e3f2fd")
        btn: tk.Button = tk.Button(parent, text=text, command=command, font=self.F_UI,
                                   bg=bg, fg=fg, activebackground=act, relief=tk.FLAT,
                                   padx=10, pady=3, highlightbackground="#b0bec5", highlightthickness=1)
        btn.pack(side=tk.LEFT, padx=(0, 6))
        return btn

    def _build_body(self) -> None:
        """主体：左地图卡 + 右信息卡列。"""
        body: tk.Frame = tk.Frame(self._root, bg=self.BG)
        body.pack(fill=tk.BOTH, expand=True, padx=14, pady=12)
        self._build_map_card(body)
        info: tk.Frame = tk.Frame(body, bg=self.BG)
        info.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=(14, 0))
        self._build_status_card(info)
        self._build_action_card(info)
        self._build_process_card(info)

    def _card(self, parent: tk.Widget, title: str,
              right_tag: Optional[str] = None) -> tuple:
        """标准卡片：蓝竖条标题行（可选右侧 tag）+ 内容区；返回 (外层, 内容, tag Label)。"""
        outer: tk.Frame = tk.Frame(parent, bg=self.CARD_BG,
                                   highlightbackground=self.CARD_BORDER, highlightthickness=1)
        head: tk.Frame = tk.Frame(outer, bg=self.CARD_BG)
        head.pack(fill=tk.X, padx=14, pady=(9, 2))
        tag_lbl: Optional[tk.Label] = None
        if right_tag is not None:
            tag_lbl = tk.Label(head, text=right_tag, font=self.F_SMALL, bg=self.CARD_BG, fg="#90a4ae")
            tag_lbl.pack(side=tk.RIGHT)
        bar: tk.Canvas = tk.Canvas(head, width=4, height=15, bg=self.CARD_BG, highlightthickness=0)
        bar.pack(side=tk.LEFT, pady=1)
        bar.create_rectangle(0, 0, 4, 15, fill=self.ACCENT, outline="")
        tk.Label(head, text=title, font=self._f_title, bg=self.CARD_BG,
                 fg=self.H3_FG).pack(side=tk.LEFT, padx=(7, 0))
        box: tk.Frame = tk.Frame(outer, bg=self.CARD_BG)
        box.pack(fill=tk.BOTH, expand=True, padx=14, pady=(2, 12))
        return outer, box, tag_lbl

    def _build_map_card(self, body: tk.Widget) -> None:
        """左列地图卡：网格画布 + 图例（类型 chip + 数值色阶条）。"""
        card, box, _ = self._card(body, "网格世界（坐标与 CSV/World 一致：(0,0) 左下角；墙格不显示数值）")
        card.pack(side=tk.LEFT, fill=tk.Y)
        self._canvas_box: tk.Frame = tk.Frame(box, bg=self.CARD_BG)
        self._canvas_box.pack(fill=tk.X)
        tk.Frame(box, height=1, bg="#dde3ea").pack(fill=tk.X, pady=(4, 8))
        row1: tk.Frame = tk.Frame(box, bg=self.CARD_BG)
        row1.pack(fill=tk.X)
        for kind, name in ((0, "空"), (1, "墙"), (2, "炸弹"), (3, "苹果"), (4, "终点"), ("agent", "agent")):
            self._legend_chip(row1, kind, name)
        row2: tk.Frame = tk.Frame(box, bg=self.CARD_BG)
        row2.pack(fill=tk.X, pady=(6, 0))
        ramp_cv: tk.Canvas = tk.Canvas(row2, width=176, height=38, bg=self.CARD_BG, highlightthickness=0)
        ramp_cv.pack(side=tk.LEFT)
        MapCanvas.paint_ramp(ramp_cv, 8, 20, 140, 13, tkfont.Font(family="Microsoft YaHei UI", size=8))
        tk.Label(row2, text="数值颜色：正蓝 / 负红，越深 |值| 越大", font=self.F_SMALL,
                 bg=self.CARD_BG, fg="#546e7a").pack(side=tk.LEFT, padx=(2, 0))

    def _legend_chip(self, parent: tk.Widget, kind, name: str) -> None:
        """图例单条：小色块 + 名称；kind 为格子类型编码或 "agent"。"""
        chip: tk.Frame = tk.Frame(parent, bg=self.CARD_BG)
        chip.pack(side=tk.LEFT, padx=(0, 12))
        size: int = MapCanvas.legend_chip_size()
        cv: tk.Canvas = tk.Canvas(chip, width=size, height=size, bg=self.CARD_BG, highlightthickness=0)
        cv.pack(side=tk.LEFT, padx=(0, 5))
        MapCanvas.paint_cell_style(cv, 0, 0, size, kind)
        if kind in (TraceReplay.TYPE_BOMB, TraceReplay.TYPE_APPLE, TraceReplay.TYPE_TERMINAL):
            MapCanvas.draw_cell_icon(cv, size / 2, size / 2, size * 0.22, kind)
        tk.Label(chip, text=name, font=self.F_SMALL, bg=self.CARD_BG,
                 fg="#546e7a").pack(side=tk.LEFT)

    def _build_status_card(self, info: tk.Widget) -> None:
        """运行状态卡：4 行 kv + α/γ/ε 参数条 + 总进度条。"""
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
        for sym, label in (("α", "学习率"), ("γ", "折扣因子"), ("ε", "探索率")):
            cap: tk.Frame = tk.Frame(params, bg="#eef2f7", highlightbackground="#d5dde8",
                                     highlightthickness=1)
            cap.pack(side=tk.LEFT, padx=(0, 8))
            tk.Label(cap, text=sym, font=("Consolas", 10, "bold"), bg="#eef2f7",
                     fg=self.TITLE_FG).pack(side=tk.LEFT, padx=(9, 2), pady=2)
            val_lbl: tk.Label = tk.Label(cap, text="", font=self.F_SMALL, bg="#eef2f7", fg="#37474f")
            val_lbl.pack(side=tk.LEFT, padx=(2, 9), pady=2)
            self._param_capsules[sym] = (val_lbl, label)
        self._progress: ttk.Progressbar = ttk.Progressbar(box, style="Viewer.Horizontal.TProgressbar", maximum=1, value=0)
        self._progress.pack(fill=tk.X, pady=(12, 2))

    def _kv_row(self, grid: tk.Widget, row: int, key: str) -> tk.Label:
        """kv 表格行（灰键名 + 等宽值），返回值 Label 供更新。"""
        tk.Label(grid, text=key, font=self.F_SMALL, bg=self.CARD_BG, fg=self.KV_KEY_FG).grid(
            row=row, column=0, sticky="w", padx=(0, 16), pady=2)
        val: tk.Label = tk.Label(grid, text="—", font=self._f_kv, bg=self.CARD_BG, fg=self.TEXT_FG)
        val.grid(row=row, column=1, sticky="w", pady=2)
        return val

    def _build_action_card(self, info: tk.Widget) -> None:
        """最近一个 action 详情卡：无动作时显示提示；内容区整体重建式刷新。"""
        card, box, _ = self._card(info, "最近一个 action 详情")
        card.pack(fill=tk.X, pady=(0, 12))
        self._act_box: tk.Frame = box
        tk.Label(box, text="（尚无动作——点击「执行一次 action」开始回放）",
                 font=self.F_SMALL, bg=self.CARD_BG, fg="#b0bec5").pack(pady=10)

    def _build_process_card(self, info: tk.Widget) -> None:
        """G 计算与 S/Q 更新过程卡：说明 + 公式条 + 8 列合并单表。"""
        card, box, self._proc_tag = self._card(
            info, "最近一个 episode 的 G 计算与 S/Q 更新", right_tag="")
        card.pack(fill=tk.BOTH, expand=True)
        tk.Label(box, text="倒序逐行处理（每行 = 一次状态访问），三步同行完成：",
                 font=self.F_SMALL, bg=self.CARD_BG, fg="#90a4ae").pack(anchor="w")
        tk.Label(box, text=("① G = r + γ·G(后继)     ② S ← S + α·(G − S)     "
                            "③ Q(s,a) ← Q(s,a) + α·(G − Q(s,a))"),
                 font=("Consolas", 10), bg="#e8f0fe", fg=self.TITLE_FG,
                 highlightbackground="#c9dcf7", highlightthickness=1).pack(fill=tk.X, pady=(4, 8))
        table_row: tk.Frame = tk.Frame(box, bg=self.CARD_BG)
        table_row.pack(fill=tk.BOTH, expand=True)
        self._tree: ttk.Treeview = self._build_tree(table_row)

    def _build_tree(self, parent: tk.Widget) -> ttk.Treeview:
        """8 列过程表（处理序/状态/动作/r/γ·G/G/S/Q），右侧带纵向滚动条。

        「episode 内步号」不单列：结算序（文件序）下 no ≡ N+1−处理序（每步恰好一行），
        纯镜像冗余；步号仍见「最近一个 action 详情」卡。
        """
        columns: tuple = ("seq", "state", "act", "r", "disc", "g", "s", "q")
        tree: ttk.Treeview = ttk.Treeview(parent, columns=columns, show="headings", style="Viewer.Treeview")
        headers: dict = {"seq": "处理序", "state": "状态 (x,y)", "act": "动作",
                         "r": "r", "disc": "γ·G(后继)", "g": "G", "s": "S：更新前 → 更新后", "q": "Q：更新前 → 更新后"}
        widths: dict = {"seq": 48, "state": 88, "act": 42, "r": 38, "disc": 82, "g": 68, "s": 156, "q": 156}
        for col in columns:
            tree.heading(col, text=headers[col])
            tree.column(col, width=widths[col], anchor="e", stretch=False)
        ybar: ttk.Scrollbar = ttk.Scrollbar(parent, orient="vertical", command=tree.yview)
        tree.configure(yscrollcommand=ybar.set)
        tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        ybar.pack(side=tk.RIGHT, fill=tk.Y)
        return tree

    def _build_statusbar(self) -> None:
        """底部状态条：状态圆点（绿=执行中/灰=空闲）+ 日志文本。"""
        bar: tk.Frame = tk.Frame(self._root, bg=self.STATUS_BG)
        bar.pack(fill=tk.X, side=tk.BOTTOM)
        tk.Frame(self._root, height=1, bg=self.SEP_FG).pack(fill=tk.X)
        self._dot: tk.Canvas = tk.Canvas(bar, width=10, height=10, bg=self.STATUS_BG, highlightthickness=0)
        self._dot.pack(side=tk.LEFT, padx=(14, 7), pady=7)
        self._dot.create_oval(1, 1, 9, 9, fill="#9e9e9e", outline="", tags=("dot",))
        self._status_text: tk.Label = tk.Label(bar, text="就绪", font=self.F_SMALL,
                                               bg=self.STATUS_BG, fg="#455a64", anchor="w")
        self._status_text.pack(side=tk.LEFT, fill=tk.X, expand=True, pady=6)

    # ================= trace 加载与切换 =================

    def _load_trace(self, path: Path) -> None:
        """加载 trace.json：重建回放/画布并全量同步；失败时保持旧状态。"""
        try:
            replay: TraceReplay = TraceReplay(path)
        except Exception as exc:
            messagebox.showerror("trace 加载失败", f"{path}\n{exc}")
            return
        self._root.config(cursor="watch")
        self._root.update_idletasks()
        self._replay = replay
        for child in self._canvas_box.winfo_children():
            child.destroy()
        self._map_canvas = MapCanvas(self._canvas_box, replay)
        self._map_canvas.pack()
        # 标题/下拉/参数条同步；参数一律取自 trace meta，避免两处维护
        self._map_id = self._find_map_id(path)
        # 工具栏标题保持精简（与原型一致），完整 map/run 信息移到窗口标题栏
        self._title_lbl.config(text=f"蒙特卡洛可视化 · {self._map_id}")
        self._root.title(f"蒙特卡洛评估与控制过程可视化 · {self._map_id} · {path.parent.name}")
        self._map_cb.set(self._map_id if self._map_id in self._maps else "")
        meta: dict = {"α": replay.alpha, "γ": replay.gamma, "ε": replay.epsilon}
        for sym, (val_lbl, label) in self._param_capsules.items():
            val_lbl.config(text=f" = {label} ({meta[sym]:g})")
        self._progress.config(maximum=replay.total_episodes)
        self.sync_all()
        self._set_status(f"就绪：已加载 {path}（共 {replay.total_episodes} 个 episode）", False)
        self._root.config(cursor="")

    def _find_map_id(self, path: Path) -> str:
        """从 trace 路径推断 map-id（data/trace/map-N/run-*/trace.json）。"""
        parent: str = path.parent.parent.name
        return parent if parent.startswith("map-") else "自定义"

    def _on_map_selected(self, _event=None) -> None:
        """地图下拉切换：加载对应分组的最新一次运行 trace。"""
        sel: str = self._map_cb.get()
        if sel in self._maps:
            self._load_trace(self._maps[sel])

    def _on_open_file(self) -> None:
        """浏览文件：加载任意 trace.json（便于对比旧运行 / 自定义目录）。"""
        path: str = filedialog.askopenfilename(title="选择 trace.json", filetypes=[("trace 文件", "*.json")])
        if path:
            self._load_trace(Path(path))

    # ================= 按钮事件 =================

    def _guard_finished(self) -> bool:
        """全部 episode 已回放完时提示并返回 True（调用方应立即 return）。"""
        if not self._require_replay().has_more:
            self._set_status("全部 episode 已回放完成，请先 reset", False)
            return True
        return False

    def _on_step(self) -> None:
        """执行一次 action：无进行中回合则自动开新回合；回合最后一步触发结算。"""
        if self._guard_finished():
            return
        result: StepResult = self._require_replay().apply_one()
        self._sync_after_step(result)
        msg: str = (f"episode {result.ep_no} 结束（共 {result.total_steps} 步）："
                    f"累计奖励 {result.total_reward:+d}，S/Q 更新已完成"
                    if result.settled else
                    f"episode {result.ep_no} 回放中：已完成 "
                    f"{result.visit.no} / {result.total_steps} 个 action")
        self._set_status(msg, True)

    def _on_finish_episode(self) -> None:
        """执行完当前 episode：从当前位置连续步进至回合结束并结算。"""
        if self._guard_finished():
            return
        self._set_status(f"episode {self._require_replay().shown_episode} 快进执行中……", True)
        result: StepResult = self._run_until_settled()
        self._sync_after_step(result)
        self._set_status(f"episode {result.ep_no} 已执行完毕：累计奖励 {result.total_reward:+d}，过程面板已刷新", True)

    def _on_run_100(self) -> None:
        """以当前 episode 为第 1 个，连续执行 100 个（不足则到文件末尾）。"""
        if not self._guard_finished():
            self._run_batch(100)

    def _on_run_all(self) -> None:
        """以当前 episode 为第 1 个，执行到全部 episode 结束。"""
        if not self._guard_finished():
            self._run_batch(self._require_replay().total_episodes)

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
        self._set_status(f"已执行 {count} 个 episode（至第 {replay.finished_eps} 个结束）：过程面板展示最近一个回合的结算{tail}", True)

    def _run_until_settled(self) -> StepResult:
        """连续单步直到当前回合结算；返回该回合最后一步的结果。"""
        while True:
            result: StepResult = self._require_replay().apply_one()
            if result.settled:
                return result

    def _on_reset(self) -> None:
        """reset：S/Q 清零、计数归零、agent 回起点、地图还原、面板清空。"""
        replay: TraceReplay = self._require_replay()
        replay.reset()
        self.sync_all()
        self._set_status(f"已 reset：S/Q 清零、计数归零、agent 回到起点 ({replay.start_x}, {replay.start_y})", False)

    # ================= UI 同步 =================

    def _require_replay(self) -> TraceReplay:
        """取当前 replay（未加载属编程错误，直接抛出）。"""
        if self._replay is None:
            raise RuntimeError("尚未加载任何 trace")
        return self._replay

    def _sync_after_step(self, result: StepResult) -> None:
        """单步/回合结束后的统一同步：画布 + 徽章 + 状态卡 + 详情（结算时 + 过程卡）。"""
        self._map_canvas.refresh()
        self._sync_badges()
        self._sync_status_card()
        self._fill_action_detail(result)
        if result.settled:
            self._fill_process_panel()
        self._progress.config(value=self._require_replay().shown_episode)

    def sync_all(self) -> None:
        """全量刷新（加载 / reset 后调用）。"""
        self._map_canvas.refresh()
        self._sync_badges()
        self._sync_status_card()
        self._fill_action_detail(None)
        self._fill_process_panel()
        self._progress.config(value=self._require_replay().shown_episode)

    def _last_total(self, replay: TraceReplay) -> int:
        """最近一步后的累计奖励（尚无任何动作时为 0）。"""
        last: Optional[StepResult] = replay.last_result
        return last.visit.total_reward if last is not None else 0

    def _sync_badges(self) -> None:
        """顶部徽章：episode / action / 位置 / 累计奖励。"""
        replay: TraceReplay = self._require_replay()
        self._b_ep.config(text=str(replay.shown_episode))
        self._b_act.config(text=(f"{replay.applied_steps} / {replay.total_steps}"
                                 if replay.active_index is not None else "— / —"))
        ax, ay = replay.agent_pos
        self._b_pos.config(text=f"({ax}, {ay})")
        self._b_total.config(text=str(self._last_total(replay)))

    def _sync_status_card(self) -> None:
        """运行状态卡 kv：当前 episode / action / 位置 / 回合累计奖励。"""
        replay: TraceReplay = self._require_replay()
        self._kv_ep.config(text=f"第 {replay.shown_episode} 个（共 {replay.total_episodes}）")
        self._kv_step.config(text=(f"本 episode 第 {replay.applied_steps} / {replay.total_steps} 个 action"
                                   if replay.active_index is not None else "episode 已结束 / 尚未开始"))
        ax, ay = replay.agent_pos
        self._kv_pos.config(text=f"({ax}, {ay})")
        self._kv_total.config(text=str(self._last_total(replay)))

    def _fill_action_detail(self, result: Optional[StepResult]) -> None:
        """重建详情卡：无动作时提示；有则展示步号/起终点/奖励/动作说明。"""
        box: tk.Frame = self._act_box
        for child in box.winfo_children():
            child.destroy()
        if result is None:
            tk.Label(box, text="（尚无动作——点击「执行一次 action」开始回放）", font=self.F_SMALL,
                     bg=self.CARD_BG, fg="#b0bec5").pack(pady=10)
            return
        v: Visit = result.visit
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
        tk.Label(box, text=result.note, font=self.F_SMALL, bg=self.NOTE_BG,
                 fg="#5d4037").pack(anchor="w", fill=tk.X)

    def _fill_process_panel(self) -> None:
        """过程面板：按文件序（结算循环序）填充最近结算回合的全部访问行。"""
        for item in self._tree.get_children():
            self._tree.delete(item)
        ep: Optional[Episode] = self._require_replay().last_settled_episode
        if ep is None:
            self._proc_tag.config(text="")
            return
        sign: str = "+" if ep.total_reward > 0 else ""
        self._proc_tag.config(text=f"episode #{ep.index} · 共 {len(ep.rows)} 次访问 · 累计奖励 {sign}{ep.total_reward}")
        for idx, v in enumerate(reversed(ep.rows), start=1):
            arrow: str = self.ACTION_ARROW[v.action]
            self._tree.insert("", "end", values=(
                idx, f"({v.x}, {v.y})", arrow, v.reward,
                f"{v.discounted_next_g:.2f}", f"{v.g:.2f}",
                f"{v.s_old:.2f} → {v.s_new:.2f}", f"{v.q_old:.2f} → {v.q_new:.2f}"))

    def _set_status(self, text: str, running: bool) -> None:
        """更新底部状态条：文字 + 圆点（绿=执行中/灰=空闲）。"""
        self._status_text.config(text=text)
        self._dot.itemconfig("dot", fill="#43a047" if running else "#9e9e9e")
