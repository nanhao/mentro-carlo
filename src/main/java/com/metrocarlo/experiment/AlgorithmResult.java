package com.metrocarlo.experiment;

import java.util.List;

/**
 * 单张地图上一种算法的实验结果（intention-16 的采样集合）：每次重复 =
 * 用 ε=1 的 ε-贪婪策略从零训练 episodes 个 episode 得到 Q 表 → 用学到的 Q 表
 * 全程贪婪走一遍 → 记录该回合总奖励。
 *
 * @param algorithm       算法标识（取值 TraceMeta.ALGORITHM_MONTECARLO / _SARSA / _QLEARNING）
 * @param repRewards      每次重复的贪婪回合总奖励（长度 = 重复次数）
 * @param meanReward      repRewards 的均值，即 intention-16 的平均奖励
 * @param stdReward       repRewards 的总体标准差（重复间波动，可视化作误差棒）
 * @param terminatedCount 贪婪走图抵达终点的次数（低于重复次数说明学到的策略会成环）
 */
public record AlgorithmResult(String algorithm, List<Integer> repRewards,
                              double meanReward, double stdReward, int terminatedCount) {
}
