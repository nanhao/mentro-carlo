package com.metrocarlo.history;

import lombok.Builder;
import lombok.Data;

/**
 * 时序差分算法（sarsa / qlearning）单步 TD 更新明细（TD 算法 trace 中 visits 行的嵌套 td 对象），
 * 字段与 design/12/README.md §3.3「td 对象字段字典」及 design/14/README.md §3.3（qlearning 语义）一一对应，
 * 且与原型（requirements/10/prototype.html）公式条 ①②③ 一一对应：
 * ① TD 目标 = r + γ·Q(s′,a′)；② δ = TD 目标 − Q(s,a)；③ Q(s,a) ← Q(s,a) + α·δ。
 * <p>
 * 仅 TD 算法行存在（montecarlo 行不设置、JSON 不输出）；nextAction/nextQ 语义按 algorithm 区分：
 * sarsa = 策略在 s′ 实际选择的 a′ 及 Q(s′,a′)（on-policy 自举）；
 * qlearning = argmax 动作 a* 及 max_a′ Q(s′,a′)（off-policy 自举，平局按上→右→下→左取首个）。
 * 终点行 Q(终点,·) 恒 0（GUI 显示 —）。
 */
@Data
@Builder
public class TdUpdateRecord {

    /** 下一动作：up | right | down | left；语义按 algorithm——sarsa=策略在 s′ 实际选择的 a′，qlearning=argmax 动作 a*（终点行也如实记录，GUI 显示 —） */
    private String nextAction;
    /** 自举 Q 值，更新前读值；语义按 algorithm——sarsa=Q(s′,a′)，qlearning=max_a′ Q(s′,a′)；终点行恒 0；撞墙 s′==s 时读到尚未更新的旧值（qlearning 下 max 含 Q(s,a) 旧值，见 design/14 §3.5） */
    private double nextQ;
    /** ① TD 目标 = reward + γ × nextQ */
    private double tdTarget;
    /** ② δ = tdTarget − 所在行 qOld */
    private double delta;
    /** ③ 本行实际使用的学习率（DECAY_ALPHA 开启时随该 (状态, 动作) 衰减后的值；meta.alpha 仅初始值） */
    private double alpha;
}
