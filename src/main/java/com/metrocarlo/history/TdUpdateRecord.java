package com.metrocarlo.history;

import lombok.Builder;
import lombok.Data;

/**
 * 时序差分算法（sarsa）单步 TD 更新明细（sarsa trace 中 visits 行的嵌套 td 对象），
 * 字段与 design/12/README.md §3.3「td 对象字段字典」一一对应，
 * 且与原型（requirements/10/prototype.html）公式条 ①②③ 一一对应：
 * ① TD 目标 = r + γ·Q(s′,a′)；② δ = TD 目标 − Q(s,a)；③ Q(s,a) ← Q(s,a) + α·δ。
 * <p>
 * 仅 sarsa 行存在（montecarlo 行不设置、JSON 不输出）；a′ 为策略在 s′ 实际选择的
 * 下一动作（on-policy 自举），终点行也如实记录（Q(终点,·) 恒 0，GUI 显示 —）。
 */
@Data
@Builder
public class TdUpdateRecord {

    /** 策略在 s′ 实际选择的下一动作 a′：up | right | down | left（终点行为真实选择，GUI 显示 —） */
    private String nextAction;
    /** Q(s′,a′)，更新前读值；终点行恒 0；撞墙且 a′==a 时读到尚未更新的旧值（== 所在行 qOld） */
    private double nextQ;
    /** ① TD 目标 = reward + γ × nextQ */
    private double tdTarget;
    /** ② δ = tdTarget − 所在行 qOld */
    private double delta;
    /** ③ 本行实际使用的学习率（DECAY_ALPHA 开启时随该 (状态, 动作) 衰减后的值；meta.alpha 仅初始值） */
    private double alpha;
}
