# -*- coding: utf-8 -*-
"""蒙特卡洛 trace 回放引擎：读取 data/trace/.../trace.json，按 design/3 README §四的
「GUI 回放与 S/Q 重建契约」驱动 单步 / 整回合 / 快进 / 重置，供 GUI（viewer_app.py）消费。

关键设计（与契约一一对应）：
- 数据驱动、零领域推演：单步只做纯数据写回——agent 位置 ← nextX/nextY、动态地图
  map[nextY][nextX] ← toType；不复制 World.move 的道具消耗规则（规则只存在于 Java 一处）；
- 回合制结算：S/Q 内存表在回合结算瞬间按文件序（结算循环序，即回放序的反向）逐行
  覆盖写 sNew / qNew，等价于 Java 端回合结束后倒序单循环的逐步更新；
- 本模块只依赖标准库，不含任何 tkinter / 领域外逻辑。

trace 格式版本：meta.version == 1（design/3 §三 字段字典）。
"""

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Optional


@dataclass(frozen=True, slots=True)
class Visit:
    """visits 数组中的一行 = 一次状态访问（一次移动）；字段与 design/3 §3.1 一一对应。

    no 为 episode 内正序步号（1 起），故按 no 升序即可得到回放顺序。
    """

    no: int
    x: int                 # 动作前所在格（World 坐标系）
    y: int
    action: str            # up | right | down | left
    reward: int            # 即时奖励（撞墙/越界 -1、炸弹 -5、苹果 +2、其余 0）
    total_reward: int      # 该步后的累计奖励
    next_x: int            # 到达格（撞墙/越界时为 (x, y) 原地）
    next_y: int
    from_type: int         # 移动前所在格类型（本步开始时的状态）
    to_type: int           # 移动后所在格类型（道具被本步消耗后已为 0；进入终点为 4）
    discounted_next_g: float   # γ × G(后继)，回合最后一步为 0
    g: float               # G = reward + discounted_next_g
    s_old: float           # 该格 stateValue 更新前 / 后
    s_new: float
    q_old: float           # 该 (状态, 动作) 的 qValue 更新前 / 后
    q_new: float

    @staticmethod
    def parse(raw: dict) -> "Visit":
        """按 trace.json 的驼峰字段名映射构造 Visit（整数语义字段保持 int）。"""
        return Visit(
            no=raw["no"],
            x=raw["x"], y=raw["y"],
            action=raw["action"],
            reward=raw["reward"], total_reward=raw["totalReward"],
            next_x=raw["nextX"], next_y=raw["nextY"],
            from_type=raw["fromType"], to_type=raw["toType"],
            discounted_next_g=raw["discountedNextG"], g=raw["G"],
            s_old=raw["sOld"], s_new=raw["sNew"],
            q_old=raw["qOld"], q_new=raw["qNew"],
        )


@dataclass(slots=True)
class Episode:
    """单个 episode：回合元信息 + 按回放序（no 升序）排列的访问行。

    结算与过程面板需要「文件序 = 结算循环序」（回放序的反向），
    直接取 rows 的反向即可，无需另存一份。
    """

    index: int            # episode 序号（1 起，与 UI 展示一致）
    total_reward: int     # 回合累计奖励
    ended_by: str         # terminal（进入终点）| maxSteps（达步数上限）
    rows: list            # 按 no 升序的访问行（回放顺序）


@dataclass(frozen=True, slots=True)
class StepResult:
    """单步应用结果：供 GUI 详情卡与状态条展示（一次 apply_one 的完整语义快照）。"""

    visit: Visit          # 被应用的这一行
    note: str             # 动作文案（由行字段直判）
    ep_no: int            # episode 序号（1 起）
    total_steps: int      # 该 episode 的总步数
    settled: bool         # 本步是否为回合最后一步（触发结算）
    ended_by: str         # 结算时结束原因；未结算为 ""
    finished_eps: int     # 结算后的已完成 episode 数
    total_reward: int     # 该 episode 回合累计奖励（结算后可用）


class TraceReplay:
    """一次蒙特卡洛运行 trace 的回放状态机：加载 + 单步 + 整回合结算 + 重置。

    状态只经 apply_one() / reset() 两个入口变化（start_next_episode 由 apply_one
    在回合间隙自动调用），GUI 每步完成后全量重读只读属性即可单向同步。
    """

    # ---- 格子类型编码（与 Constants.CELL_TYPE_* 一致）----
    TYPE_EMPTY = 0
    TYPE_WALL = 1
    TYPE_BOMB = 2
    TYPE_APPLE = 3
    TYPE_TERMINAL = 4

    # ---- 动作方向（trace 中使用的字符串，与 Constants.DIRECTION_* 0↑ 1→ 2↓ 3← 对应）----
    ACTION_UP = "up"
    ACTION_RIGHT = "right"
    ACTION_DOWN = "down"
    ACTION_LEFT = "left"

    def __init__(self, trace_path: Path) -> None:
        """加载 trace.json：meta 原样归档，initialMap 由 CSV 行序转 world 序（map[y][x]）。"""
        if not trace_path.is_file():
            raise FileNotFoundError(f"trace 文件不存在: {trace_path}")
        raw: dict = json.loads(Path(trace_path).read_text(encoding="utf-8"))
        meta: dict = raw["meta"]
        self.version: int = meta["version"]
        self.map_path: str = meta["mapPath"]
        self.width: int = meta["width"]
        self.height: int = meta["height"]
        self.alpha: float = meta["alpha"]
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
                (Visit.parse(v) for v in ep["visits"]), key=lambda v: v.no)
            self.episodes.append(Episode(
                index=ep["index"], total_reward=ep["totalReward"],
                ended_by=ep["endedBy"], rows=visits))
        if not self.episodes:
            raise ValueError(f"trace 中没有 episode 数据: {trace_path}")
        self.reset()

    # ================= 只读状态快照 =================

    @property
    def total_episodes(self) -> int:
        """文件中的 episode 总数（回放进度上限，以实际文件为准）。"""
        return len(self.episodes)

    @property
    def finished_eps(self) -> int:
        """已结算（回放完成）的 episode 数。"""
        return self._finished

    @property
    def active_index(self) -> Optional[int]:
        """进行中 episode 的下标（0 起）；无进行中回合为 None。"""
        return self._active

    @property
    def shown_episode(self) -> int:
        """UI 展示的 episode 序号：进行中显示当前回合，否则显示已完成数。"""
        return self._finished + (1 if self._active is not None else 0)

    @property
    def total_steps(self) -> int:
        """进行中 episode 的总步数；无进行中回合为 0。"""
        if self._active is None:
            return 0
        return len(self.episodes[self._active].rows)

    @property
    def applied_steps(self) -> int:
        """进行中 episode 已回放的步数；无进行中回合为 0。"""
        return self._applied

    @property
    def agent_pos(self) -> tuple:
        """agent 当前位置（world 坐标系）；从未动作时为起点。"""
        return self._pos

    @property
    def has_more(self) -> bool:
        """是否还有未完成的 episode（进行中回合也算未完成）。"""
        return self._finished < len(self.episodes)

    @property
    def last_result(self) -> Optional[StepResult]:
        """最近一次单步的结果（供详情卡展示）；从未单步为 None。"""
        return self._last_result

    @property
    def last_settled_index(self) -> Optional[int]:
        """最近一次完成结算的 episode 下标（0 起）；尚未结算任何回合为 None。"""
        return self._settled

    @property
    def last_settled_episode(self) -> Optional[Episode]:
        """最近一次完成结算的 episode 对象（过程面板数据源）。"""
        if self._settled is None:
            return None
        return self.episodes[self._settled]

    # ================= 渲染查询（GUI 逐格取值） =================

    def cell_type(self, x: int, y: int) -> int:
        """当前位置 (x, y) 的动态格子类型（道具已被此前步骤消耗后为 0）。"""
        return self._map[y][x]

    def s_value(self, x: int, y: int) -> float:
        """格子 (x, y) 的 stateValue（从未更新为 0.0）。"""
        return self._s.get((x, y), 0.0)

    def q_value(self, x: int, y: int, action: str) -> float:
        """格子 (x, y) 在动作 action 上的 qValue（从未更新为 0.0）。"""
        return self._q.get((x, y, action), 0.0)

    def max_abs_s(self) -> float:
        """非墙格中 |stateValue| 的最大值（颜色映射分母；全 0 时返回 0）。"""
        best: float = 0.0
        for y in range(self.height):
            for x in range(self.width):
                if self.cell_type(x, y) == self.TYPE_WALL:
                    continue
                best = max(best, abs(self.s_value(x, y)))
        return best

    def max_abs_q(self) -> float:
        """非墙格中 |qValue| 的最大值（颜色映射分母；全 0 时返回 0）。"""
        best: float = 0.0
        for y in range(self.height):
            for x in range(self.width):
                if self.cell_type(x, y) == self.TYPE_WALL:
                    continue
                for action in (self.ACTION_UP, self.ACTION_RIGHT,
                               self.ACTION_DOWN, self.ACTION_LEFT):
                    best = max(best, abs(self.q_value(x, y, action)))
        return best

    # ================= 动作 / 结算 / 重置 =================

    @staticmethod
    def note_of(visit: Visit) -> str:
        """由行字段直判动作文案（design/3 §4.4），GUI 不做任何领域推演。"""
        if visit.reward == -1:
            return "撞墙 / 越界，留在原地，奖励 -1"
        if visit.to_type == TraceReplay.TYPE_TERMINAL:
            return "进入终点，本回合结束"
        if visit.reward == 2:
            return "吃到苹果，奖励 +2"
        if visit.reward == -5:
            return "踩中炸弹，奖励 -5"
        return "进入空格子"

    def start_next_episode(self) -> None:
        """开始下一个 episode：还原动态地图（道具复原）、agent 回起点、计数清零。"""
        if self._active is not None or not self.has_more:
            return
        self._active = self._finished
        self._applied = 0
        self._pos = (self.start_x, self.start_y)
        self._map = [list(row) for row in self._initial_map]

    def apply_one(self) -> StepResult:
        """回放当前 episode 的下一步（自动开新回合），并返回该步的展示信息。

        本步若是回合最后一行则立即结算：按文件序反向覆盖写 sNew / qNew。
        """
        if self._active is None:
            self.start_next_episode()
        ep: Episode = self.episodes[self._active]
        visit: Visit = ep.rows[self._applied]
        self._applied += 1
        # ---- 纯数据写回（design/3 §4.1）：不复制任何 World.move 规则 ----
        self._pos = (visit.next_x, visit.next_y)
        self._map[visit.next_y][visit.next_x] = visit.to_type
        settled: bool = self._applied == len(ep.rows)
        if settled:
            # 结算循环顺序 = 文件序 = 回放序反向；逐行覆盖写即重建 S/Q 表
            for v in reversed(ep.rows):
                self._s[(v.x, v.y)] = v.s_new
                self._q[(v.x, v.y, v.action)] = v.q_new
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

    def reset(self) -> None:
        """全部状态清零：S/Q 表归零、agent 回起点、动态地图还原、面板数据清空。"""
        self._finished = 0
        self._active: Optional[int] = None
        self._applied = 0
        self._pos: tuple = (self.start_x, self.start_y)
        self._map: list = [list(row) for row in self._initial_map]
        self._s: dict = {}
        self._q: dict = {}
        self._last_result: Optional[StepResult] = None
        self._settled: Optional[int] = None
