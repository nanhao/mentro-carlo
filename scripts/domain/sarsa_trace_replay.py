# -*- coding: utf-8 -*-
"""Sarsa trace 回放引擎：读取 data/trace/sarsa/.../trace.json，按 design/12 README §六的
「GUI 回放与 Q 表重建契约」驱动 单步 / 整回合 / 快进 / 重置，供 GUI（domain/sarsa_viewer_app.py）消费。

关键设计（与契约一一对应；骨架继承自 MC 回放引擎 TraceReplay）：
- 数据驱动、零领域推演：单步只做纯数据写回——agent 位置 ← nextX/nextY、动态地图
  map[nextY][nextX] ← toType；不复制 World.move 的道具消耗规则（规则只存在于 Java 一处）；
- 在线逐行更新（与 MC 的回合末结算本质不同）：文件序 = 执行正序，行应用时即把 Q 表
  覆盖写 qMap[(x,y,action)] = qNew，无回合末结算阶段，行应用完表状态即正确；
- 只有 Q 表：无 stateValue，中心 V̂(s) = max_a Q(s,a) 为 GUI 派生量（vhat_value）；
- 本模块只依赖标准库，不含任何 tkinter / 领域外逻辑。

trace 格式版本：meta.version == 2 且 meta.algorithm == "sarsa"（design/12 §3.1/§3.3）。
"""

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Optional, Tuple

from domain.trace_replay import Episode, StepResult, TraceReplay


@dataclass(frozen=True, slots=True)
class TdDetail:
    """visits 行内嵌的 td 对象 = 本步 TD 更新明细（design/12 §3.3），与原型公式条 ①②③ 对应。"""

    next_action: str       # 策略在 s′ 实际选择的 a′（终点行也如实记录，GUI 按 toType==4 显示 —）
    next_q: float          # Q(s′,a′)，更新前读值（终点行恒 0）
    td_target: float       # ① TD 目标 = reward + γ × nextQ
    delta: float           # ② δ = TD 目标 − qOld
    alpha: float           # ③ 本行实际使用的学习率（DECAY_ALPHA 开启时随该 (s,a) 衰减）

    @staticmethod
    def parse(raw: dict) -> "TdDetail":
        """按 trace.json 的驼峰字段名映射构造 TdDetail。"""
        return TdDetail(
            next_action=raw["nextAction"], next_q=raw["nextQ"],
            td_target=raw["tdTarget"], delta=raw["delta"], alpha=raw["alpha"],
        )


@dataclass(frozen=True, slots=True)
class SarsaVisit:
    """visits 数组中的一行 = 一步在线 TD 更新（design/12 §3.3）。

    no 为 episode 内正序步号（1 起），文件序 = 执行序，GUI 直接顺序播放。
    """

    no: int
    x: int                 # 动作前所在格（World 坐标系）
    y: int
    action: str            # up | right | down | left
    reward: int            # 即时奖励净额（撞墙/越界 -2、炸弹 -51、苹果 +19、空/终点 -1）
    total_reward: int      # 该步后的累计奖励
    next_x: int            # 到达格（撞墙/越界时为 (x, y) 原地）
    next_y: int
    from_type: int         # 移动前所在格类型（本步开始时的状态）
    to_type: int           # 移动后所在格类型（道具被本步消耗后已为 0；进入终点为 4）
    td: TdDetail           # 本步 TD 更新明细（sarsa 行专属嵌套对象，MC 行不含）
    q_old: float           # 该 (状态, 动作) 的 qValue 更新前 / 后
    q_new: float

    @staticmethod
    def parse(raw: dict) -> "SarsaVisit":
        """按 trace.json 的驼峰字段名映射构造 SarsaVisit（整数语义字段保持 int）。"""
        return SarsaVisit(
            no=raw["no"], x=raw["x"], y=raw["y"], action=raw["action"],
            reward=raw["reward"], total_reward=raw["totalReward"],
            next_x=raw["nextX"], next_y=raw["nextY"],
            from_type=raw["fromType"], to_type=raw["toType"],
            td=TdDetail.parse(raw["td"]), q_old=raw["qOld"], q_new=raw["qNew"],
        )


class SarsaTraceReplay(TraceReplay):
    """一次 Sarsa 运行 trace 的回放状态机：继承 MC 骨架（常量/状态属性/回合切换），
    覆盖加载、行应用与动作文案为 sarsa 语义（在线 TD 更新、无回合末结算）。

    继承复用：TYPE_*/ACTION_* 常量、只读状态属性、cell_type/q_value、start_next_episode /
    reset / has_more 等全部与 MC 一致（reset 建立的 _s 表对 sarsa 恒为空，无害）；
    Episode / StepResult 数据语义两种算法下完全一致，直接复用（StepResult.visit 实际为 SarsaVisit）。
    """

    # ---- 动作遍历顺序（与 Constants.DIRECTION_* 0↑ 1→ 2↓ 3← 对应；V̂ 取 max 与顺序无关）----
    ACTIONS: tuple = (TraceReplay.ACTION_UP, TraceReplay.ACTION_RIGHT,
                      TraceReplay.ACTION_DOWN, TraceReplay.ACTION_LEFT)

    def __init__(self, trace_path: Path) -> None:
        """加载 trace.json：meta 原样归档 + 算法校验，initialMap 由 CSV 行序转 world 序（map[y][x]）。"""
        if not trace_path.is_file():
            raise FileNotFoundError(f"trace 文件不存在: {trace_path}")
        raw: dict = json.loads(Path(trace_path).read_text(encoding="utf-8"))
        meta: dict = raw["meta"]
        self.version: int = meta["version"]
        self.algorithm: str = meta.get("algorithm", "montecarlo")
        if self.algorithm != "sarsa":
            # design/12 §六.1 加载分发：非 sarsa trace 互不影响，引导至 MC viewer
            raise ValueError(
                f"该 trace 的算法为 {self.algorithm!r}（非 sarsa），请用 montecarlo_viewer.py 查看: {trace_path}")
        self.map_path: str = meta["mapPath"]
        self.width: int = meta["width"]
        self.height: int = meta["height"]
        self.alpha: float = meta["alpha"]          # 初始 α；每行实际 α 见 td.alpha
        self.gamma: float = meta["gamma"]
        self.epsilon: float = meta["epsilon"]
        self.max_episode: int = meta["maxEpisode"]
        self.max_steps_per_episode: int = meta["maxStepsPerEpisode"]
        self.seed: Optional[int] = meta["seed"]
        self.exported_at: str = meta["exportedAt"]
        self.start_x: int = meta["startX"]
        self.start_y: int = meta["startY"]
        # initialMap 为 CSV 行序（首行 = 最上排 = y = height-1），内部统一为 world 序
        csv_rows: list = meta["initialMap"]
        self._initial_map: list = [list(row) for row in reversed(csv_rows)]
        self.episodes: list = []
        for ep in raw["episodes"]:
            visits: list = sorted(
                (SarsaVisit.parse(v) for v in ep["visits"]), key=lambda v: v.no)
            self.episodes.append(Episode(
                index=ep["index"], total_reward=ep["totalReward"],
                ended_by=ep["endedBy"], rows=visits))
        if not self.episodes:
            raise ValueError(f"trace 中没有 episode 数据: {trace_path}")
        self.reset()

    # ================= 派生量与面板数据源 =================

    def vhat_value(self, x: int, y: int) -> float:
        """格子 (x, y) 的中心 V̂(s) = max_a Q(s,a)（GUI 派生量，不落 trace，design/12 §六.6）。"""
        return max(self.q_value(x, y, action) for action in self.ACTIONS)

    @property
    def panel_state(self) -> Optional[Tuple[Episode, list]]:
        """过程面板数据源：(episode, 已应用行列表)。

        进行中回合返回已应用的前缀行（正序 = 执行序）；否则返回最近一个完成回合的
        全部行（在线更新下行应用完即最终状态）；尚未回放任何回合为 None。
        """
        if self._active is not None:
            ep: Episode = self.episodes[self._active]
            return ep, ep.rows[:self._applied]
        if self._finished > 0:
            ep = self.episodes[self._finished - 1]
            return ep, list(ep.rows)
        return None

    @property
    def alpha_decayed(self) -> bool:
        """α 衰减直判（design/12 §六.7，无硬编码）：面板行内 td.alpha 出现多个不同取值即开启。"""
        state: Optional[Tuple[Episode, list]] = self.panel_state
        if state is None:
            return False
        return len({row.td.alpha for row in state[1]}) > 1

    # ================= 动作文案 / 行应用（sarsa 语义） =================

    @staticmethod
    def note_of(visit: SarsaVisit) -> str:
        """由行字段直判动作文案（design/12 §六.8，净奖励口径），GUI 不做任何领域推演。"""
        if visit.to_type == SarsaTraceReplay.TYPE_TERMINAL:
            return "进入终点，本回合结束"
        if visit.next_x == visit.x and visit.next_y == visit.y:
            return "撞墙 / 越界，留在原地，奖励 -2"
        if visit.reward == 19:
            return "吃到苹果，奖励 +19"
        if visit.reward == -51:
            return "踩中炸弹，奖励 -51"
        return "进入空格子，奖励 -1"

    def apply_one(self) -> StepResult:
        """回放当前 episode 的下一步（自动开新回合）：纯数据写回 + 在线 Q 更新。

        与 MC 的本质差异：每行应用即覆盖写 Q 表（qNew），无回合末结算——行应用完
        Q 表状态即正确；本步为回合最后一行时仅切换回合状态（无任何结算动作）。
        """
        if self._active is None:
            self.start_next_episode()
        ep: Episode = self.episodes[self._active]
        visit: SarsaVisit = ep.rows[self._applied]
        self._applied += 1
        # ---- 纯数据写回（design/12 §六.3）：不复制任何 World.move 规则 ----
        self._pos = (visit.next_x, visit.next_y)
        self._map[visit.next_y][visit.next_x] = visit.to_type
        # ---- 在线 TD 更新：Q 表覆盖写该 (状态, 动作) 的更新后值 ----
        self._q[(visit.x, visit.y, visit.action)] = visit.q_new
        settled: bool = self._applied == len(ep.rows)
        if settled:
            self._settled = self._active
            self._finished += 1
            self._active = None
        result: StepResult = StepResult(
            visit=visit, note=self.note_of(visit),
            ep_no=ep.index, total_steps=len(ep.rows),
            settled=settled,
            ended_by=ep.ended_by if settled else "",
            finished_eps=self._finished, total_reward=ep.total_reward,
        )
        self._last_result = result
        return result
