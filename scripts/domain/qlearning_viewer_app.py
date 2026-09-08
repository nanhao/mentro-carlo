# -*- coding: utf-8 -*-
"""Q-learning trace 可视化主窗口（tkinter GUI）。qlearning 回放语义与 sarsa 完全同形
（design/14 §3.3：行结构逐字段同形、在线 TD 更新、无回合末结算），故主窗口直接继承
sarsa 版（domain/sarsa_viewer_app.py），画布也复用 SarsaMapCanvas（中心 V̂ 派生徽章与
贪婪动作描边只依赖 Q 表的 max/argmax，两种算法下规则一致），仅覆盖 off-policy 展示差异：
- 公式条 / 过程面板说明：① TD 目标 = r + γ·max_a′ Q(s′,a′)（a* = argmax，与行为动作解耦）；
- 过程表自举两列：列头 a′ / Q(s′,a′) → a* / Q(s′,a*)；
- 详情卡自举动作行措辞：a*（argmax），区别于 sarsa 的「下一动作 a′（自举项）」；
- 标题与加载：Q-learning 文案 + algorithm == "qlearning" 校验（_new_replay 挂钩）。

界面其余（五按钮、徽章组、V̂ 图例、α 衰减胶囊、10 列正序过程表结构、状态条）与
sarsa 版一致，零重复实现。
"""

from pathlib import Path

from domain.qlearning_trace_replay import QLearningTraceReplay
from domain.sarsa_viewer_app import SarsaViewerApp
from domain.sarsa_trace_replay import SarsaVisit
from domain.trace_replay import TraceReplay


class QLearningViewerApp(SarsaViewerApp):
    """Q-learning 主窗口：继承 sarsa 骨架，仅覆盖标题/公式/列头/自举措辞与加载引擎。"""

    # ---- off-policy 展示差异（design/14 §一：TD 目标取下一状态 max Q 而非行为动作 Q）----
    ALGO_NAME: str = "Q-learning"
    PROCESS_HINT: str = ("在线更新：每执行一步用 r + γ·max_a′ Q(s′,a′) 更新该 (s,a) 的 Q"
                         "（正序 = 执行顺序）：")
    FORMULA: str = ("① TD 目标 = r + γ·max_a′ Q(s′,a′)     ② δ = TD 目标 − Q(s,a)     "
                    "③ Q(s,a) ← Q(s,a) + α·δ")
    # 过程表自举两列头：a*（argmax 动作）与 Q(s′,a*)（即 max_a′ Q(s′,a′)），其余列沿用 sarsa
    TREE_HEADERS: dict = {**SarsaViewerApp.TREE_HEADERS, "na": "a*", "nq": "Q(s′,a*)"}

    def _new_replay(self, path: Path) -> TraceReplay:
        """构造 qlearning 回放引擎（加载时校验 meta.algorithm == "qlearning"）。"""
        return QLearningTraceReplay(path)

    def _bootstrap_line(self, visit: SarsaVisit) -> str:
        """详情卡「自举动作」行文本：a* = argmax_a′ Q(s′,a′)，终点行显示 —（design/14 §3.3）。"""
        if visit.to_type == QLearningTraceReplay.TYPE_TERMINAL:
            na_text: str = "—（已进入终点，Q(s′,·) 恒为 0）"
        else:
            na_text = f"{self.ACTION_ARROW[visit.td.next_action]}（argmax）"
        return f"自举动作 a*：{na_text} · max Q(s′,·) = {visit.td.next_q:.2f}"
