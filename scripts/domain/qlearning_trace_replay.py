# -*- coding: utf-8 -*-
"""Q-learning trace 回放引擎：读取 data/trace/qlearning/.../trace.json，按 design/14 §3.3 逐步回放，
供 GUI（domain/qlearning_viewer_app.py）消费。

qlearning 行结构与 sarsa 逐字段同形（version=2、algorithm="qlearning"），回放语义
（纯数据写回 + 在线逐行 Q 表覆盖写）与 sarsa 零差异，故本类仅覆盖算法标识 ALGORITHM，
加载校验 / 单步 / 整回合 / 快进 / reset / V̂ 与 α 衰减派生量全部继承 SarsaTraceReplay。

与 sarsa 的唯一语义差异在 td 对象（design/14 §3.3，只影响 GUI 措辞，不影响回放状态）：
- td.nextAction = s′ 的 argmax 动作 a*（严格 > 保留首个，上→右→下→左），非行为动作；
- td.nextQ = max_a′ Q(s′,a′)，终点行恒 0（Q(终点,·) 永不更新）；
- on-policy 链（td.nextAction(k) == action(k+1)）对 qlearning 不成立（design/14 §六.6），
  GUI 展示 a* 时不可与下一步实际执行的动作混为一谈。
本模块只依赖标准库与 domain.trace_replay / domain.sarsa_trace_replay，不含 tkinter 逻辑。
"""

from domain.sarsa_trace_replay import SarsaTraceReplay


class QLearningTraceReplay(SarsaTraceReplay):
    """Q-learning trace 回放状态机：行结构与回放语义与 sarsa 同形，仅算法标识不同。

    全部行为继承 SarsaTraceReplay（纯数据写回 + 在线 Q 更新 + panel_state / vhat_value /
    alpha_decayed 派生量）；trace 加载校验自动改为 meta.algorithm == "qlearning"
    （基类 __init__ 按 self.ALGORITHM 分发，design/14 §一 决策 9/§3.1）。
    """

    # ---- 算法标识：决定 trace 加载校验与错误引导（design/14 §一 决策 9）----
    ALGORITHM: str = "qlearning"
