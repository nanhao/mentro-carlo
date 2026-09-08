package com.metrocarlo.experiment;

/**
 * 一次全程贪婪走图（评估回合）的结果（intention-7/16 的单次奖励 rri / rei-ε 采样）。
 *
 * @param totalReward 该回合累计奖励（含每步 -1 惩罚与道具奖励）
 * @param terminated  是否在步数上限内抵达终点（false 说明学到的贪婪策略会成环/原地打转）
 */
public record GreedyOutcome(int totalReward, boolean terminated) {
}
