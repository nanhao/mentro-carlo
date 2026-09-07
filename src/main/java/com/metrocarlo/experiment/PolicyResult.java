package com.metrocarlo.experiment;

import java.util.List;

/**
 * 单张地图上一种策略配置的实验结果（intention-7 的 rri/rei-ε 采样集合）：
 * 每次重复 = 从零训练 episodes 个 episode 得到 S/Q 值 → 用学到的 Q 表全程贪婪走一遍
 * → 记录该回合总奖励。
 *
 * @param policy          策略标识：{@link #RANDOM} 或 {@link #EPS_GREEDY}
 * @param epsilon         ε-贪婪的探索率；random 策略为 null
 * @param repRewards      每次重复的贪婪回合总奖励（长度 = 重复次数）
 * @param meanReward      repRewards 的均值，即 intention-7 的平均奖励 rri' / rei'-ε
 * @param stdReward       repRewards 的总体标准差（重复间波动，可视化作误差棒）
 * @param terminatedCount 贪婪走图抵达终点的次数（低于重复次数说明学到的策略会成环）
 */
public record PolicyResult(String policy, Double epsilon, List<Integer> repRewards,
                           double meanReward, double stdReward, int terminatedCount) {

    /** random 策略标识（JSON policy 字段取值） */
    public static final String RANDOM = "random";
    /** ε-贪婪策略标识（JSON policy 字段取值） */
    public static final String EPS_GREEDY = "eps-greedy";
}
